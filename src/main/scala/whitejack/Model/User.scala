package whitejack.Model

import akka.actor.typed.ActorRef
import whitejack.GameClient
import whitejack.Protocol.JsonSerializable

case class User(name: String, ref: ActorRef[GameClient.Command]) extends JsonSerializable {
  override def toString: String = name
}
