package whitejack.Model

import akka.actor.typed.ActorRef
import whitejack.GameClient
import whitejack.Protocol.JsonSerializable

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class Room(name: String, ref: ActorRef[GameClient.Command]) extends JsonSerializable{
  val roomUser: mutable.HashSet[User] = new mutable.HashSet[User]()
  var storedMatrix: Array[Array[Int]] = Array(Array(0))
  val storedMessage: ArrayBuffer[String] = new ArrayBuffer[String]()

  override def toString = name
}
