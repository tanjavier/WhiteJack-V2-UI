package whitejack.Controller

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.text.Text
import scalafxml.core.macros.sfxml
import whitejack.Model.User
import whitejack.Util.ClientUtil
import whitejack.{Board, GameClient, GameClientApp, MainMenu, Server}

@sfxml
class LobbyController (
  private var playerName: TextField,
  private var topText: Text,
  private var lobbyList: ListView[User],
  private var roomList: ListView[User],
  private var txtMessage: TextField,
  private var sendButton: Button,
  private var joinBtn: Button,
  private var textList: ListView[String],
  private var inviteBtn: Button,
  private var newBtn: Button,
  private var startBtn: Button,
  private var leaveBtn: Button) {

  newBtn.disable = true
  inviteBtn.disable = true
  startBtn.disable = true
  leaveBtn.disable = true
  txtMessage.disable = true
  sendButton.disable = true

  def playerJoin(action: ActionEvent): Unit = {
    if (playerName.text() != "") {
      ClientUtil.ownName = playerName.text()
      ClientUtil.ownRef.foreach(_ ! GameClient.StartJoin(playerName.text()))
    } else {
      new Alert(AlertType.Warning) {
        title = "ERROR"
        headerText = "Name must not be empty"
        contentText = "Please insert a name."
      }.showAndWait()
    }
  }

  def playerJoined(): Unit = {
    newBtn.disable = false
    txtMessage.disable = false
    sendButton.disable = false
    topText.text = s"Welcome, ${ClientUtil.ownName}!"
    playerName.setVisible(false)
    joinBtn.setVisible(false)
  }

  def playerInvite(action: ActionEvent): Unit = {
    val indexOpt = lobbyList.selectionModel().selectedIndex.toInt
    if (indexOpt < 0) {
      new Alert(AlertType.Warning) {
        title = "ERROR"
        headerText = "No player selected"
        contentText = "Please select a player first."
      }.showAndWait()
    } else {
      val userRefOpt = lobbyList.selectionModel().selectedItem.value.ref
      if (!ClientUtil.isHost) {
        new Alert(AlertType.Warning) {
          title = "ERROR"
          headerText = "Only host can invite"
          contentText = "Wait for host or join another room."
        }.showAndWait()
      } else if (ClientUtil.roomList.size >= 3) {
        new Alert(AlertType.Warning) {
          title = "ERROR"
          headerText = "Room full"
          contentText = "Wait for a player to leave before inviting again."
        }.showAndWait()
      } else if (userRefOpt == GameClientApp.ownRef) {
        new Alert(AlertType.Warning) {
          title = "ERROR"
          headerText = "Invalid player"
          contentText = "Invite another player."
        }.showAndWait()
      } else {
        ClientUtil.ownRef.foreach(userRefOpt ! GameClient.IsInvitable(_))
      }
    }
  }

  def playerInviteResult(from: User, result: Boolean): Unit = {
    if (result) {
      ClientUtil.ownRef.foreach(_ ! GameClient.SendInvitation(from.ref))
    } else {
      new Alert(AlertType.Warning) {
        title = "ERROR"
        headerText = s"${from.name} is busy right now"
        contentText = "Invite another player."
      }.showAndWait()
    }
  }

  def playerInvited(from: User): Unit = {
    val alert = new Alert(AlertType.Confirmation) {
      title = "Invitation to Room"
      headerText = s"${from.name} has invited you to their room."
      contentText = "Would you like to join?"
    }

    val result = alert.showAndWait()
    result match {
      case Some(ButtonType.OK) => {
        newBtn.disable = true
        leaveBtn.disable = false
        ClientUtil.ownRef.foreach(_ ! GameClient.AcceptInvitation(User(ClientUtil.ownName, from.ref)))
      }
      case _ => ClientUtil.ownRef.foreach(_ ! GameClient.RejectInvitation(User(ClientUtil.ownName, from.ref)))
    }
  }

  def showInvitationResponse(choice: Boolean, from: User): Unit = {
    if (choice) {
      new Alert(AlertType.Information) {
        title = "Invitation Accepted"
        headerText = s"${from.name} has accepted your invitation."
        contentText = ""
      }.showAndWait()
    } else {
      new Alert(AlertType.Information) {
        title = "Invitation Rejected"
        headerText = s"${from.name} has rejected your invitation."
        contentText = ""
      }.showAndWait()
    }
  }

  def playerNew(action: ActionEvent): Unit = {
    inviteBtn.disable = false
    newBtn.disable = true
    startBtn.disable = false
    leaveBtn.disable = false
    ClientUtil.ownRef.foreach(_ ! GameClient.NewRoom)
  }

  def playerLeave(action: ActionEvent): Unit = {
    inviteBtn.disable = true
    newBtn.disable = false
    startBtn.disable = true
    leaveBtn.disable = true

    if (ClientUtil.isHost) {
      for (user <- ClientUtil.roomList) {
        if (user.ref != GameClientApp.ownRef) {
          ClientUtil.ownRef.foreach(_ ! GameClient.HostLeaveRoom(user))
        }
      }
    } else {
      for (user <- ClientUtil.roomList) {
        if (user.ref != GameClientApp.ownRef) {
          ClientUtil.ownRef.foreach(_ ! GameClient.ClientLeaveRoom(user))
        }
      }
    }
    ClientUtil.ownRef.foreach(_ ! GameClient.ResetRoomList)
  }

  def hostLeft(): Unit = {
    newBtn.disable = false
    leaveBtn.disable = true
    ClientUtil.ownRef.foreach(_ ! GameClient.ResetRoomList)
    new Alert(AlertType.Warning) {
      title = "ERROR"
      headerText = "The host left the room"
      contentText = "Please join another room."
    }.showAndWait()
  }

  def updateList(x: Iterable[User]): Unit = {
    lobbyList.items = new ObservableBuffer[User]() ++= x
  }

  def updateRoomList(x: Iterable[User]): Unit = {
    roomList.items = new ObservableBuffer[User]() ++= x
  }

  def replaceText(x: Iterable[String]): Unit = {
    textList.items = new ObservableBuffer[String]() ++= x
  }

  def gameStart(action: ActionEvent): Unit = {
    inviteBtn.disable = true
    newBtn.disable = false
    leaveBtn.disable = true
    startBtn.disable = true
    for (user <- ClientUtil.roomList) {
      ClientUtil.ownRef.foreach(_ ! GameClient.GameStart(user.ref))
    }
  }

  def gameLoad(): Unit = {
    ClientUtil.isPlaying = true
    if (ClientUtil.loadFXMLCount == 0) {
      ClientUtil.loadFXMLCount += 1
      Board
    } else {
      Board.load()
    }
  }

  def handleSend(actionEvent: ActionEvent): Unit = {
    if (txtMessage.text().nonEmpty) {
      val message = GameClient.ChatMessage(User(ClientUtil.ownName, GameClientApp.ownRef), txtMessage.text())
      GameClient.members.foreach { user => user.ref ! message}
      txtMessage.clear()
    }
  }

  def handleClose(): Unit = {
    MainMenu.load()
  }
}