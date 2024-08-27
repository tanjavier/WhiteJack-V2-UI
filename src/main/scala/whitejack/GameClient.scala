package whitejack

import akka.actor.Address
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.cluster.typed._
import scalafx.application.Platform
import scalafx.collections.{ObservableBuffer, ObservableHashSet}
import whitejack.Model.User
import whitejack.Protocol.JsonSerializable
import whitejack.Util.{CardUtil, ClientUtil}

object GameClient {
  sealed trait Command extends JsonSerializable

  case object start extends Command
  case class StartJoin(name: String) extends Command
  final case class Joined(list: Iterable[User]) extends Command
  final case class SendMessageL(target: ActorRef[GameClient.Command], content: String) extends Command
  final case object FindTheServer extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command
  private final case class MemberChange(event: MemberEvent) extends Command
  private final case class ReachabilityChange(reachabilityEvent: ReachabilityEvent) extends Command

  val members = new ObservableHashSet[User]()
  val unreachables = new ObservableHashSet[Address]()
  var roomList = new ObservableHashSet[User]()
  val messageList = new ObservableBuffer[String]()

  members.onChange{(ns, _) =>
    Platform.runLater {
      Lobby.control.updateList(ns.toList.filter(y => ! unreachables.contains(y.ref.path.address)))
    }
  }

  unreachables.onChange{(ns, _) =>
    Platform.runLater {
      Lobby.control.updateList(members.toList.filter(y => ! unreachables.contains(y.ref.path.address)))
      for (user <- roomList.toList.filter(y => unreachables.contains(y.ref.path.address))) {
        if (user.ref == ClientUtil.hostRef.get) {
          ClientUtil.ownRef.foreach(_ ! HostLeaveRoomReceive)
        } else {
          ClientUtil.ownRef.foreach(_ ! ClientLeaveRoomReceive(user))
        }
        roomList -= user
      }
    }
  }

  roomList.onChange{(ns, _) =>
    ClientUtil.roomList = roomList
    Platform.runLater {
      Lobby.control.updateRoomList(roomList)
    }
  }

  messageList.onChange { (ns, _) =>
    ClientUtil.messageList = messageList
    Platform.runLater {
      Lobby.control.replaceText(messageList)
    }
  }

  final case class MemberList(list: Iterable[User]) extends Command

  final case object NewRoom extends Command

  final case class IsInvitable(from: ActorRef[GameClient.Command]) extends Command
  final case class IsInvitableResponse(userTarget: User, result: Boolean) extends Command
  final case class SendInvitation(target: ActorRef[GameClient.Command]) extends Command
  final case class ReceiveInvitation(from: User) extends Command
  final case class AcceptInvitation(sender: User) extends Command
  final case class RejectInvitation(sender: User) extends Command
  final case class InvitationResponse(choice: Boolean, from: User) extends Command
  final case class ChatMessage(sender: User, content: String) extends Command
  final case class LobbyList(list: Iterable[User]) extends Command
  final case class RoomList(list: Iterable[User]) extends Command

  final case object ResetRoomList extends Command
  final case class AddToRoomList(from: User) extends Command
  final case class RemoveFromRoomList(from: User) extends Command
  final case class HostLeaveRoom(from: User) extends Command
  final case object HostLeaveRoomReceive extends Command
  final case class ClientLeaveRoom(from: User) extends Command
  final case class ClientLeaveRoomReceive(from: User) extends Command

  final case class GameStart(target: ActorRef[GameClient.Command]) extends Command
  final case object GameStartReceive extends Command

  final case class  RoundStart(target: User) extends Command
  final case object RoundStartReceive extends Command

  final case class GiveCard(cardMeta: CardUtil, target: User) extends Command
  final case class ReceiveCard(cardMeta: CardUtil) extends Command

  final case class AnnounceTurn(currentUser: User, target: User) extends Command
  final case class AnnounceTurnReceive(currentUser: User) extends Command

  final case class PlayerHit(currentUser: User) extends Command
  final case class PlayerStand(currentUser: User) extends Command

  final case class IncreaseBetSend(hostRef: ActorRef[GameClient.Command]) extends Command
  final case class IncreaseBetReceive(from: User) extends Command

  final case class DecreaseBetSend(hostRef: ActorRef[GameClient.Command]) extends Command
  final case class DecreaseBetReceive(from: User) extends Command

  final case class UpdateBet(target: User, user: User, amt: Int) extends Command
  final case class UpdateBetReceive(user: User, amt: Int) extends Command

  final case class ConfirmBetSend(hostRef: ActorRef[GameClient.Command]) extends Command
  final case class ConfirmBetReceive(from: User) extends Command
  final case object ConfirmBetReceiveAck extends Command

  final case class UpdateBalAndBet(target: User, player: User, bal: Int, bet: Int) extends Command
  final case class UpdateBalAndBetReceive(player: User, bal: Int, bet: Int) extends Command

