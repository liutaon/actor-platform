package im.actor.server.api.rpc.service.profile

import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.util.Timeout
import slick.driver.PostgresDriver.api._

import im.actor.api.rpc.DBIOResult._
import im.actor.api.rpc._
import im.actor.api.rpc.files.FileLocation
import im.actor.api.rpc.misc.{ ResponseBool, ResponseSeq }
import im.actor.api.rpc.profile.{ ProfileService, ResponseEditAvatar }
import im.actor.server.api.ApiConversions._
import im.actor.server.file.FileErrors
import im.actor.server.persist
import im.actor.server.push.SeqUpdatesManagerRegion
import im.actor.server.sequence.SeqState
import im.actor.server.social.SocialManagerRegion
import im.actor.server.user.{ UserCommands, UserOffice, UserProcessorRegion, UserViewRegion }
import im.actor.server.util.{ FileStorageAdapter, ImageUtils, StringUtils }

object ProfileErrors {
  val NicknameInvalid = RpcError(400, "NICK_NAME_INVALID",
    "Invalid nick name. Valid nick name should contain from 5 to 32 characters, and may consist of latin characters, numbers and underscores", false, None)
  val NicknameBusy = RpcError(400, "NICK_NAME_Busy", "This nickname already belongs some other user, we are sorry!", false, None)
  val AboutTooLong = RpcError(400, "ABOUT_TOO_LONG",
    "About is too long. It should be no longer then 255 characters", false, None)
}

class ProfileServiceImpl()(
  implicit
  actorSystem:         ActorSystem,
  db:                  Database,
  userProcessorRegion: UserProcessorRegion,
  userViewRegion:      UserViewRegion,
  socialManagerRegion: SocialManagerRegion,
  seqUpdManagerRegion: SeqUpdatesManagerRegion,
  fsAdapter:           FileStorageAdapter
) extends ProfileService {

  import FileHelpers._
  import ImageUtils._

  override implicit val ec: ExecutionContext = actorSystem.dispatcher

  implicit val timeout = Timeout(5.seconds) // TODO: configurable

  override def jhandleEditAvatar(fileLocation: FileLocation, clientData: ClientData): Future[HandlerResult[ResponseEditAvatar]] = {
    // TODO: flatten

    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      withFileLocation(fileLocation, AvatarSizeLimit) {
        scaleAvatar(fileLocation.fileId, ThreadLocalRandom.current()) flatMap {
          case Right(avatar) ⇒
            for {
              UserCommands.UpdateAvatarAck(avatar, SeqState(seq, state)) ← DBIO.from(UserOffice.updateAvatar(client.userId, client.authId, Some(avatar)))
            } yield Ok(ResponseEditAvatar(
              avatar.get,
              seq,
              state.toByteArray
            ))
          case Left(e) ⇒
            throw FileErrors.LocationInvalid
        }
      }
    }

    db.run(toDBIOAction(authorizedAction)) recover {
      case FileErrors.LocationInvalid ⇒ Error(Errors.LocationInvalid)
    }
  }

  override def jhandleRemoveAvatar(clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      for {
        UserCommands.UpdateAvatarAck(_, SeqState(seq, state)) ← DBIO.from(UserOffice.updateAvatar(client.userId, client.authId, None))
      } yield Ok(ResponseSeq(seq, state.toByteArray))
    }

    db.run(toDBIOAction(authorizedAction))
  }

  override def jhandleEditName(name: String, clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData) map { implicit client ⇒
      DBIO.from(UserOffice.changeName(client.userId, name) map {
        case SeqState(seq, state) ⇒ Ok(ResponseSeq(seq, state.toByteArray))
      })
    }
    db.run(toDBIOAction(authorizedAction))
  }

  def jhandleEditNickName(nickname: Option[String], clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData) map { implicit client ⇒
      val action: Result[ResponseSeq] = for {
        trimmed ← point(nickname.map(_.trim))
        _ ← fromBoolean(ProfileErrors.NicknameInvalid)(trimmed.map(StringUtils.validNickName).getOrElse(true))
        _ ← if (trimmed.isDefined) {
          for {
            checkExist ← fromOption(ProfileErrors.NicknameInvalid)(trimmed)
            _ ← fromDBIOBoolean(ProfileErrors.NicknameBusy)(persist.User.nicknameExists(checkExist).map(exist ⇒ !exist))
          } yield ()
        } else point(())
        SeqState(seq, state) ← fromFuture(UserOffice.changeNickname(client.userId, client.authId, trimmed))
      } yield ResponseSeq(seq, state.toByteArray)
      action.run
    }
    db.run(toDBIOAction(authorizedAction))
  }

  def jhandleCheckNickName(nickname: String, clientData: ClientData): Future[HandlerResult[ResponseBool]] = {
    val authorizedAction = requireAuth(clientData) map { implicit client ⇒
      (for {
        _ ← fromBoolean(ProfileErrors.NicknameInvalid)(StringUtils.validNickName(nickname))
        exists ← fromDBIO(persist.User.nicknameExists(nickname.trim))
      } yield ResponseBool(!exists)).run
    }
    db.run(toDBIOAction(authorizedAction))
  }

  //todo: move validation inside of UserOffice
  def jhandleEditAbout(about: Option[String], clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData) map { implicit client ⇒
      val action: Result[ResponseSeq] = for {
        trimmed ← point(about.map(_.trim))
        _ ← fromBoolean(ProfileErrors.AboutTooLong)(trimmed.map(s ⇒ s.nonEmpty & s.length < 255).getOrElse(true))

        SeqState(seq, state) ← fromFuture(UserOffice.changeAbout(client.userId, client.authId, trimmed))
      } yield ResponseSeq(seq, state.toByteArray)
      action.run
    }
    db.run(toDBIOAction(authorizedAction))
  }
}