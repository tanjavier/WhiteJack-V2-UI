package whitejack

import com.typesafe.config.{Config, ConfigFactory}

import java.net.InetAddress

object Configuration {
  var localAddress: Option[InetAddress] = None
  var runLocalOnly: Option[Boolean] = None

  def askServerConfig(port: Int): Config = {
    //val ipRaw = Array(25, 25, 65, 242) //for hamachi connection
    val ipRaw = Array(127, 0, 0, 1) //for same device connection
    val inetAddress = InetAddress.getByAddress(ipRaw.map(x => x.toByte))
    localAddress = Some(inetAddress)
    runLocalOnly = Some(true)
    Configuration(localAddress.get.getHostAddress, "", "2222")
  }

  def askClientConfig(): Config = {
    //val ipRaw = Array(25, 25, 65, 242) //for hamachi connection
    val ipRaw = Array(127, 0, 0, 1) //for same device connection
    val inetAddress = InetAddress.getByAddress(ipRaw.map(x => x.toByte))
    localAddress = Some(inetAddress)
    runLocalOnly = Some(true)
    Configuration(localAddress.get.getHostAddress, "", "0")
  }

  def apply(extHostName: String, intHostName: String, port: String): Config = {
    ConfigFactory.parseString(
      s"""
         |akka {
         |  loglevel = "INFO" #INFO, DEBUG
         |  actor {
         |    # provider=remote is possible, but prefer cluster
         |    provider =  cluster
         |    allow-java-serialization=on
         |    serializers {
         |      jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
         |    }
         |    serialization-bindings {
         |      "whitejack.Protocol.JsonSerializable" = jackson-json
         |    }
         |  }
         |  remote {
         |    artery {
         |      transport = tcp # See Selecting a transport below
         |      canonical.hostname = "$extHostName"
         |      canonical.port = $port
         |      bind.hostname = "$intHostName" # internal (bind) hostname
         |      bind.port = $port              # internal (bind) port
         |
         |      #log-sent-messages = on
         |      #log-received-messages = on
         |    }
         |  }
         |  cluster {
         |    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
         |  }
         |
         |  discovery {
         |    loglevel = "OFF"
         |    method = akka-dns
         |  }
         |
         |  management {
         |    loglevel = "OFF"
         |    http {
         |      hostname = "$extHostName"
         |      port = 8558
         |      bind-hostname = "$intHostName"
         |      bind-port = 8558
         |    }
         |  }
         |}
         """.stripMargin)
  }
}