  final case class AnnounceHouseWin(target: User) extends Command
  final case object AnnounceHouseWinReceive extends Command

  final case class AnnounceWinResult(target: User, playerResult: String) extends Command
  final case class AnnounceWinResultReceive(playerResult: String) extends Command

  var defaultBehavior: Option[Behavior[GameClient.Command]] = None
  var remoteOpt: Option[ActorRef[Server.Command]] = None
  var nameOpt: Option[String] = None

  def messageStarted(): Behavior[GameClient.Command] =
    Behaviors.receive[GameClient.Command] { (context, message) =>
      message match {
        case ReachabilityChange(reachabilityEvent) =>
          reachabilityEvent match {
            case UnreachableMember(member) =>
              unreachables += member.address
              Behaviors.same
            case ReachableMember(member) =>
              unreachables -= member.address
              Behaviors.same
          }

        case ChatMessage(sender, content) =>
          Platform.runLater {
            messageList += s"${sender.name}: $content"
          }
          Behaviors.same
        case MemberList(list: Iterable[User]) =>
          members.clear()
          members ++= list
          Behaviors.same
        case NewRoom =>
          ClientUtil.hostRef = Option(context.self)
          roomList += User(ClientUtil.ownName, context.self)
          Behaviors.same
        case IsInvitable(from) =>
          if (roomList.size == 0) {
            from ! IsInvitableResponse(User(ClientUtil.ownName, context.self), result = true)
          } else {
            from ! IsInvitableResponse(User(ClientUtil.ownName, context.self), result = false)
          }
          Behaviors.same
        case IsInvitableResponse(from, result) =>
          Platform.runLater {
            Lobby.control.playerInviteResult(from, result)
          }
          Behaviors.same
        case SendInvitation(target) =>
          target ! ReceiveInvitation(User(ClientUtil.ownName, context.self))
          Behaviors.same
        case ReceiveInvitation(from) =>
          Platform.runLater {
            Lobby.control.playerInvited(from)
          }
          Behaviors.same
        case AcceptInvitation(sender) =>
          roomList += User(ClientUtil.ownName, context.self)
          ClientUtil.hostRef = Option(sender.ref)
          sender.ref ! InvitationResponse(choice = true, User(ClientUtil.ownName, context.self))
          Behaviors.same
        case RejectInvitation(sender) =>
          sender.ref ! InvitationResponse(choice = false, User(ClientUtil.ownName, context.self))
          Behaviors.same
        case InvitationResponse(choice, from) =>
          if (choice) {
            roomList += from
            for(outerUser <- roomList) {
              if (outerUser.ref != GameClientApp.ownRef) {
                for(user <- roomList) {
                  outerUser.ref ! GameClient.AddToRoomList(user)
                }
              }
            }
          }
          Platform.runLater {
            Lobby.control.showInvitationResponse(choice, from)
          }
          Behaviors.same
        case AddToRoomList(from) =>
          roomList += from
          Behaviors.same
        case RemoveFromRoomList(from) =>
          roomList -= from
          Behaviors.same
        case ResetRoomList =>
          roomList.clear()
          ClientUtil.hostRef = None
          Behaviors.same
        case HostLeaveRoom(target) =>
          target.ref ! GameClient.HostLeaveRoomReceive
          Behaviors.same
        case HostLeaveRoomReceive =>
          Platform.runLater {
            Lobby.control.hostLeft()
          }
          Behaviors.same
        case ClientLeaveRoom(target) =>
          target.ref ! GameClient.ClientLeaveRoomReceive(User(ClientUtil.ownName, context.self))
          Behaviors.same
        case ClientLeaveRoomReceive(from) =>
          ClientUtil.ownRef.foreach(_ ! GameClient.RemoveFromRoomList(from))
          Platform.runLater {
            if (ClientUtil.isPlaying)
              Board.control.clientSuddenLeft(from)
          }
          Behaviors.same

        case GameStart(target) =>
          target ! GameClient.GameStartReceive
          Behaviors.same
        case GameStartReceive =>
          Platform.runLater {
            Lobby.control.gameLoad()
          }
          Behaviors.same

        case RoundStart(target) =>
          target.ref ! GameClient.RoundStartReceive
          Behaviors.same
        case RoundStartReceive =>
          Platform.runLater {
            Board.control.roundStartReceive()
          }
          Behaviors.same

        case GiveCard(cardMeta, target) =>
          target.ref ! GameClient.ReceiveCard(cardMeta)
          Behaviors.same
        case ReceiveCard(cardMeta) =>
          Platform.runLater {
            Board.control.updateCard(cardMeta)
          }
          Behaviors.same
        case AnnounceTurn(currentUser, target) =>
          target.ref ! GameClient.AnnounceTurnReceive(currentUser)
          Behaviors.same
        case AnnounceTurnReceive(currentUser) =>
          Platform.runLater {
            Board.control.announceTurnReceive(currentUser)
          }
          Behaviors.same
        case PlayerHit(currentUser) =>
          Platform.runLater {
            Board.control.playerHitReceive(currentUser)
          }
          Behaviors.same
        case PlayerStand(currentUser) =>
          Platform.runLater {
            Board.control.playerStandReceive(currentUser)
          }
          Behaviors.same

        case IncreaseBetSend(hostRef) =>
          hostRef ! GameClient.IncreaseBetReceive(User(ClientUtil.ownName, context.self))
          Behaviors.same
        case IncreaseBetReceive(from) =>
          Platform.runLater {
            Board.control.increaseBetReceive(from)
          }
          Behaviors.same

        case DecreaseBetSend(hostRef) =>
          hostRef ! GameClient.DecreaseBetReceive(User(ClientUtil.ownName, context.self))
          Behaviors.same
        case DecreaseBetReceive(from) =>
          Platform.runLater {
            Board.control.decreaseBetReceive(from)
          }
          Behaviors.same

        case UpdateBet(target, user, amt) =>
          target.ref ! GameClient.UpdateBetReceive(user, amt)
          Behaviors.same
        case UpdateBetReceive(user, amt) =>
          Platform.runLater {
            Board.control.updateBetReceive(user, amt)
          }
          Behaviors.same

        case ConfirmBetSend(hostRef) =>
          hostRef ! GameClient.ConfirmBetReceive(User(ClientUtil.ownName, context.self))
          Behaviors.same
        case ConfirmBetReceive(from) =>
          Platform.runLater {
            Board.control.confirmBetReceive(from)
          }
          from.ref ! ConfirmBetReceiveAck
          Behaviors.same
        case ConfirmBetReceiveAck =>
          Platform.runLater {
            Board.control.confirmBetReceiveAck()
          }
          Behaviors.same

        case UpdateBalAndBet(target, player, bal, bet) =>
          target.ref ! GameClient.UpdateBalAndBetReceive(player, bal, bet)
          Behaviors.same
        case UpdateBalAndBetReceive(player, bal, bet) =>
          Platform.runLater {
            Board.control.updateBalAndBetReceive(player, bal, bet)
          }
          Behaviors.same

        case AnnounceHouseWin(target) =>
          target.ref ! GameClient.AnnounceHouseWinReceive
          Behaviors.same
        case AnnounceHouseWinReceive =>
          Platform.runLater {
            Board.control.announceHouseWin()
          }
          Behaviors.same

        case AnnounceWinResult(target, playerResult) =>
          target.ref ! GameClient.AnnounceWinResultReceive(playerResult)
          Behaviors.same
        case AnnounceWinResultReceive(playerResult) =>
          Platform.runLater {
            Board.control.announceWinResult(playerResult)
          }
          Behaviors.same
        case _=>
          Behaviors.unhandled
      }
    }.receiveSignal {
      case (context, PostStop) =>
        for (name <- nameOpt; remote <- remoteOpt){
          remote ! Server.Leave(name, context.self)
        }
        defaultBehavior.getOrElse(Behaviors.same)
    }

