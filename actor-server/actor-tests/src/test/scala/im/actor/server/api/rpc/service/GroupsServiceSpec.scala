package im.actor.server.api.rpc.service

import scala.util.Random

import com.google.protobuf.CodedInputStream
import org.scalatest.Inside._
import slick.dbio.DBIO

import im.actor.api.rpc._
import im.actor.api.rpc.groups._
import im.actor.api.rpc.messaging._
import im.actor.api.rpc.misc.ResponseSeqDate
import im.actor.api.rpc.peers.{ OutPeer, PeerType, UserOutPeer }
import im.actor.server._
import im.actor.server.api.rpc.service.groups.{ GroupInviteConfig, GroupRpcErrors, GroupsServiceImpl }
import im.actor.server.api.rpc.service.sequence.{ SequenceServiceConfig, SequenceServiceImpl }
import im.actor.server.oauth.{ GoogleProvider, OAuth2GoogleConfig }
import im.actor.server.presences.{ GroupPresenceManager, PresenceManager }
import im.actor.server.util.{ ACLUtils, GroupServiceMessages }

class GroupsServiceSpec extends BaseAppSuite with GroupsServiceHelpers with MessageParsing with ImplicitGroupRegions {
  behavior of "GroupsService"

  it should "send invites on group creation" in e1

  it should "send updates on group invite" in e2

  it should "send updates ot title change" in e3

  it should "persist service messages in history" in e4

  it should "generate invite url for group member" in e5

  it should "not generate invite url for group non members" in e6

  it should "revoke invite token and generate new token for group member" in e7

  it should "allow user to join group by correct invite link and send correct updates" in e8

  it should "not allow group member to join group by invite link" in e9

  it should "send updates on user join" in e10

  it should "send UserInvited and UserJoined on user's first MessageRead" in e11

  it should "receive userJoined once" in e12

  it should "not allow to create group with empty name" in e13

  "Creator of group" should "be groupAdmin" in e14

  "MakeUserAdmin" should "allow group member to become admin" in e15

  it should "forbid to perform action by non-admin" in e16

  it should "return error when user is already admin" in e17

  "EditGroupAbout" should "allow group admin to change 'about'" in e18

  it should "forbid to change 'about' by non-admin" in e19

  it should "set 'about' to empty when None comes" in e20

  it should "forbid to set invalid 'about' field (empty, or longer than 255 characters)" in e21

  "EditGroupTopic" should "allow any group member to change topic" in e22

  it should "forbid to set invalid topic (empty, or longer than 255 characters)" in e23

  it should "set topic to empty when None comes" in e24

  implicit val sessionRegion = buildSessionRegionProxy()

  implicit val presenceManagerRegion = PresenceManager.startRegion()
  implicit val groupPresenceManagerRegion = GroupPresenceManager.startRegion()

  val groupInviteConfig = GroupInviteConfig("http://actor.im")

  val sequenceConfig = SequenceServiceConfig.load().toOption.get

  val sequenceService = new SequenceServiceImpl(sequenceConfig)
  val messagingService = messaging.MessagingServiceImpl(mediator)
  implicit val service = new GroupsServiceImpl(groupInviteConfig)
  val oauthGoogleConfig = OAuth2GoogleConfig.load(system.settings.config.getConfig("services.google.oauth"))
  implicit val oauth2Service = new GoogleProvider(oauthGoogleConfig)
  implicit val authService = buildAuthService()

