package org.enterprisedlt.fabric.service.node.process

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Ports.Binding
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import org.enterprisedlt.fabric.service.node.IFabricProcessManager
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  * @author pandelie
  */
class DockerBasedProcessManager(
    hostHomePath: String,
    dockerSocket: String,
    config: ServiceConfig
) extends IFabricProcessManager {
    private val logger = LoggerFactory.getLogger(getClass)
    private val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
    private val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder.withDockerHost(dockerSocket).build
    private val docker: DockerClient = DockerClientBuilder.getInstance(dockerConfig).build()

    private def checkContainerExistence(name: String): Boolean = {
        try {
            docker.inspectContainerCmd(name).exec()
            true
        } catch {
            case e: NotFoundException => false
            case other: Throwable => throw other
        }
    }

    private def stopAndRemoveContainer(name: String): Boolean = {
        try {
            docker.removeContainerCmd(name).withForce(true).exec()
            true
        } catch {
            case e: NotFoundException => false
            case other: Throwable => throw other
        }
    }

    def checkNetworkExistence(name: String): Boolean = {
        try {
            docker.inspectNetworkCmd().withNetworkId(name).exec()
            true
        } catch {
            case e: NotFoundException => false
            case other: Throwable => throw other
        }
    }

    def createNetwork(name: String): Unit = {
        docker.createNetworkCmd()
          .withName(name)
          .withDriver("bridge")
          .exec()
    }

    //=========================================================================
    override def startOrderingNode(name: String, port: Int): String = {
        val osnFullName = s"$name.$organizationFullName"
        logger.info(s"Starting $osnFullName...")
        if (checkContainerExistence(osnFullName: String)) {
            stopAndRemoveContainer(osnFullName: String)
        }
        val configHost = new HostConfig()
          .withBinds(
              new Bind(s"$hostHomePath/hosts", new Volume("/etc/hosts")),
              new Bind(s"$hostHomePath/artifacts/genesis.block", new Volume("/var/hyperledger/orderer/orderer.genesis.block")),
              new Bind(s"$hostHomePath/crypto/ordererOrganizations/$organizationFullName/orderers/$name.$organizationFullName/msp", new Volume("/var/hyperledger/orderer/msp")),
              new Bind(s"$hostHomePath/crypto/ordererOrganizations/$organizationFullName/orderers/$name.$organizationFullName/tls", new Volume("/var/hyperledger/orderer/tls"))
          )
          .withPortBindings(
              new PortBinding(new Binding("0.0.0.0", port.toString), new ExposedPort(port, InternetProtocol.TCP))
          )
          .withNetworkMode(config.network.name)

        val osnContainerId: String = docker.createContainerCmd("hyperledger/fabric-orderer")
          .withName(osnFullName)
          .withEnv(
              "FABRIC_LOGGING_SPEC=DEBUG",
              "ORDERER_GENERAL_LISTENADDRESS=0.0.0.0",
              s"ORDERER_GENERAL_LISTENPORT=$port",
              "ORDERER_GENERAL_GENESISMETHOD=file",
              "ORDERER_GENERAL_GENESISFILE=/var/hyperledger/orderer/orderer.genesis.block",
              s"ORDERER_GENERAL_LOCALMSPID=osn-${config.organization.name}",
              "ORDERER_GENERAL_LOCALMSPDIR=/var/hyperledger/orderer/msp",
              "ORDERER_GENERAL_TLS_ENABLED=true",
              "ORDERER_GENERAL_TLS_PRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
              "ORDERER_GENERAL_TLS_CERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
              "ORDERER_GENERAL_TLS_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]",
              "ORDERER_GENERAL_CLUSTER_CLIENTCERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
              "ORDERER_GENERAL_CLUSTER_CLIENTPRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
              "ORDERER_GENERAL_CLUSTER_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]"
              //              "GODEBUG=netdns=go"
          )
          .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric")
          .withCmd("orderer")
          .withExposedPorts(new ExposedPort(port, InternetProtocol.TCP))
          .withHostConfig(configHost)
          .exec().getId
        docker.startContainerCmd(osnContainerId).exec
        logger.info(s"OSN container started, ID: $osnContainerId")
        osnContainerId
    }

    //=============================================================================
    override def startPeerNode(name: String, port: Int): String = {
        val peerFullName = s"$name.$organizationFullName"
        logger.info(s"Starting $peerFullName...")
        if (checkContainerExistence(peerFullName: String)) {
            stopAndRemoveContainer(peerFullName: String)
        }
        val configHost = new HostConfig()
          .withBinds(
              new Bind(s"$hostHomePath/hosts", new Volume("/etc/hosts")),
              new Bind(s"$hostHomePath/crypto/peerOrganizations/$organizationFullName/peers/$peerFullName/msp", new Volume("/etc/hyperledger/fabric/msp")),
              new Bind(s"$hostHomePath/crypto/peerOrganizations/$organizationFullName/peers/$peerFullName/tls", new Volume("/etc/hyperledger/fabric/tls")),
              new Bind(s"/var/run", new Volume("/host/var/run/"))
          )
          .withPortBindings(
              new PortBinding(new Binding("0.0.0.0", port.toString), new ExposedPort(port, InternetProtocol.TCP))
          )
          .withNetworkMode(config.network.name)

        val peerContainerId: String = docker.createContainerCmd("hyperledger/fabric-peer")
          .withName(peerFullName)
          .withEnv(List(
              "CORE_VM_ENDPOINT=unix:///host/var/run/docker.sock",
              "CORE_VM_DOCKER_HOSTCONFIG_NETWORKMODE=testnet",
              "FABRIC_LOGGING_SPEC=DEBUG",
              "CORE_PEER_TLS_ENABLED=true",
              "CORE_PEER_GOSSIP_USELEADERELECTION=true",
              "CORE_PEER_GOSSIP_ORGLEADER=false",
              "CORE_PEER_PROFILE_ENABLED=false",
              "CORE_PEER_TLS_CERT_FILE=/etc/hyperledger/fabric/tls/server.crt",
              "CORE_PEER_TLS_KEY_FILE=/etc/hyperledger/fabric/tls/server.key",
              "CORE_PEER_TLS_ROOTCERT_FILE=/etc/hyperledger/fabric/tls/ca.crt",
              s"CORE_PEER_ID=$peerFullName",
              s"CORE_PEER_ADDRESS=0.0.0.0:$port",
              s"CORE_PEER_LISTENADDRESS=0.0.0.0:$port",
              "CORE_CHAINCODE_JAVA_RUNTIME=apolubelov/fabric-scalaenv",
              s"CORE_PEER_GOSSIP_BOOTSTRAP=$peerFullName:$port",
              s"CORE_PEER_GOSSIP_EXTERNALENDPOINT=$peerFullName:$port",
              s"CORE_PEER_LOCALMSPID=${config.organization.name}"
//              "CORE_LEDGER_STATE_STATEDATABASE=CouchDB",
//              s"CORE_LEDGER_STATE_COUCHDBCONFIG_COUCHDBADDRESS=couchdb0.${organizationFullName}:5984",
//              "CORE_LEDGER_STATE_COUCHDBCONFIG_USERNAME=",
//              "CORE_LEDGER_STATE_COUCHDBCONFIG_PASSWORD=",
//              "GODEBUG=netdns=go"
          ).asJava)
          .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric/peer")
          .withCmd("peer", "node", "start")
          .withExposedPorts(new ExposedPort(port, InternetProtocol.TCP))
          .withHostConfig(configHost)
          .exec().getId
        docker.startContainerCmd(peerContainerId).exec
        logger.info(s"Peer container started, ID $peerContainerId")
        peerContainerId
    }

    //=============================================================================
    def couchDBStart(ORG: String, DOMAIN: String, COUCHDB_NAME: String, COUCHDB_PORT: Int): String = {
        logger.info(s"Request for ${COUCHDB_NAME} start.")
        val couchDBName = s"${COUCHDB_NAME}.${ORG}.${DOMAIN}"
        if (checkContainerExistence(couchDBName: String)) {
            stopAndRemoveContainer(couchDBName: String)
        }
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder
          .withDockerHost("unix:///host/var/run/docker.sock")
          .build()
        val docker = DockerClientBuilder.getInstance(config).build()
        val configHost = new HostConfig()
          .withPortBindings(
              new PortBinding(new Binding("0.0.0.0", s"${COUCHDB_PORT}"), new ExposedPort(COUCHDB_PORT, InternetProtocol.TCP))
          )
          .withNetworkMode("testnet")
        val couchDBContainerId: String = docker.createContainerCmd("hyperledger/fabric-couchdb")
          .withName(couchDBName)
          .withEnv(
              "COUCHDB_USER=",
              "COUCHDB_PASSWORD="
          )
          .withExposedPorts(new ExposedPort(COUCHDB_PORT, InternetProtocol.TCP))
          .withHostConfig(configHost)
          .exec().getId
        docker.startContainerCmd(couchDBContainerId).exec
        logger.info(s"CouchDB container started with ID ${couchDBContainerId}")
        logger.info("====================================================")
        couchDBContainerId
    }

    override def awaitOrderingJoinedRaft(name: String): Unit = {
        //TODO: implement
    }
}