  def apply(): Behavior[GameClient.Command] =
    Behaviors.setup { context =>
      var counter = 0

      val reachabilityAdapter = context.messageAdapter(ReachabilityChange)
      Cluster(context.system).subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

      val listingAdapter: ActorRef[Receptionist.Listing] = context.messageAdapter { listing =>
        println(s"listingAdapter:listing: ${listing.toString}")
        GameClient.ListingResponse(listing)
      }
      context.system.receptionist ! Receptionist.Subscribe(Server.ServerKey, listingAdapter)

      defaultBehavior = Some(Behaviors.receiveMessage { message =>
        message match {
          case GameClient.start =>
            context.self ! FindTheServer
            Behaviors.same
          case FindTheServer =>
            println(s"Client received FindTheServer message")
            context.system.receptionist ! Receptionist.Find(Server.ServerKey, listingAdapter)
            Behaviors.same
          case ListingResponse(Server.ServerKey.Listing(listings)) =>
            val xs: Set[ActorRef[Server.Command]] = listings
            for (x <- xs) {
              remoteOpt = Some(x)
            }
            Behaviors.same
          case StartJoin(name) =>
            nameOpt = Option(name)
            remoteOpt.foreach (_ ! Server.JoinChat(name, context.self))
            Behaviors.same
          case GameClient.Joined(x) =>
            Platform.runLater {
              Lobby.control.playerJoined()
              GameClientApp.stage.setTitle(s"WhiteJack | ${nameOpt.get}")
            }
            members.clear()
            members ++= x
            messageStarted()
          case _=>
            Behaviors.unhandled
        }
      })
      defaultBehavior.get
    }
}
