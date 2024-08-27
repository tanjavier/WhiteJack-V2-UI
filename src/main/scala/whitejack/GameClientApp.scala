package whitejack

import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.typed._
import com.typesafe.config.{Config, ConfigFactory}
import javafx.scene.layout.{AnchorPane, BorderPane}
import javafx.{scene => jfxs}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.image.Image
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import whitejack.Util.ClientUtil

import java.net.URL

object GameClientApp extends JFXApp {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val config: Config = ConfigFactory.load()
  var mainSystem: actor.ActorSystem = akka.actor.ActorSystem("HelloSystem", Configuration.askClientConfig().withFallback(config))
  var greeterMain: ActorSystem[Nothing] = mainSystem.toTyped
  var cluster: Cluster = Cluster(greeterMain)
  var ownRef: ActorRef[GameClient.Command] = mainSystem.spawn(GameClient(), "ChatClient")

  def joinSeedNode(serverIP: String, serverPort: Int): Unit = {
    val address = akka.actor.Address("akka", "HelloSystem", serverIP, serverPort)
    cluster.manager ! JoinSeedNodes(List(address))
  }
  //joinSeedNode("25.25.65.242", 2222) //for hamachi connection
  joinSeedNode("127.0.0.1", 2222) //for same device connection

  val rootResource: URL = getClass.getResource("view/RootLayout.fxml")
  val loader = new FXMLLoader(rootResource, NoDependencyResolver)
  loader.load()
  val roots: BorderPane = loader.getRoot[jfxs.layout.BorderPane]
  stage = new PrimaryStage {
    title = "WhiteJack"
    icons += new Image(getClass.getResource("img/icon.jpg").toURI.toString)
    scene = new Scene {
      root = roots
    }
  }

  stage.setResizable(false)
  stage.onCloseRequest = handle( {mainSystem.terminate})
  MainMenu.load()
}

object MainMenu {
  val resource: URL = getClass.getResource("view/MainMenu.fxml")
  val loader = new FXMLLoader(resource, NoDependencyResolver)
  loader.load()
  val roots: AnchorPane = loader.getRoot[jfxs.layout.AnchorPane]
  var control = loader.getController[whitejack.Controller.MainMenuController#Controller]()
  GameClientApp.roots.setCenter(roots)

  def load(): Unit = {
    ClientUtil.ownRef = Option(GameClientApp.ownRef)
    GameClientApp.roots.setCenter(roots)
  }
}

object Lobby {
  val resource: URL = getClass.getResource("view/Lobby.fxml")
  val loader = new FXMLLoader(resource, NoDependencyResolver)
  loader.load()
  val roots: AnchorPane = loader.getRoot[jfxs.layout.AnchorPane]
  var control = loader.getController[whitejack.Controller.LobbyController#Controller]()

  def load(): Unit = {
    ClientUtil.ownRef = Option(GameClientApp.ownRef)
    GameClientApp.roots.setCenter(roots)
  }
}

object Board {
  val resource: URL = getClass.getResource("view/Board.fxml")
  val loader = new FXMLLoader(resource, NoDependencyResolver)
  loader.load()
  val roots: AnchorPane = loader.getRoot[jfxs.layout.AnchorPane]
  var control = loader.getController[whitejack.Controller.BoardController#Controller]()
  GameClientApp.roots.setCenter(roots)

  def load(): Unit = {
    val resource = getClass.getResource("view/Board.fxml")
    val loader = new FXMLLoader(resource, NoDependencyResolver)
    loader.load()
    val roots = loader.getRoot[javafx.scene.layout.AnchorPane]
    GameClientApp.roots.setCenter(roots)
    this.control = loader.getController[whitejack.Controller.BoardController#Controller]()
  }
}

object Instruction {
  val resource: URL = getClass.getResource("view/Instruction.fxml")
  val loader = new FXMLLoader(resource, NoDependencyResolver)
  loader.load()
  val roots: AnchorPane = loader.getRoot[jfxs.layout.AnchorPane]
  var control = loader.getController[whitejack.Controller.InstructionController#Controller]()
  GameClientApp.roots.setCenter(roots)

  def load(): Unit = {
    ClientUtil.ownRef = Option(GameClientApp.ownRef)
    GameClientApp.roots.setCenter(roots)
  }
}
