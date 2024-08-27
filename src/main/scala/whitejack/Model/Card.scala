package whitejack.Model

import whitejack.Protocol.JsonSerializable

case class Card(rank: String, suit: String) extends JsonSerializable {
  override def toString: String = {s"${rank}_of_${suit}"}
}


