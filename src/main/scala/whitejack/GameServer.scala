package whitejack

import akka.actor.Address
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.ClusterEvent.{ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.cluster.typed._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import whitejack.Model.User
import whitejack.Protocol.JsonSerializable
import com.typesafe.config.ConfigFactory
import scalafx.collections.ObservableHashSet

object Server {
  sealed trait Command extends JsonSerializable
  case class JoinChat(name: String, from: ActorRef[GameClient.Command]) extends Command
  case class Leave(name: String, from: ActorRef[GameClient.Command]) extends Command
  private final case class ReachabilityChange(reachabilityEvent: ReachabilityEvent) extends Command
  private var registeredNames = Set.empty[String]

  val ServerKey: ServiceKey[Server.Command] = ServiceKey("ChatServer")

  val members = new ObservableHashSet[User]()
  val unreachables = new ObservableHashSet[Address]()

  members.onChange{(ns, _) =>
    for(member <- ns){
      member.ref ! GameClient.MemberList(ns.toList.filter(y => ! unreachables.contains(y.ref.path.address)))
    }
  }

  unreachables.onChange{(ns, _) =>
    for(member <- members){
      member.ref ! GameClient.MemberList(members.toList.filter(y => ! unreachables.contains(y.ref.path.address)))
    }
  }

  def apply(): Behavior[Server.Command] = Behaviors.setup { context =>
    context.system.receptionist ! Receptionist.Register(ServerKey, context.self)

    val reachabilityAdapter = context.messageAdapter(ReachabilityChange)
    Cluster(context.system).subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

    Behaviors.receiveMessage { message =>
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

        case JoinChat(name, from) =>
          members += Model.User(name, from)
          from ! GameClient.Joined(members.toList.filter(y => ! unreachables.contains(y.ref.path.address)))
          Behaviors.same

        case Leave(name, from) =>
          members -= Model.User(name, from)
          Behaviors.same
      }
    }
  }
}

object GameServer extends App {
  val config = ConfigFactory.load()
  val mainSystem = akka.actor.ActorSystem("HelloSystem", Configuration.askServerConfig(22222).withFallback(config))
  val typedSystem: ActorSystem[Nothing] = mainSystem.toTyped
  val cluster = Cluster(typedSystem)
  cluster.manager ! Join(cluster.selfMember.address)
  AkkaManagement(mainSystem).start()
  ClusterBootstrap(mainSystem).start()
  mainSystem.spawn(Server(), "ChatServer")
}
