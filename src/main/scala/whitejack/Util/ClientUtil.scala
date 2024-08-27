package whitejack.Util

import akka.actor.typed.ActorRef
import whitejack.Model.User
import scalafx.collections.{ObservableBuffer, ObservableHashSet}
import whitejack.GameClient

object ClientUtil {
  var loadFXMLCount: Int = 0
  var isPlaying: Boolean = false

  var ownRef: Option[ActorRef[GameClient.Command]] = None
  var ownName: String = ""

  var roomList = new ObservableHashSet[User]()
  var messageList = new ObservableBuffer[String]()

  var hostRef: Option[ActorRef[GameClient.Command]] = None

  def isHost: Boolean = {
    if (ownRef == hostRef) {
      true
    } else {
      false
    }
  }
}