  def e1() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()

    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set(user2.id)).groupPeer

    whenReady(db.run(persist.sequence.SeqUpdate.findLast(authId2))) { uOpt ⇒
      val u = uOpt.get
      u.header should ===(UpdateGroupInvite.header)
    }

    whenReady(db.run(persist.GroupUser.findUserIds(groupOutPeer.groupId))) { userIds ⇒
      userIds.toSet should ===(Set(user1.id, user2.id))
    }
  }

  def e2() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    whenReady(service.handleInviteUser(groupOutPeer, Random.nextLong(), user2OutPeer)) { resp ⇒
      resp should matchPattern {
        case Ok(ResponseSeqDate(1001, _, _)) ⇒
      }
    }

    Thread.sleep(300)

    whenReady(db.run(persist.sequence.SeqUpdate.find(authId2))) { updates ⇒
      updates.map(_.header) should ===(
        Seq(UpdateGroupInvite.header)
      )
    }

    whenReady(db.run(persist.sequence.SeqUpdate.find(authId1))) { updates ⇒
      updates.length should ===(2)
      updates.head.header should ===(UpdateGroupUserInvited.header)
    }
  }

  def e3() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set(user2.id)).groupPeer

    whenReady(service.handleEditGroupTitle(groupOutPeer, Random.nextLong(), "Very fun group")) { resp ⇒
      resp should matchPattern {
        case Ok(ResponseSeqDate(1002, _, _)) ⇒
      }
    }

    whenReady(db.run(persist.sequence.SeqUpdate.find(authId1))) { updates ⇒
      updates.head.header should ===(UpdateGroupTitleChanged.header)
    }

    whenReady(db.run(persist.sequence.SeqUpdate.find(authId2))) { updates ⇒
      updates.head.header should ===(UpdateGroupTitleChanged.header)
    }
  }

  def e4() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
      serviceMessages should have length 1
      serviceMessages
        .map { e ⇒ parseMessage(e.messageContentData) } shouldEqual
        Vector(Right(GroupServiceMessages.groupCreated))

    }

    whenReady(service.handleInviteUser(groupOutPeer, Random.nextLong(), user2OutPeer)) { resp ⇒
      resp should matchPattern { case Ok(_) ⇒ }
      whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 2
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) } shouldEqual
          Vector(
            Right(GroupServiceMessages.userInvited(user2.id)),
            Right(GroupServiceMessages.groupCreated)
          )
      }
      whenReady(db.run(persist.HistoryMessage.find(user2.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 1
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) } shouldEqual
          Vector(Right(GroupServiceMessages.userInvited(user2.id)))
      }
    }

    //TODO: is it ok to remove avatar of group without avatar
    whenReady(service.handleRemoveGroupAvatar(groupOutPeer, Random.nextLong())) { resp ⇒
      resp should matchPattern { case Ok(_) ⇒ }

      Thread.sleep(500)

      whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 3
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) } shouldEqual
          Vector(
            Right(GroupServiceMessages.changedAvatar(None)),
            Right(GroupServiceMessages.userInvited(user2.id)),
            Right(GroupServiceMessages.groupCreated)
          )
      }
      whenReady(db.run(persist.HistoryMessage.find(user2.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 2
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) } shouldEqual
          Vector(
            Right(GroupServiceMessages.changedAvatar(None)),
            Right(GroupServiceMessages.userInvited(user2.id))
          )
      }
    }

    whenReady(service.handleEditGroupTitle(groupOutPeer, Random.nextLong(), "Not fun group")) { resp ⇒
      resp should matchPattern { case Ok(_) ⇒ }
      whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 4
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) }.head shouldEqual Right(GroupServiceMessages.changedTitle("Not fun group"))
      }
    }

    whenReady(service.handleLeaveGroup(groupOutPeer, Random.nextLong())(ClientData(authId2, sessionId, Some(user2.id)))) { resp ⇒
      resp should matchPattern { case Ok(_) ⇒ }
      whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 5
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) }.head shouldEqual Right(GroupServiceMessages.userLeft(user2.id))
      }
    }

    whenReady(service.handleInviteUser(groupOutPeer, Random.nextLong(), user2OutPeer)) { resp ⇒
      resp should matchPattern { case Ok(_) ⇒ }
      whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 6
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) }.head shouldEqual Right(GroupServiceMessages.userInvited(user2.id))
      }
    }

    whenReady(service.handleKickUser(groupOutPeer, Random.nextLong(), user2OutPeer)) { resp ⇒
      resp should matchPattern { case Ok(_) ⇒ }
      whenReady(db.run(persist.HistoryMessage.find(user1.id, models.Peer.group(groupOutPeer.groupId)))) { serviceMessages ⇒
        serviceMessages should have length 7
        serviceMessages.map { e ⇒ parseMessage(e.messageContentData) }.head shouldEqual Right(GroupServiceMessages.userKicked(user2.id))
      }
    }

  }

  def e5() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set(user2.id)).groupPeer

    {
      implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))
      var expUrl: String = ""
      whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
        inside(resp) {
          case Ok(ResponseInviteUrl(url)) ⇒
            url should startWith(groupInviteConfig.baseUrl)
            expUrl = url
        }
      }
      whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
        inside(resp) {
          case Ok(ResponseInviteUrl(url)) ⇒
            url should startWith(groupInviteConfig.baseUrl)
            url shouldEqual expUrl
        }
      }
    }

    {
      implicit val clientData = ClientData(authId2, sessionId, Some(user2.id))
      var expUrl: String = ""
      whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
        inside(resp) {
          case Ok(ResponseInviteUrl(url)) ⇒
            url should startWith(groupInviteConfig.baseUrl)
            expUrl = url
        }
      }
      whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
        inside(resp) {
          case Ok(ResponseInviteUrl(url)) ⇒
            url should startWith(groupInviteConfig.baseUrl)
            url shouldEqual expUrl
        }
      }
    }

    val findTokens =
      for {
        tokens ← DBIO.sequence(List(
          persist.GroupInviteToken.find(groupOutPeer.groupId, user1.id),
          persist.GroupInviteToken.find(groupOutPeer.groupId, user2.id)
        ))
      } yield tokens.flatten
    whenReady(db.run(findTokens)) { tokens ⇒
      tokens should have length 2
      tokens.foreach(_.groupId shouldEqual groupOutPeer.groupId)
      tokens.map(_.creatorId) should contain allOf (user1.id, user2.id)
    }
  }

  def e6() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    {
      implicit val clientData = ClientData(authId2, sessionId, Some(user2.id))
      whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
        resp should matchNotAuthorized
      }
    }

  }

  def e7() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    var expUrl: String = ""
    whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
      inside(resp) {
        case Ok(ResponseInviteUrl(url)) ⇒
          url should startWith(groupInviteConfig.baseUrl)
          expUrl = url
      }
    }
    whenReady(service.handleRevokeInviteUrl(groupOutPeer)) { resp ⇒
      inside(resp) {
        case Ok(ResponseInviteUrl(url)) ⇒
          url should startWith(groupInviteConfig.baseUrl)
          url should not equal expUrl
      }
    }

    whenReady(db.run(persist.GroupInviteToken.find(groupOutPeer.groupId, user1.id))) { tokens ⇒
      tokens should have length 1
    }

  }

  def e8() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData = ClientData(authId1, sessionId, Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Invite Fun group", Set.empty).groupPeer

    whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
      inside(resp) {
        case Ok(ResponseInviteUrl(url)) ⇒
          url should startWith(groupInviteConfig.baseUrl)

          {
            implicit val clientData = ClientData(authId2, sessionId, Some(user2.id))
            whenReady(service.handleJoinGroup(url)) { resp ⇒
              resp should matchPattern {
                case Ok(ResponseJoinGroup(_, _, _, _, _, _)) ⇒
              }
            }
          }
      }
    }
    whenReady(db.run(persist.GroupUser.findUserIds(groupOutPeer.groupId))) { userIds ⇒
      userIds should have length 2
      userIds should contain allOf (user1.id, user2.id)
    }
  }

  def e9() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    implicit val clientData = ClientData(authId1, createSessionId(), Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set(user2.id)).groupPeer

    whenReady(service.handleGetGroupInviteUrl(groupOutPeer)) { resp ⇒
      inside(resp) {
        case Ok(ResponseInviteUrl(url)) ⇒
          url should startWith(groupInviteConfig.baseUrl)

          {
            implicit val clientData = ClientData(authId2, createSessionId(), Some(user2.id))
            whenReady(service.handleJoinGroup(url)) { resp ⇒
              inside(resp) {
                case Error(err) ⇒ err shouldEqual GroupRpcErrors.UserAlreadyInvited
              }
            }
          }
      }
    }
  }

  def e10() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    implicit val clientData1 = ClientData(authId1, sessionId, Some(user1.id))
    val clientData2 = ClientData(authId2, sessionId, Some(user2.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData1.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val createGroupResponse = createGroup("Invite Fun group", Set.empty)

    val groupOutPeer = createGroupResponse.groupPeer

    whenReady(service.jhandleGetGroupInviteUrl(groupOutPeer, clientData1)) { resp ⇒
      inside(resp) {
        case Ok(ResponseInviteUrl(url)) ⇒
          url should startWith(groupInviteConfig.baseUrl)

          whenReady(service.jhandleJoinGroup(url, clientData2))(_ ⇒ ())

          whenReady(sequenceService.jhandleGetDifference(createGroupResponse.seq, createGroupResponse.state, clientData1)) { diff ⇒
            val resp = diff.toOption.get

            val updates = resp.updates
            updates should have length 1

            val update = UpdateMessage.parseFrom(CodedInputStream.newInstance(updates.head.update)).right.toOption.get
            update.message shouldEqual GroupServiceMessages.userJoined
          }
      }
    }
    whenReady(db.run(persist.GroupUser.findUserIds(groupOutPeer.groupId))) { userIds ⇒
      userIds should have length 2
      userIds should contain allOf (user1.id, user2.id)
    }
  }

  def e11() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val sessionId = createSessionId()
    val clientData1 = ClientData(authId1, sessionId, Some(user1.id))
    val clientData2 = ClientData(authId2, sessionId, Some(user2.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData1.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = {
      implicit val clientData = clientData1
      val groupOutPeer = createGroup("Invite Fun group", Set.empty).groupPeer

      whenReady(service.handleInviteUser(groupOutPeer, Random.nextLong, user2OutPeer)) { _ ⇒ }

      groupOutPeer
    }

    {
      implicit val clientData = clientData2
      // send it twice to ensure that ServiceMessage isn't sent twice
      whenReady(messagingService.handleMessageRead(OutPeer(PeerType.Group, groupOutPeer.groupId, groupOutPeer.accessHash), System.currentTimeMillis))(identity)
      whenReady(messagingService.handleMessageRead(OutPeer(PeerType.Group, groupOutPeer.groupId, groupOutPeer.accessHash), System.currentTimeMillis))(identity)
    }

    Thread.sleep(2000)

    {
      implicit val clientData = clientData1

      whenReady(sequenceService.handleGetDifference(0, Array.empty)) { diff ⇒
        val resp = diff.toOption.get

        /**
         * Updates should be:
         * * UpdateGroupInvite
         * * UpdateGroupUserInvited
         * * UpdateMessageRead
         * * UpdateMessageRead
         * * UpdateMessage with GroupServiceMessages.userJoined message
         */
        val updates = resp.updates
        updates should have length 5

        val update = UpdateMessage.parseFrom(CodedInputStream.newInstance(updates(3).update)).right.toOption.get
        update.message shouldEqual GroupServiceMessages.userJoined
      }
    }

  }

  def e12() = {

    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))
    val clientData2 = ClientData(authId2, createSessionId(), Some(user2.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData1.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = {
      implicit val clientData = clientData1
      createGroup("Fun group", Set.empty).groupPeer
    }

    val peer = OutPeer(PeerType.Group, groupOutPeer.groupId, groupOutPeer.accessHash)

    val url = whenReady(service.jhandleGetGroupInviteUrl(groupOutPeer, clientData1)) {
      _.toOption.get.url
    }

    messagingService.jhandleSendMessage(peer, 22324L, TextMessage("hello", Vector.empty, None), clientData1)

    whenReady(service.jhandleJoinGroup(url, clientData2)) { resp ⇒
      resp should matchPattern {
        case Ok(ResponseJoinGroup(_, _, _, _, _, _)) ⇒
      }
    }

    whenReady(messagingService.jhandleMessageRead(peer, System.currentTimeMillis, clientData2)) { _ ⇒ }

    Thread.sleep(1000)

    whenReady(sequenceService.jhandleGetDifference(0, Array.empty, clientData1)) { diff ⇒
      val resp = diff.toOption.get
      val updates = resp.updates

      /**
       * updates should be:
       * * UpdateGroupInvite
       * * ServiceExGroupCreated
       * * UpdateMessage
       * * UpdateMessageRead
       */
      updates should have length 4
      val update = UpdateMessage.parseFrom(CodedInputStream.newInstance(updates(2).update)).right.toOption.get
      update.message shouldEqual GroupServiceMessages.userJoined
    }

  }

  def e13() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    implicit val clientData = ClientData(authId1, createSessionId(), Some(user1.id))

    whenReady(service.handleCreateGroup(1L, "", Vector.empty)) { resp ⇒
      inside(resp) {
        case Error(GroupRpcErrors.WrongGroupTitle) ⇒
      }
    }
  }

  def e14() = {
    val (user1, authId1, _) = createUser()
    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    whenReady(db.run(persist.GroupUser.find(groupOutPeer.groupId, user1.id))) { groupUser ⇒
      groupUser shouldBe defined
      groupUser.get.isAdmin shouldEqual true
    }
  }

  def e15() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData1.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set(user2.id)).groupPeer

    whenReady(service.handleMakeUserAdmin(groupOutPeer, user2OutPeer)) { resp ⇒
      inside(resp) {
        case Ok(ResponseMakeUserAdmin(members, _, _)) ⇒
          members.find(_.userId == user2.id) foreach (_.isAdmin shouldEqual Some(true))
      }
    }
  }

  def e16() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()
    val (user3, authId3, _) = createUser()

    val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))
    val clientData2 = ClientData(authId2, createSessionId(), Some(user2.id))

    val user3Model = getUserModel(user3.id)
    val user3AccessHash = ACLUtils.userAccessHash(clientData2.authId, user3.id, user3Model.accessSalt)
    val user3OutPeer = UserOutPeer(user3.id, user3AccessHash)

    val groupOutPeer = {
      implicit val clientData = clientData1
      createGroup("Fun group", Set(user2.id)).groupPeer
    }

    whenReady(service.jhandleMakeUserAdmin(groupOutPeer, user3OutPeer, clientData2)) { resp ⇒
      resp shouldEqual Error(CommonErrors.forbidden("Only admin can perform this action."))
    }
  }

  def e17() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val user2Model = getUserModel(user2.id)
    val user2AccessHash = ACLUtils.userAccessHash(clientData1.authId, user2.id, user2Model.accessSalt)
    val user2OutPeer = UserOutPeer(user2.id, user2AccessHash)

    val groupOutPeer = createGroup("Fun group", Set(user2.id)).groupPeer

    whenReady(service.handleMakeUserAdmin(groupOutPeer, user2OutPeer)) { resp ⇒
      resp should matchPattern {
        case Ok(_: ResponseMakeUserAdmin) ⇒
      }
    }

    whenReady(service.handleMakeUserAdmin(groupOutPeer, user2OutPeer)) { resp ⇒
      resp shouldEqual Error(GroupRpcErrors.UserAlreadyAdmin)
    }
  }

  def e18() = {
    val (user1, authId1, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    val about = Some("It is group for fun")
    whenReady(service.handleEditGroupAbout(groupOutPeer, 1L, about)) { resp ⇒
      resp should matchPattern {
        case Ok(_: ResponseSeqDate) ⇒
      }
    }

    whenReady(db.run(persist.Group.find(groupOutPeer.groupId))) { group ⇒
      group.get.about shouldEqual about
    }
  }

  def e19() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))
    val clientData2 = ClientData(authId2, createSessionId(), Some(user2.id))

    val groupOutPeer = {
      implicit val clientData = clientData1
      createGroup("Fun group", Set(user2.id)).groupPeer
    }

    whenReady(service.jhandleEditGroupAbout(groupOutPeer, 1L, Some("It is group for fun"), clientData2)) { resp ⇒
      resp shouldEqual Error(CommonErrors.forbidden("Only admin can perform this action."))
    }
  }

  def e20() = {
    val (user1, authId1, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    whenReady(service.handleEditGroupAbout(groupOutPeer, 1L, None)) { resp ⇒
      resp should matchPattern {
        case Ok(_: ResponseSeqDate) ⇒
      }
    }

    whenReady(db.run(persist.Group.find(groupOutPeer.groupId))) { group ⇒
      group.get.about shouldEqual None
    }
  }

  def e21() = {
    val (user1, authId1, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    val longAbout = 1 to 300 map (e ⇒ ".") mkString ""
    whenReady(service.handleEditGroupAbout(groupOutPeer, 1L, Some(longAbout))) { resp ⇒
      resp shouldEqual Error(GroupRpcErrors.AboutTooLong)
    }

    val emptyAbout = ""
    whenReady(service.handleEditGroupAbout(groupOutPeer, 1L, Some(emptyAbout))) { resp ⇒
      resp shouldEqual Error(GroupRpcErrors.AboutTooLong)
    }

    whenReady(db.run(persist.Group.find(groupOutPeer.groupId))) { group ⇒
      group.get.about shouldEqual None
    }
  }

  def e22() = {
    val (user1, authId1, _) = createUser()
    val (user2, authId2, _) = createUser()

    val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))
    val clientData2 = ClientData(authId2, createSessionId(), Some(user2.id))

    val groupOutPeer = {
      implicit val cd = clientData1
      createGroup("Fun group", Set(user2.id)).groupPeer
    }

    val topic1 = Some("Fun stufff")
    whenReady(service.jhandleEditGroupTopic(groupOutPeer, 1L, topic1, clientData1)) { resp ⇒
      resp should matchPattern {
        case Ok(_: ResponseSeqDate) ⇒
      }
    }

    val topic2 = Some("Fun stuff. Typo!")
    whenReady(service.jhandleEditGroupTopic(groupOutPeer, 1L, topic2, clientData2)) { resp ⇒
      resp should matchPattern {
        case Ok(_: ResponseSeqDate) ⇒
      }
    }

    whenReady(db.run(persist.Group.find(groupOutPeer.groupId))) { group ⇒
      group.get.topic shouldEqual topic2
    }

  }

  def e23() = {
    val (user1, authId1, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    val longTopic = 1 to 300 map (e ⇒ ".") mkString ""
    whenReady(service.handleEditGroupTopic(groupOutPeer, 1L, Some(longTopic))) { resp ⇒
      resp shouldEqual Error(GroupRpcErrors.TopicTooLong)
    }

    val emptyTopic = ""
    whenReady(service.handleEditGroupTopic(groupOutPeer, 1L, Some(emptyTopic))) { resp ⇒
      resp shouldEqual Error(GroupRpcErrors.TopicTooLong)
    }

    whenReady(db.run(persist.Group.find(groupOutPeer.groupId))) { group ⇒
      group.get.topic shouldEqual None
    }

  }

  def e24() = {
    val (user1, authId1, _) = createUser()

    implicit val clientData1 = ClientData(authId1, createSessionId(), Some(user1.id))

    val groupOutPeer = createGroup("Fun group", Set.empty).groupPeer

    whenReady(service.handleEditGroupTopic(groupOutPeer, 1L, None)) { resp ⇒
      resp should matchPattern {
        case Ok(_: ResponseSeqDate) ⇒
      }
    }

    whenReady(db.run(persist.Group.find(groupOutPeer.groupId))) { group ⇒
      group.get.topic shouldEqual None
    }

  }

}
