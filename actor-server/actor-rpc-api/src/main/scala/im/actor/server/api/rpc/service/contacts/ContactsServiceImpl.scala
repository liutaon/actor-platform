package im.actor.server.api.rpc.service.contacts

import java.security.MessageDigest

import scala.collection.immutable
import scala.concurrent._
import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout
import scodec.bits.BitVector
import slick.dbio
import slick.dbio.DBIO
import slick.dbio.Effect.{ Read, Write }
import slick.driver.PostgresDriver.api._

import im.actor.api.rpc.DBIOResult._
import im.actor.api.rpc._
import im.actor.api.rpc.contacts._
import im.actor.api.rpc.misc._
import im.actor.api.rpc.users.{ UpdateUserLocalNameChanged, User }
import im.actor.server.push.{ SeqUpdatesManager, SeqUpdatesManagerRegion }
import im.actor.server.sequence.SeqState
import im.actor.server.social.{ SocialManager, SocialManagerRegion }
import im.actor.server.user.{ UserOffice, UserViewRegion }
import im.actor.server.util.{ ACLUtils, ContactsUtils, PhoneNumberUtils, UserUtils }
import im.actor.server.{ models, persist }

class ContactsServiceImpl(
  implicit
  userViewRegion:      UserViewRegion,
  seqUpdManagerRegion: SeqUpdatesManagerRegion,
  socialManagerRegion: SocialManagerRegion,
  db:                  Database,
  actorSystem:         ActorSystem
)
  extends ContactsService {

  import ContactsUtils._
  import SeqUpdatesManager._
  import SocialManager._
  import UserUtils._

  override implicit val ec: ExecutionContext = actorSystem.dispatcher
  implicit val timeout = Timeout(5.seconds)

  object Errors {
    val CantAddSelf = RpcError(401, "OWN_USER_ID", "User id cannot be equal to self.", false, None)
    val ContactAlreadyExists = RpcError(400, "CONTACT_ALREADY_EXISTS", "Contact already exists.", false, None)
    val ContactNotFound = RpcError(404, "CONTACT_NOT_FOUND", "Contact not found.", false, None)
  }

  case class EmailNameUser(email: String, name: Option[String], userId: Int)

  private[service] def hashIds(ids: Seq[Int]): String = {
    val md = MessageDigest.getInstance("SHA-256")
    val uids = ids.to[immutable.SortedSet].mkString(",")
    BitVector(md.digest(uids.getBytes)).toHex
  }

  override def jhandleImportContacts(phones: Vector[PhoneToImport], emails: Vector[EmailToImport], clientData: ClientData): Future[HandlerResult[ResponseImportContacts]] = {
    val action =
      for {
        client ← authorizedClient(clientData)
        user ← fromDBIOOption(CommonErrors.UserNotFound)(persist.User.find(client.userId).headOption)
        optPhone ← fromDBIO(persist.UserPhone.findByUserId(client.userId).headOption)
        optEmail ← fromDBIO(persist.UserEmail.findByUserId(client.userId).headOption)

        phoneUsers ← fromDBIO(importPhones(user, optPhone, phones)(client))
        (pUsers, pUserIds) = phoneUsers

        emailUsersAndIds ← fromDBIO(importEmails(user, optEmail, emails)(client))
        (eUsers, eUserIds) = emailUsersAndIds

        seqstate ← fromFuture({
          implicit val c = client
          UserOffice.broadcastClientUpdate(UpdateContactsAdded((pUserIds ++ eUserIds).toVector), None, isFat = true)
        })
      } yield ResponseImportContacts((pUsers ++ eUsers).toVector, seqstate.seq, seqstate.state.toByteArray)

    db.run(action.run)
  }

  override def jhandleGetContacts(contactsHash: String, clientData: ClientData): Future[HandlerResult[ResponseGetContacts]] = {
    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      val action = persist.contact.UserContact.findContactIdsActive(client.userId).map(hashIds).flatMap { hash ⇒
        if (contactsHash == hash) {
          DBIO.successful(Ok(ResponseGetContacts(Vector.empty[users.User], isNotChanged = true)))
        } else {
          for {
            userIdsNames ← persist.contact.UserContact.findContactIdsWithLocalNames(client.userId)
            userIds = userIdsNames.map(_._1).toSet
            users ← persist.User.findByIds(userIds)
            namesMap = immutable.Map(userIdsNames: _*)
            // TODO: #perf optimize (so much requests!)
            userStructs ← DBIO.sequence(users.map(user ⇒
              userStruct(user, namesMap.get(user.id).getOrElse(None), clientData.authId)))
          } yield {
            Ok(ResponseGetContacts(
              users = userStructs.toVector,
              isNotChanged = false
            ))
          }
        }
      }

      action.transactionally
    }

    db.run(toDBIOAction(authorizedAction))
  }

  override def jhandleRemoveContact(userId: Int, accessHash: Long, clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      persist.contact.UserContact.find(ownerUserId = client.userId, contactUserId = userId).flatMap {
        case Some(contact) ⇒
          if (accessHash == ACLUtils.userAccessHash(clientData.authId, userId, contact.accessSalt)) {
            for {
              _ ← persist.contact.UserContact.delete(client.userId, userId)
              _ ← DBIO.from(UserOffice.broadcastClientUpdate(UpdateUserLocalNameChanged(userId, None), None, isFat = false))
              seqstate ← DBIO.from(UserOffice.broadcastClientUpdate(UpdateContactsRemoved(Vector(userId)), None, isFat = false))
            } yield {
              Ok(ResponseSeq(seqstate.seq, seqstate.state.toByteArray))
            }
          } else {
            DBIO.successful(Error(CommonErrors.InvalidAccessHash))
          }
        case None ⇒ DBIO.successful(Error(Errors.ContactNotFound))
      }
    }

    db.run(toDBIOAction(authorizedAction))
  }

  override def jhandleAddContact(userId: Int, accessHash: Long, clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      val action = (for {
        optUser ← persist.User.find(userId).headOption
        optNumber ← optUser.map(user ⇒ persist.UserPhone.findByUserId(user.id).headOption).getOrElse(DBIO.successful(None))
      } yield {
        (optUser, optNumber map (_.number))
      }).flatMap {
        case (Some(user), Some(userPhoneNumber)) ⇒
          if (accessHash == ACLUtils.userAccessHash(clientData.authId, user.id, user.accessSalt)) {
            persist.contact.UserContact.find(ownerUserId = client.userId, contactUserId = userId).flatMap {
              case None ⇒
                for {
                  _ ← addContact(user.id, userPhoneNumber, None, user.accessSalt)
                  seqstate ← DBIO.from(UserOffice.broadcastClientUpdate(UpdateContactsAdded(Vector(user.id)), None, isFat = true))
                } yield Ok(ResponseSeq(seqstate.seq, seqstate.state.toByteArray))
              case Some(contact) ⇒
                DBIO.successful(Error(Errors.ContactAlreadyExists))
            }
          } else DBIO.successful(Error(CommonErrors.InvalidAccessHash))
        case (None, _) ⇒ DBIO.successful(Error(CommonErrors.UserNotFound))
        case (_, None) ⇒ DBIO.successful(Error(CommonErrors.UserPhoneNotFound))
      }

      action.transactionally
    }

    db.run(toDBIOAction(authorizedAction))
  }

  override def jhandleSearchContacts(rawNumber: String, clientData: ClientData): Future[HandlerResult[ResponseSearchContacts]] = {
    val action =
      for {
        client ← authorizedClient(clientData)
        clientUser ← fromDBIOOption(CommonErrors.UserNotFound)(persist.User.find(client.userId).headOption)
        optPhone ← fromDBIO(persist.UserPhone.findByUserId(client.userId).headOption)
        normalizedPhone ← point(PhoneNumberUtils.normalizeStr(rawNumber, clientUser.countryCode))

        contactUsers ← if (optPhone.map(_.number) == normalizedPhone) point(Vector.empty[User])
        else fromDBIO(DBIO.sequence(normalizedPhone.toVector.map { phone ⇒
          implicit val c = client
          for {
            userPhones ← persist.UserPhone.findByPhoneNumber(phone)
            users ← getUserStructs(userPhones.map(_.userId).toSet)
          } yield {
            userPhones foreach (p ⇒ recordRelation(p.userId, client.userId))
            users.toVector
          }
        }) map (_.flatten))
      } yield ResponseSearchContacts(contactUsers)
    db.run(action.run)
  }

  private def importEmails(user: models.User, optOwnEmail: Option[models.UserEmail], emails: Vector[EmailToImport])(implicit client: AuthorizedClientData): DBIO[(Seq[User], Seq[Sequence])] = {
    //filtering out user's own email and making `Map` from emails to optional name
    val filtered: Map[String, Option[String]] = optOwnEmail
      .map(e ⇒ emails.filterNot(_.email == e.email)).getOrElse(emails)
      .map(e ⇒ e.email → e.name).toMap
    val filteredEmails = filtered.keySet

    for {
      //finding emails of users that are registered
      // but don't contain in user's contact list
      emailModels ← persist.UserEmail.findByEmails(filteredEmails)
      userContacts ← persist.contact.UserContact.findContactIdsAll(user.id)
      newEmailContacts = emailModels.filter(e ⇒ !userContacts.contains(e.userId))

      //registering UserEmailContacts
      newEmailContactsM = newEmailContacts.map(e ⇒ e.email → e.userId).toMap
      emailsNamesUsers = newEmailContactsM.keySet.map(k ⇒ EmailNameUser(k, filtered(k), newEmailContactsM(k)))
      usersAndIds ← createEmailContacts(user.id, emailsNamesUsers)

      //creating unregistered contacts
      unregisteredEmails = filteredEmails -- emailModels.map(_.email)
      unregisteredEmailActions = unregisteredEmails.map { email ⇒
        persist.contact.UnregisteredEmailContact.createIfNotExists(email, user.id, filtered(email))
      }
      _ ← DBIO.sequence(unregisteredEmailActions.toSeq)
    } yield usersAndIds.flatten.unzip
  }

  private def importPhones(user: models.User, optPhone: Option[models.UserPhone], phones: Vector[PhoneToImport])(client: AuthorizedClientData): DBIO[(Seq[User], Set[Int])] = {
    val filteredPhones = optPhone.map(p ⇒ phones.filterNot(_.phoneNumber == p.number)).getOrElse(phones)

    val (phoneNumbers, phonesMap) = filteredPhones.foldLeft((Set.empty[Long], Map.empty[Long, Option[String]])) {
      case ((phonesAcc, mapAcc), PhoneToImport(phone, nameOpt)) ⇒
        PhoneNumberUtils.normalizeLong(phone, user.countryCode) match {
          case Nil        ⇒ (phonesAcc, mapAcc + ((phone, nameOpt)))
          case normPhones ⇒ ((phonesAcc ++ normPhones), mapAcc ++ ((phone, nameOpt) +: normPhones.map(_ → nameOpt)))
        }
    }

    val f = for {
      userPhones ← persist.UserPhone.findByNumbers(phoneNumbers)
      ignoredContactsIds ← persist.contact.UserContact.findContactIdsAll(user.id)
      uniquePhones = userPhones.filter(p ⇒ !ignoredContactsIds.contains(p.userId))
      usersPhones ← DBIO.sequence(uniquePhones map (p ⇒ persist.User.find(p.userId).headOption map (_.map((_, p.number))))) map (_.flatten) // TODO: #perf lots of sql queries
    } yield {
      usersPhones.foldLeft((immutable.Seq.empty[(models.User, Long, Option[String])], immutable.Set.empty[Int], immutable.Set.empty[Long])) {
        case ((usersPhonesNames, newContactIds, registeredPhones), (user, phone)) ⇒
          (usersPhonesNames :+ Tuple3(user, phone, phonesMap(phone)),
            newContactIds + user.id,
            registeredPhones + phone)
      }
    }

    f flatMap {
      case (usersPhonesNames, newContactIds, registeredPhoneNumbers) ⇒
        actorSystem.log.debug("Phone numbers: {}, registered: {}", phoneNumbers, registeredPhoneNumbers)

        // TODO: #perf do less queries
        val unregInsertActions = (phoneNumbers &~ registeredPhoneNumbers).toSeq map { phoneNumber ⇒
          persist.contact.UnregisteredPhoneContact.createIfNotExists(phoneNumber, user.id, phonesMap.get(phoneNumber).getOrElse(None))
        }

        for {
          _ ← DBIO.sequence(unregInsertActions)
          _ ← DBIO.successful(newContactIds.toSeq foreach (id ⇒ recordRelation(id, user.id)))
          userStructs ← if (usersPhonesNames.nonEmpty)
            createPhoneContacts(user.id, usersPhonesNames)(client) else DBIO.successful(Seq.empty[User])
        } yield (userStructs, newContactIds)
    }
  }

  private def createPhoneContacts(ownerUserId: Int, usersPhonesNames: immutable.Seq[(models.User, Long, Option[String])])(implicit client: AuthorizedClientData): dbio.DBIOAction[immutable.Seq[User], NoStream, Read with Write with Read with Read with Read with Read] = {
    persist.contact.UserContact.findIds(ownerUserId, usersPhonesNames.map(_._1.id).toSet).flatMap { existingContactUserIds ⇒
      val actions = usersPhonesNames map {
        case (user, phone, localName) ⇒
          val userContact = models.contact.UserPhoneContact(
            ownerUserId = ownerUserId,
            contactUserId = user.id,
            phoneNumber = phone,
            name = localName,
            accessSalt = user.accessSalt,
            isDeleted = false
          )

          for {
            _ ← persist.contact.UserPhoneContact.insertOrUpdate(userContact)
            userStruct ← userStruct(user, localName, client.authId)
          } yield {
            userStruct
          }
      }

      DBIO.sequence(actions)
    }
  }

  private def createEmailContacts(ownerUserId: Int, contacts: Set[EmailNameUser])(implicit client: AuthorizedClientData) = {
    val actions = contacts.map { contact ⇒
      val userContact = models.contact.UserEmailContact(
        ownerUserId = ownerUserId,
        contactUserId = contact.userId,
        email = contact.email,
        name = contact.name,
        accessSalt = "",
        isDeleted = false
      )
      for {
        _ ← persist.contact.UserEmailContact.insertOrUpdate(userContact)
        optUser ← persist.User.find(contact.userId).headOption
        userStruct ← optUser.map { user ⇒
          userStruct(user, contact.name, client.authId).map(Some(_))
        }.getOrElse(DBIO.successful(None))
      } yield userStruct.map(_ → contact.userId)
    }
    DBIO.sequence(actions.toSeq)
  }

}
