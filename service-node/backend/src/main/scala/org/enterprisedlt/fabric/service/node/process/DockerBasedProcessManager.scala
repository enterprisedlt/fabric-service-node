//package org.enterprisedlt.fabric.service.node.process
//
//import java.io.Closeable
//import java.nio.charset.StandardCharsets
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.atomic.AtomicReference
//
//import com.github.dockerjava.api.DockerClient
//import com.github.dockerjava.api.async.ResultCallback
//import com.github.dockerjava.api.command.DockerCmdExecFactory
//import com.github.dockerjava.api.exception.NotFoundException
//import com.github.dockerjava.api.model.LogConfig.LoggingType
//import com.github.dockerjava.api.model.Ports.Binding
//import com.github.dockerjava.api.model._
//import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
//import com.github.dockerjava.okhttp.OkHttpDockerCmdExecFactory
//import org.enterprisedlt.fabric.service.node.FabricProcessManager
//import org.enterprisedlt.fabric.service.node.configuration.{DockerConfig, NetworkConfig, OrganizationConfig}
//import org.slf4j.LoggerFactory
//
//import scala.collection.JavaConverters._
//
///**
// * @author Andrew Pudovikov
// */
//class DockerBasedProcessManager(
//    hostHomePath: String,
//    organizationConfig: OrganizationConfig,
//    networkName: String,
//    networkConfig: NetworkConfig,
//    processConfig: DockerConfig,
//    LogWindow: Int = 1500
//) extends FabricProcessManager {
//    private val logger = LoggerFactory.getLogger(this.getClass)
//    private val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
//    private val dockerConfig = new DefaultDockerClientConfig.Builder().withDockerHost(processConfig.dockerSocket).build
//    private val execFactory: DockerCmdExecFactory = new OkHttpDockerCmdExecFactory
//    private val docker: DockerClient = DockerClientImpl.getInstance(dockerConfig).withDockerCmdExecFactory(execFactory)
//    private val DefaultLabels =
//        Map(
//            "com.docker.compose.project" -> networkName,
//            "com.docker.compose.service" -> organizationFullName
//        ).asJava
//    private val logConfig = makeDockerLogConfig(processConfig)
//    // =================================================================================================================
//    logger.info(s"Initializing ${this.getClass.getSimpleName} ...")
//    val containerName = s"service.$organizationFullName"
//    logger.info(s"Checking network ...")
//    if (docker.listNetworksCmd().withNameFilter(networkName).exec().isEmpty) {
//        logger.info(s"Network $networkName does not exist, creating ...")
//        docker.createNetworkCmd()
//          .withName(networkName)
//          .withDriver("bridge")
//          .exec()
//    }
//    logger.info(s"Connecting myself ($containerName) to network $networkName ...")
//    docker.connectToNetworkCmd()
//      .withContainerId(containerName)
//      .withNetworkId(networkName)
//      .exec()
//    // =================================================================================================================
//
//
//    //=========================================================================
//    override def startOrderingNode(osnFullName: String): Either[String, String] = {
//        logger.info(s"Starting $osnFullName ...")
//        if (checkContainerExistence(osnFullName: String)) {
//            stopAndRemoveContainer(osnFullName: String)
//        }
//        networkConfig.orderingNodes
//          .find(_.name == osnFullName)
//          .toRight(s"There is no configuration for $osnFullName")
//          .map { osnConfig =>
//              val configHost = new HostConfig()
//                .withBinds(
//                    new Bind(s"$hostHomePath/hosts", new Volume("/etc/hosts")),
//                    new Bind(s"$hostHomePath/artifacts/genesis.block", new Volume("/var/hyperledger/orderer/orderer.genesis.block")),
//                    new Bind(s"$hostHomePath/crypto/orderers/$osnFullName/msp", new Volume("/var/hyperledger/orderer/msp")),
//                    new Bind(s"$hostHomePath/crypto/orderers/$osnFullName/tls", new Volume("/var/hyperledger/orderer/tls"))
//                )
//                .withPortBindings(
//                    new PortBinding(new Binding("0.0.0.0", osnConfig.port.toString), new ExposedPort(osnConfig.port, InternetProtocol.TCP))
//                )
//                .withNetworkMode(networkName)
//                .withLogConfig(logConfig)
//
//              val osnContainerId: String = docker.createContainerCmd("hyperledger/fabric-orderer")
//                .withName(osnFullName)
//                .withEnv(
//                    s"FABRIC_LOGGING_SPEC=${processConfig.fabricComponentsLogLevel}",
//                    "ORDERER_GENERAL_LISTENADDRESS=0.0.0.0",
//                    s"ORDERER_GENERAL_LISTENPORT=${osnConfig.port}",
//                    "ORDERER_GENERAL_GENESISMETHOD=file",
//                    "ORDERER_GENERAL_GENESISFILE=/var/hyperledger/orderer/orderer.genesis.block",
//                    s"ORDERER_GENERAL_LOCALMSPID=${organizationConfig.name}",
//                    "ORDERER_GENERAL_LOCALMSPDIR=/var/hyperledger/orderer/msp",
//                    "ORDERER_GENERAL_TLS_ENABLED=true",
//                    "ORDERER_GENERAL_TLS_PRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
//                    "ORDERER_GENERAL_TLS_CERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
//                    "ORDERER_GENERAL_TLS_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]",
//                    "ORDERER_GENERAL_CLUSTER_CLIENTCERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
//                    "ORDERER_GENERAL_CLUSTER_CLIENTPRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
//                    "ORDERER_GENERAL_CLUSTER_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]"
//                )
//                .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric")
//                .withCmd("orderer")
//                .withExposedPorts(new ExposedPort(osnConfig.port, InternetProtocol.TCP))
//                .withHostConfig(configHost)
//                .withLabels(DefaultLabels)
//                .exec().getId
//              docker.startContainerCmd(osnContainerId).exec
//              logger.info(s"OSN $osnFullName started, ID: $osnContainerId")
//              osnContainerId
//          }
//    }
//
//    //=============================================================================
//    override def startPeerNode(peerFullName: String): Either[String, String] = {
//        //        val peerFullName = s"$name.$organizationFullName"
//        logger.info(s"Starting $peerFullName ...")
//        if (checkContainerExistence(peerFullName: String)) {
//            stopAndRemoveContainer(peerFullName: String)
//        }
//        networkConfig.peerNodes
//          .find(_.name == peerFullName)
//          .toRight(s"There is no configuration for $peerFullName")
//          .map { peerConfig =>
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
//
//              val configHost = new HostConfig()
//                .withBinds(
//                    new Bind(s"$hostHomePath/hosts", new Volume("/etc/hosts")),
//                    new Bind(s"$hostHomePath/crypto/peers/$peerFullName/msp", new Volume("/etc/hyperledger/fabric/msp")),
//                    new Bind(s"$hostHomePath/crypto/peers/$peerFullName/tls", new Volume("/etc/hyperledger/fabric/tls")),
//                    new Bind(s"/var/run", new Volume("/host/var/run/"))
//                )
//                .withPortBindings(
//                    new PortBinding(new Binding("0.0.0.0", peerConfig.port.toString), new ExposedPort(peerConfig.port, InternetProtocol.TCP))
//                )
//                .withNetworkMode(networkName)
//                .withLogConfig(logConfig)
//
//              val peerContainerId: String = docker.createContainerCmd("hyperledger/fabric-peer")
//                .withName(peerFullName)
//                .withEnv(
//                    List("CORE_VM_ENDPOINT=unix:///host/var/run/docker.sock",
//                        s"CORE_VM_DOCKER_HOSTCONFIG_NETWORKMODE=$networkName",
//                        s"FABRIC_LOGGING_SPEC=${processConfig.fabricComponentsLogLevel}",
//                        "CORE_PEER_TLS_ENABLED=true",
//                        "CORE_PEER_GOSSIP_USELEADERELECTION=true",
//                        "CORE_PEER_GOSSIP_ORGLEADER=false",
//                        "CORE_PEER_PROFILE_ENABLED=false",
//                        "CORE_PEER_TLS_CERT_FILE=/etc/hyperledger/fabric/tls/server.crt",
//                        "CORE_PEER_TLS_KEY_FILE=/etc/hyperledger/fabric/tls/server.key",
//                        "CORE_PEER_TLS_ROOTCERT_FILE=/etc/hyperledger/fabric/tls/ca.crt",
//                        s"CORE_PEER_ID=$peerFullName",
//                        s"CORE_PEER_ADDRESS=$peerFullName:${peerConfig.port}",
//                        s"CORE_PEER_LISTENADDRESS=0.0.0.0:${peerConfig.port}",
//                        "CORE_CHAINCODE_JAVA_RUNTIME=enterprisedlt/fabric-jar-env",
//                        s"CORE_PEER_GOSSIP_BOOTSTRAP=$peerFullName:${peerConfig.port}",
//                        s"CORE_PEER_GOSSIP_EXTERNALENDPOINT=$peerFullName:${peerConfig.port}",
//                        s"CORE_PEER_LOCALMSPID=${organizationConfig.name}"
//                    ) ++ couchEnv: _*
//                )
//                .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric/peer")
//                .withCmd("peer", "node", "start")
//                .withExposedPorts(new ExposedPort(peerConfig.port, InternetProtocol.TCP))
//                .withHostConfig(configHost)
//                .withLabels(DefaultLabels)
//                .exec().getId
//              docker.startContainerCmd(peerContainerId).exec
//
//              awaitPeerStarted(peerFullName)
//
//              logger.info(s"Peer $peerFullName started, ID $peerContainerId")
//              peerContainerId
//          }
//    }
//
//    //=============================================================================
//    private def startCouchDB(couchDBFullName: String, port: Int): String = {
//        logger.info(s"Starting $couchDBFullName ...")
//        if (checkContainerExistence(couchDBFullName)) {
//            stopAndRemoveContainer(couchDBFullName)
//        }
//        val configHost = new HostConfig()
//          .withPortBindings(
//              new PortBinding(new Binding("0.0.0.0", port.toString), new ExposedPort(5984, InternetProtocol.TCP))
//          )
//          .withNetworkMode(networkName)
//          .withLogConfig(logConfig)
//
//        val couchDBContainerId: String = docker.createContainerCmd("hyperledger/fabric-couchdb")
//          .withName(couchDBFullName)
//          .withEnv(
//              "COUCHDB_USER=",
//              "COUCHDB_PASSWORD="
//          )
//          .withExposedPorts(new ExposedPort(5984, InternetProtocol.TCP))
//          .withHostConfig(configHost)
//          .withLabels(DefaultLabels)
//          .exec().getId
//        docker.startContainerCmd(couchDBContainerId).exec
//        logger.info(s"CouchDB $couchDBFullName started, ID $couchDBContainerId")
//        couchDBContainerId
//    }
//
//    override def osnAwaitJoinedToRaft(osnFullName: String): Unit = {
//        logger.info(s"Awaiting for $osnFullName to join RAFT cluster ...")
//        val findResult = docker.logContainerCmd(osnFullName)
//          .withStdOut(true)
//          .withStdErr(true)
//          .withTail(LogWindow)
//          .withFollowStream(true)
//          .exec(new FindInLog("Raft leader changed"))
//        findResult.get() match {
//            case Some(logLine) =>
//                logger.info(s"$osnFullName:\n$logLine")
//            case _ =>
//                val msg = s"$osnFullName is failed to join RAFT cluster"
//                logger.error(msg)
//                throw new Exception(msg)
//        }
//    }
//
//    override def osnAwaitJoinedToChannel(osnFullName: String, channelName: String): Unit = {
//        (for {
//            findResult <- docker.logContainerCmd(osnFullName)
//              .withStdOut(true)
//              .withStdErr(true)
//              .withTail(LogWindow)
//              .withFollowStream(true)
//              .exec(new FindInLog(s"Starting raft node to join an existing channel channel=$channelName"))
//              .get()
//
//            _ <- Option(logger.info(s"RAFT OSN node started on $channelName:\n$findResult"))
//
//            nodeId <- Option(findResult.split("=")).filter(_.length == 3).map(_ (2).trim)
//
//            _ <- Option(logger.info(s"Got OSN node id for channel $channelName: $nodeId"))
//
//            logLine <- docker.logContainerCmd(osnFullName)
//              .withStdOut(true)
//              .withStdErr(true)
//              .withTail(LogWindow)
//              .withFollowStream(true)
//              .exec(new FindInLog(s"Applied config change to add node $nodeId", s"channel=$channelName"))
//              .get()
//
//        } yield {
//            logger.info(s"$osnFullName:\n$logLine")
//        }).getOrElse {
//            val msg = s"$osnFullName is failed to on-board to channel $channelName"
//            logger.error(msg)
//            throw new Exception(msg)
//        }
//    }
//
//    override def peerAwaitForBlock(peerFullName: String, blockNumber: Long): Unit = {
//        logger.info(s"Awaiting for block $blockNumber on peer $peerFullName ...")
//        val findResult = docker.logContainerCmd(peerFullName)
//          .withStdOut(true)
//          .withStdErr(true)
//          .withTail(LogWindow)
//          .withFollowStream(true)
//          .exec(new FindInLog(s"Committed block [$blockNumber]"))
//        findResult.get() match {
//            case Some(logLine) =>
//                logger.info(s"$peerFullName:\n$logLine")
//            case _ =>
//                val msg = s"Didn't got block $blockNumber on $peerFullName"
//                logger.error(msg)
//                throw new Exception(msg)
//        }
//    }
//
//    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Unit = {
//        val containerName = s"dev-$peerName-$chainCodeName-$chainCodeVersion"
//        stopAndRemoveContainer(containerName)
//    }
//
//
//    // =================================================================================================================
//    private def makeDockerLogConfig(processConfig: DockerConfig): LogConfig = {
//        val jsonLogConfig = Map(
//            "max-size" -> processConfig.logFileSize,
//            "max-file" -> processConfig.logMaxFiles
//        ).asJava
//        new LogConfig(LoggingType.JSON_FILE, jsonLogConfig)
//    }
//
//    // =================================================================================================================
//    private def checkContainerExistence(name: String): Boolean = {
//        try {
//            docker.inspectContainerCmd(name).exec()
//            true
//        } catch {
//            case e: NotFoundException => false
//            case other: Throwable => throw other
//        }
//    }
//
//    // =================================================================================================================
//    private def stopAndRemoveContainer(name: String): Unit = {
//        docker
//          .stopContainerCmd(name)
//          .exec()
//
//        docker
//          .removeContainerCmd(name)
//          .withForce(true)
//          .exec()
//    }
//
//    // =================================================================================================================
//    private def awaitPeerStarted(peerFullName: String): Unit = {
//        logger.info(s"Awaiting for $peerFullName to start ...")
//        val findResult = docker.logContainerCmd(peerFullName)
//          .withStdOut(true)
//          .withStdErr(true)
//          .withTail(LogWindow)
//          .withFollowStream(true)
//          .exec(new FindInLog("Started peer"))
//        findResult.get() match {
//            case Some(logLine) =>
//                logger.info(s"$peerFullName:\n$logLine")
//            case _ =>
//                val msg = s"$peerFullName is failed to start"
//                logger.error(msg)
//                throw new Exception(msg)
//        }
//    }
//
//    // =================================================================================================================
//    private class FindInLog(
//        toFind: String*
//    ) extends CompletableFuture[Option[String]] with ResultCallback[Frame] {
//        private val logger = LoggerFactory.getLogger(s"FindInLog")
//        private var taskControl = new AtomicReference[Closeable]()
//
//        override def onStart(taskControl: Closeable): Unit = {
//            if (!this.taskControl.compareAndSet(null, taskControl)) {
//                val ex = new IllegalStateException("Started more then once")
//                logger.error("Unexpected call to 'OnStart':", ex)
//                throw ex
//            }
//        }
//
//        override def onNext(frame: Frame): Unit = {
//            val logLine = new String(frame.getPayload, StandardCharsets.UTF_8)
//            if (toFind.forall(logLine.contains)) {
//                this.complete(Option(logLine))
//                Option(this.taskControl.get())
//                  .getOrElse {
//                      val ex = new IllegalStateException("task control is missing")
//                      logger.error("Unexpected state in 'OnNext:", ex)
//                      throw ex
//                  }
//                  .close() // stop execution of log command
//            }
//        }
//
//        override def onError(exception: Throwable): Unit = {
//            this.completeExceptionally(exception)
//        }
//
//        override def onComplete(): Unit = {
//            this.complete(None)
//        }
//
//        override def close(): Unit = {
//            /* NO-OP */
//        }
//    }
//
//}
