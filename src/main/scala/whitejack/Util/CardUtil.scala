package whitejack.Util

import whitejack.Model.{Card, User}
import whitejack.Protocol.JsonSerializable

case class CardUtil(isPlayer: Boolean, user: User, card: Card, cardPosition: Int) extends JsonSerializable {
  override def toString: String = {
    s"${isPlayer}_${user}_${card}_${cardPosition}"
  }
}