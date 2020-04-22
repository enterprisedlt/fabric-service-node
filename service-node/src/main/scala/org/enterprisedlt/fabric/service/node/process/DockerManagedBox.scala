package org.enterprisedlt.fabric.service.node.process

import java.io.{Closeable, File}
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.LogConfig.LoggingType
import com.github.dockerjava.api.model.Ports.Binding
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
import com.github.dockerjava.okhttp.OkHttpDockerCmdExecFactory
import org.enterprisedlt.fabric.service.node.Util
import org.enterprisedlt.fabric.service.node.configuration.DockerConfig
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * @author Andrew Pudovikov
 * @author Alexey Polubelov
 */
// DockerBoxManager
// DockerManagedBox
class DockerManagedBox(
    hostPath: String,
    containerName: String,
    networkName: String,
    processConfig: DockerConfig,
    LogWindow: Int = 1500
) extends ManagedBox {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val InnerPath = "/opt/profile/components"
    //    private val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
    private val dockerConfig = new DefaultDockerClientConfig.Builder().withDockerHost(processConfig.dockerSocket).build
    private val execFactory: DockerCmdExecFactory = new OkHttpDockerCmdExecFactory
    private val docker: DockerClient = DockerClientImpl.getInstance(dockerConfig).withDockerCmdExecFactory(execFactory)
    //    private val DefaultLabels =
    //        Map(
    //            "com.docker.compose.project" -> networkName,
    //            "com.docker.compose.service" -> organizationFullName
    //        ).asJava
    private val logConfig = makeDockerLogConfig(processConfig)
    // =================================================================================================================
    logger.info(s"Initializing ${this.getClass.getSimpleName} ...")
    //    val containerName = s"service.$organizationFullName"
    logger.info(s"Checking network ...")
    if (docker.listNetworksCmd().withNameFilter(networkName).exec().isEmpty) {
        logger.info(s"Network $networkName does not exist, creating ...")
        docker.createNetworkCmd()
          .withName(networkName)
          .withDriver("bridge")
          .exec()
    }
    logger.info(s"Connecting myself [$containerName] to network $networkName ...")
    docker.connectToNetworkCmd()
      .withContainerId(containerName)
      .withNetworkId(networkName)
      .exec()

    Util.mkDirs(InnerPath) // ensure inner path exists
    private val hostsFile = new File(s"$InnerPath/hosts")
    if (!hostsFile.exists()) {
        hostsFile.createNewFile()
    }
    // =================================================================================================================


    //=========================================================================
    override def startOrderingNode(request: StartOSNRequest): Either[String, String] = {
        logger.info(s"Starting ${request.component.fullName} ...")
        //        if (checkContainerExistence(osnFullName: String)) {
        //            stopAndRemoveContainer(osnFullName: String)
        //        }
        val hostComponentPath = s"$hostPath/${request.component.fullName}"
        val innerComponentPath = s"$InnerPath/${request.component.fullName}"
        Try {
            // create required directory structure, dump certificates/keys
            Util.mkDirs(innerComponentPath)
            Util.mkDirs(s"$innerComponentPath/data")
            Util.mkDirs(s"$innerComponentPath/data/ledger")
            Util.writeBase64BinaryFile(s"$innerComponentPath/data/genesis.block", request.genesis)
            storeComponentCryptoMaterial(innerComponentPath, request.organization, request.component)
            // start container
            val configHost = new HostConfig()
              .withBinds(
                  new Bind(s"$hostPath/hosts", new Volume("/etc/hosts")),
                  new Bind(s"$hostComponentPath/data/genesis.block", new Volume("/var/hyperledger/orderer/orderer.genesis.block")),
                  new Bind(s"$hostComponentPath/msp", new Volume("/var/hyperledger/orderer/msp")),
                  new Bind(s"$hostComponentPath/tls", new Volume("/var/hyperledger/orderer/tls")),
                  new Bind(s"$hostComponentPath/data/ledger", new Volume("/var/hyperledger/production/orderer"))
              )
              .withPortBindings(
                  new PortBinding(new Binding("0.0.0.0", request.port.toString), new ExposedPort(request.port, InternetProtocol.TCP))
              )
              .withNetworkMode(networkName)
              .withLogConfig(logConfig)

            val osnContainerId: String = docker.createContainerCmd("hyperledger/fabric-orderer")
              .withName(request.component.fullName)
              .withEnv(
                  s"FABRIC_LOGGING_SPEC=${processConfig.fabricComponentsLogLevel}",
                  "ORDERER_GENERAL_LISTENADDRESS=0.0.0.0",
                  s"ORDERER_GENERAL_LISTENPORT=${request.port}",
                  "ORDERER_GENERAL_GENESISMETHOD=file",
                  "ORDERER_GENERAL_GENESISFILE=/var/hyperledger/orderer/orderer.genesis.block",
                  s"ORDERER_GENERAL_LOCALMSPID=${request.organization.mspId}",
                  "ORDERER_GENERAL_LOCALMSPDIR=/var/hyperledger/orderer/msp",
                  "ORDERER_GENERAL_TLS_ENABLED=true",
                  "ORDERER_GENERAL_TLS_PRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
                  "ORDERER_GENERAL_TLS_CERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
                  "ORDERER_GENERAL_TLS_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]",
                  "ORDERER_GENERAL_CLUSTER_CLIENTCERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
                  "ORDERER_GENERAL_CLUSTER_CLIENTPRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
                  "ORDERER_GENERAL_CLUSTER_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]"
              )
              .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric")
              .withCmd("orderer")
              .withExposedPorts(new ExposedPort(request.port, InternetProtocol.TCP))
              .withHostConfig(configHost)
              //              .withLabels(DefaultLabels)
              .exec().getId
            docker.startContainerCmd(osnContainerId).exec
            logger.info(s"OSN ${request.component.fullName} started, ID: $osnContainerId")
            osnContainerId
        }.toEither.left.map(_.getMessage)
    }

    //=============================================================================
    override def startPeerNode(request: StartPeerRequest): Either[String, String] = {
        val peerFullName = request.component.fullName
        logger.info(s"Starting $peerFullName ...")
        //        if (checkContainerExistence(peerFullName: String)) {
        //            stopAndRemoveContainer(peerFullName: String)
        //        }
        //              val couchEnv = Option(peerConfig.couchDB)
        //                .map { couchDBConfig =>
        //                    val couchDBName = s"couchdb.$peerFullName"
        //                    this.startCouchDB(couchDBName, couchDBConfig.port)
        //                    List("CORE_LEDGER_STATE_STATEDATABASE=CouchDB",
        //                        s"CORE_LEDGER_STATE_COUCHDBCONFIG_COUCHDBADDRESS=$couchDBName:5984",
        //                        s"CORE_LEDGER_STATE_COUCHDBCONFIG_USERNAME=",
        //                        s"CORE_LEDGER_STATE_COUCHDBCONFIG_PASSWORD=")
        //                }
        //                .getOrElse(List.empty)
        val hostComponentPath = s"$hostPath/${request.component.fullName}"
        val innerComponentPath = s"$InnerPath/${request.component.fullName}"
        Try {
            // create required directory structure, dump certificates/keys
            Util.mkDirs(innerComponentPath)
            Util.mkDirs(s"$innerComponentPath/data")
            Util.mkDirs(s"$innerComponentPath/data/ledger")
            storeComponentCryptoMaterial(innerComponentPath, request.organization, request.component)
            // start container
            val configHost = new HostConfig()
              .withBinds(
                  new Bind(s"$hostPath/hosts", new Volume("/etc/hosts")),
                  new Bind(s"$hostComponentPath/msp", new Volume("/etc/hyperledger/fabric/msp")),
                  new Bind(s"$hostComponentPath/tls", new Volume("/etc/hyperledger/fabric/tls")),
                  new Bind(s"$hostComponentPath/data/ledger", new Volume("/var/hyperledger/production")),
                  new Bind(s"/var/run", new Volume("/host/var/run/"))
              )
              .withPortBindings(
                  new PortBinding(new Binding("0.0.0.0", request.port.toString), new ExposedPort(request.port, InternetProtocol.TCP))
              )
              .withNetworkMode(networkName)
              .withLogConfig(logConfig)

            val peerContainerId: String = docker.createContainerCmd("hyperledger/fabric-peer")
              .withName(peerFullName)
              .withEnv(
                  List(
                      "CORE_VM_ENDPOINT=unix:///host/var/run/docker.sock",
                      s"CORE_VM_DOCKER_HOSTCONFIG_NETWORKMODE=$networkName",
                      s"FABRIC_LOGGING_SPEC=${processConfig.fabricComponentsLogLevel}",
                      "CORE_PEER_TLS_ENABLED=true",
                      "CORE_PEER_GOSSIP_USELEADERELECTION=true",
                      "CORE_PEER_GOSSIP_ORGLEADER=false",
                      "CORE_PEER_PROFILE_ENABLED=false",
                      "CORE_PEER_TLS_CERT_FILE=/etc/hyperledger/fabric/tls/server.crt",
                      "CORE_PEER_TLS_KEY_FILE=/etc/hyperledger/fabric/tls/server.key",
                      "CORE_PEER_TLS_ROOTCERT_FILE=/etc/hyperledger/fabric/tls/ca.crt",
                      s"CORE_PEER_ID=$peerFullName",
                      s"CORE_PEER_ADDRESS=$peerFullName:${request.port}",
                      s"CORE_PEER_LISTENADDRESS=0.0.0.0:${request.port}",
                      "CORE_CHAINCODE_JAVA_RUNTIME=enterprisedlt/fabric-jar-env",
                      s"CORE_PEER_GOSSIP_BOOTSTRAP=$peerFullName:${request.port}",
                      s"CORE_PEER_GOSSIP_EXTERNALENDPOINT=$peerFullName:${request.port}",
                      s"CORE_PEER_LOCALMSPID=${request.organization.mspId}"
                  ): _* // ++ couchEnv
              )
              .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric/peer")
              .withCmd("peer", "node", "start")
              .withExposedPorts(new ExposedPort(request.port, InternetProtocol.TCP))
              .withHostConfig(configHost)
              //                .withLabels(DefaultLabels)
              .exec().getId
            docker.startContainerCmd(peerContainerId).exec

            awaitPeerStarted(peerFullName)

            logger.info(s"Peer $peerFullName started, ID $peerContainerId")
            peerContainerId
        }.toEither.left.map(_.getMessage)
    }

    //=============================================================================
    private def startCouchDB(couchDBFullName: String, port: Int): String = {
        logger.info(s"Starting $couchDBFullName ...")
        if (checkContainerExistence(couchDBFullName)) {
            stopAndRemoveContainer(couchDBFullName)
        }
        val configHost = new HostConfig()
          .withPortBindings(
              new PortBinding(new Binding("0.0.0.0", port.toString), new ExposedPort(5984, InternetProtocol.TCP))
          )
          .withNetworkMode(networkName)
          .withLogConfig(logConfig)

        val couchDBContainerId: String = docker.createContainerCmd("hyperledger/fabric-couchdb")
          .withName(couchDBFullName)
          .withEnv(
              "COUCHDB_USER=",
              "COUCHDB_PASSWORD="
          )
          .withExposedPorts(new ExposedPort(5984, InternetProtocol.TCP))
          .withHostConfig(configHost)
          //          .withLabels(DefaultLabels)
          .exec().getId
        docker.startContainerCmd(couchDBContainerId).exec
        logger.info(s"CouchDB $couchDBFullName started, ID $couchDBContainerId")
        couchDBContainerId
    }

    override def osnAwaitJoinedToRaft(osnFullName: String): Either[String, Unit] = {
        logger.info(s"Awaiting for $osnFullName to join RAFT cluster ...")
        val findResult =
            Try {
                docker.logContainerCmd(osnFullName)
                  .withStdOut(true)
                  .withStdErr(true)
                  .withTail(LogWindow)
                  .withFollowStream(true)
                  .exec(new FindInLog("Raft leader changed"))
                  .get() // todo: add timeout
            }
        findResult
          .toEither
          .left.map { ex =>
            val msg = s"$osnFullName is failed to join RAFT cluster: ${ex.getMessage}"
            logger.error(msg, ex)
            msg
        }
          .flatMap(_.toRight("Failed to find pattern in log"))
          .map { logLine =>
              logger.info(s"$osnFullName joined RAFT:\n$logLine")
          }
    }

    override def osnAwaitJoinedToChannel(osnFullName: String, channelName: String): Either[String, Unit] = {
        for {
            findResult <- Try {
                docker.logContainerCmd(osnFullName)
                  .withStdOut(true)
                  .withStdErr(true)
                  .withTail(LogWindow)
                  .withFollowStream(true)
                  .exec(new FindInLog(s"Starting raft node to join an existing channel channel=$channelName"))
                  .get() // todo: add timeout
            }.toEither.left.map { ex =>
                val msg = s"$osnFullName failed to on-board to channel $channelName: ${ex.getMessage}"
                logger.error(msg)
                msg
            }

            _ = logger.info(s"RAFT OSN node started on $channelName:\n$findResult")

            nodeId <- findResult.map(_.split("=")).filter(_.length == 3).map(_ (2).trim).toRight {
                val msg = s"$osnFullName failed to on-board to channel $channelName:\nFailed to resolve nodeId from: $findResult"
                logger.error(msg)
                msg
            }

            _ = logger.info(s"Got OSN node id for channel $channelName: $nodeId")

            logLine <- Try {
                docker.logContainerCmd(osnFullName)
                  .withStdOut(true)
                  .withStdErr(true)
                  .withTail(LogWindow)
                  .withFollowStream(true)
                  .exec(new FindInLog(s"Applied config change to add node $nodeId", s"channel=$channelName"))
                  .get() // todo: add timeout
            }.toEither.left.map { ex =>
                val msg = s"$osnFullName failed to on-board to channel $channelName: ${ex.getMessage}"
                logger.error(msg)
                msg
            }.flatMap(_.toRight("Failed to find pattern in log"))

        } yield {
            logger.info(s"$osnFullName joined to channel:\n$logLine")
        }
    }

    override def peerAwaitForBlock(peerFullName: String, blockNumber: Long): Either[String, Unit] = {
        logger.info(s"Awaiting for block $blockNumber on peer $peerFullName ...")
        val findResult = Try {
            docker.logContainerCmd(peerFullName)
              .withStdOut(true)
              .withStdErr(true)
              .withTail(LogWindow)
              .withFollowStream(true)
              .exec(new FindInLog(s"Committed block [$blockNumber]"))
              .get() // todo: add timeout
        }
        findResult
          .toEither
          .left.map { ex =>
            val msg = s"Failed to get block $blockNumber on $peerFullName: ${ex.getMessage}"
            logger.error(msg, ex)
            msg
        }
          .flatMap(_.toRight("Failed to find pattern in log"))
          .map { logLine =>
              logger.info(s"$peerFullName committed block:\n$logLine")
          }
    }

    override def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit] = {
        val containerName = s"dev-$peerName-$chainCodeName-$chainCodeVersion"
        Try(stopAndRemoveContainer(containerName)).toEither.left.map { ex =>
            val msg = s"Failed to terminate chain code container $containerName: ${ex.getMessage}"
            logger.error(msg, ex)
            msg
        }
    }


    // =================================================================================================================
    private def makeDockerLogConfig(processConfig: DockerConfig): LogConfig = {
        val jsonLogConfig = Map(
            "max-size" -> processConfig.logFileSize,
            "max-file" -> processConfig.logMaxFiles
        ).asJava
        new LogConfig(LoggingType.JSON_FILE, jsonLogConfig)
    }

    // =================================================================================================================
    private def checkContainerExistence(name: String): Boolean = {
        try {
            docker.inspectContainerCmd(name).exec()
            true
        } catch {
            case e: NotFoundException => false
            case other: Throwable => throw other
        }
    }

    // =================================================================================================================
    private def stopAndRemoveContainer(name: String): Unit = {
        docker
          .stopContainerCmd(name)
          .exec()

        docker
          .removeContainerCmd(name)
          .withForce(true)
          .exec()
    }

    // =================================================================================================================
    private def awaitPeerStarted(peerFullName: String): Unit = {
        logger.info(s"Awaiting for $peerFullName to start ...")
        val findResult = docker.logContainerCmd(peerFullName)
          .withStdOut(true)
          .withStdErr(true)
          .withTail(LogWindow)
          .withFollowStream(true)
          .exec(new FindInLog("Started peer"))
        findResult.get() match {
            case Some(logLine) =>
                logger.info(s"$peerFullName:\n$logLine")
            case _ =>
                val msg = s"$peerFullName is failed to start"
                logger.error(msg)
                throw new Exception(msg)
        }
    }

    // =================================================================================================================
    private def storeComponentCryptoMaterial(outPath: String, orgConfig: OrganizationConfig, component: ComponentConfig): Unit = {
        //
        Util.mkDirs(s"$outPath/msp/admincerts")
        Util.writeTextFile(s"$outPath/msp/admincerts/Admin@${orgConfig.fullName}-cert.pem", orgConfig.cryptoMaterial.admin.certificate)

        Util.mkDirs(s"$outPath/msp/cacerts")
        Util.writeTextFile(s"$outPath/msp/cacerts/ca.${orgConfig.fullName}-cert.pem", orgConfig.cryptoMaterial.ca.certificate)

        Util.mkDirs(s"$outPath/msp/tlscacerts")
        Util.writeTextFile(s"$outPath/msp/tlscacerts/tlsca.${orgConfig.fullName}-cert.pem", orgConfig.cryptoMaterial.tlsca.certificate)
        //
        Util.mkDirs(s"$outPath/msp/keystore")
        Util.writeTextFile(s"$outPath/msp/keystore/${component.fullName}_sk", component.cryptoMaterial.msp.key)

        Util.mkDirs(s"$outPath/msp/signcerts")
        Util.writeTextFile(s"$outPath/msp/signcerts/${component.fullName}-cert.pem", component.cryptoMaterial.msp.certificate)
        //
        Util.mkDirs(s"$outPath/tls")
        Util.writeTextFile(s"$outPath/tls/ca.crt", orgConfig.cryptoMaterial.tlsca.certificate)
        Util.writeTextFile(s"$outPath/tls/server.crt", component.cryptoMaterial.tls.certificate)
        Util.writeTextFile(s"$outPath/tls/server.key", component.cryptoMaterial.tls.key)
    }

    // =================================================================================================================
    private class FindInLog(
        toFind: String*
    ) extends CompletableFuture[Option[String]] with ResultCallback[Frame] {
        private val logger = LoggerFactory.getLogger(s"FindInLog")
        private var taskControl = new AtomicReference[Closeable]()

        override def onStart(taskControl: Closeable): Unit = {
            if (!this.taskControl.compareAndSet(null, taskControl)) {
                val ex = new IllegalStateException("Started more then once")
                logger.error("Unexpected call to 'OnStart':", ex)
                throw ex
            }
        }

        override def onNext(frame: Frame): Unit = {
            val logLine = new String(frame.getPayload, StandardCharsets.UTF_8)
            if (toFind.forall(logLine.contains)) {
                this.complete(Option(logLine))
                Option(this.taskControl.get())
                  .getOrElse {
                      val ex = new IllegalStateException("task control is missing")
                      logger.error("Unexpected state in 'OnNext:", ex)
                      throw ex
                  }
                  .close() // stop execution of log command
            }
        }

        override def onError(exception: Throwable): Unit = {
            this.completeExceptionally(exception)
        }

        override def onComplete(): Unit = {
            this.complete(None)
        }

        override def close(): Unit = {
            /* NO-OP */
        }
    }

}
