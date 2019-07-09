package org.enterprisedlt.fabric.service.node

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Ports.Binding
import com.github.dockerjava.api.model.{Bind, ExposedPort, HostConfig, InternetProtocol, PortBinding, Volume}
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

class FabricProcessManager(
  DOCKER_HOST_IP: String
) {
    //========================LOGGER===============================================
    private val logger = LoggerFactory.getLogger(getClass)
    //=============================================================================
    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder
      .withDockerHost(DOCKER_HOST_IP)
      .build
    private val docker: DockerClient = DockerClientBuilder.getInstance(config).build()

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

    //=============================================================================
    def ordererStart(PROFILE_PATH: String, ORG: String, DOMAIN: String, OSN_NAME: String, OSN_PORT: Int): String = {
        logger.info(s"Request for ${OSN_NAME} start.")
        val ordererName = s"${OSN_NAME}.${ORG}.${DOMAIN}"

        if (checkContainerExistence(ordererName: String)) {
            stopAndRemoveContainer(ordererName: String)
        }

        val config = DefaultDockerClientConfig.createDefaultConfigBuilder
          .withDockerHost("unix:///host/var/run/docker.sock")
          .build()
        val docker = DockerClientBuilder.getInstance(config).build()
        val configHost = new HostConfig().withBinds(
            new Bind(s"${PROFILE_PATH}/hosts", new Volume("/etc/hosts")),
            new Bind(s"${PROFILE_PATH}/artifacts/genesis.block", new Volume("/var/hyperledger/orderer/orderer.genesis.block")),
            new Bind(s"${PROFILE_PATH}/crypto/ordererOrganizations/${ORG}.${DOMAIN}/orderers/${OSN_NAME}.${ORG}.${DOMAIN}/msp", new Volume("/var/hyperledger/orderer/msp")),
            new Bind(s"${PROFILE_PATH}/crypto/ordererOrganizations/${ORG}.${DOMAIN}/orderers/${OSN_NAME}.${ORG}.${DOMAIN}/tls", new Volume("/var/hyperledger/orderer/tls"))
        ).withPortBindings(
            new PortBinding(new Binding("0.0.0.0", s"${OSN_PORT}"), new ExposedPort(OSN_PORT, InternetProtocol.TCP))
        ).withNetworkMode("testnet")
        val osnContainerId: String = docker.createContainerCmd("hyperledger/fabric-orderer")
          .withName(ordererName)
          .withEnv(List(
              "FABRIC_LOGGING_SPEC=DEBUG",
              "ORDERER_GENERAL_LISTENADDRESS=0.0.0.0",
              s"ORDERER_GENERAL_LISTENPORT=${OSN_PORT}",
              "ORDERER_GENERAL_GENESISMETHOD=file",
              "ORDERER_GENERAL_GENESISFILE=/var/hyperledger/orderer/orderer.genesis.block",
              s"ORDERER_GENERAL_LOCALMSPID=osn-${ORG}",
              "ORDERER_GENERAL_LOCALMSPDIR=/var/hyperledger/orderer/msp",
              "ORDERER_GENERAL_TLS_ENABLED=true",
              "ORDERER_GENERAL_TLS_PRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
              "ORDERER_GENERAL_TLS_CERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
              "ORDERER_GENERAL_TLS_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]",
              "ORDERER_GENERAL_CLUSTER_CLIENTCERTIFICATE=/var/hyperledger/orderer/tls/server.crt",
              "ORDERER_GENERAL_CLUSTER_CLIENTPRIVATEKEY=/var/hyperledger/orderer/tls/server.key",
              "ORDERER_GENERAL_CLUSTER_ROOTCAS=[/var/hyperledger/orderer/tls/ca.crt]",
              "GODEBUG=netdns=go"
          ).asJava)
          .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric")
          .withCmd("orderer")
          .withExposedPorts(new ExposedPort(OSN_PORT, InternetProtocol.TCP))
          .withHostConfig(configHost)
          .exec().getId
        docker.startContainerCmd(osnContainerId).exec
        logger.info(s"Orderer container started with ID ${osnContainerId}")
        logger.info("====================================================")
        osnContainerId
    }

    //=============================================================================
    def peerStart(PROFILE_PATH: String, ORG: String, DOMAIN: String, PEER_NAME: String, PEER_PORT_1: Int): String = {
        logger.info(s"Request for ${PEER_NAME} start.")
        val peerName = s"${PEER_NAME}.${ORG}.${DOMAIN}"
        if (checkContainerExistence(peerName: String)) {
            stopAndRemoveContainer(peerName: String)
        }
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder
          .withDockerHost("unix:///host/var/run/docker.sock")
          .build()
        val docker = DockerClientBuilder.getInstance(config).build()
        val configHost = new HostConfig().withBinds(
            new Bind(s"${PROFILE_PATH}/hosts", new Volume("/etc/hosts")),
            new Bind(s"${PROFILE_PATH}/crypto/peerOrganizations/${ORG}.${DOMAIN}/peers/peer0.${ORG}.${DOMAIN}/msp", new Volume("/etc/hyperledger/fabric/msp")),
            new Bind(s"${PROFILE_PATH}/crypto/peerOrganizations/${ORG}.${DOMAIN}/peers/peer0.${ORG}.${DOMAIN}/tls", new Volume("/etc/hyperledger/fabric/tls")),
            new Bind(s"/var/run", new Volume("/host/var/run/"))
        ).withPortBindings(
            new PortBinding(new Binding("0.0.0.0", s"${PEER_PORT_1}"), new ExposedPort(PEER_PORT_1, InternetProtocol.TCP))
        ).withNetworkMode("testnet")

        val peerContainerId: String = docker.createContainerCmd("hyperledger/fabric-peer")
          .withName(peerName)
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
              s"CORE_PEER_ID=peer0.${ORG}.${DOMAIN}",
              s"CORE_PEER_ADDRESS=0.0.0.0:${PEER_PORT_1}",
              s"CORE_PEER_LISTENADDRESS=0.0.0.0:${PEER_PORT_1}",
              "CORE_CHAINCODE_JAVA_RUNTIME=apolubelov/fabric-scalaenv",
              s"CORE_PEER_GOSSIP_BOOTSTRAP=peer0.${ORG}.${DOMAIN}:${PEER_PORT_1}",
              s"CORE_PEER_GOSSIP_EXTERNALENDPOINT=peer0.${ORG}.${DOMAIN}:${PEER_PORT_1}",
              s"CORE_PEER_LOCALMSPID=${ORG}",
              "CORE_LEDGER_STATE_STATEDATABASE=CouchDB",
              s"CORE_LEDGER_STATE_COUCHDBCONFIG_COUCHDBADDRESS=couchdb0.${ORG}.${DOMAIN}:5984",
              "CORE_LEDGER_STATE_COUCHDBCONFIG_USERNAME=",
              "CORE_LEDGER_STATE_COUCHDBCONFIG_PASSWORD=",
              "GODEBUG=netdns=go"
          ).asJava)
          .withWorkingDir("/opt/gopath/src/github.com/hyperledger/fabric/peer")
          .withCmd("peer", "node", "start")
          .withExposedPorts(new ExposedPort(PEER_PORT_1, InternetProtocol.TCP))
          .withHostConfig(configHost)
          .exec().getId
        docker.startContainerCmd(peerContainerId).exec
        logger.info(s"Orderer container started with ID ${peerContainerId}")
        logger.info("====================================================")
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
          ).withNetworkMode("testnet")
        val couchDBContainerId: String = docker.createContainerCmd("hyperledger/fabric-couchdb")
          .withName(couchDBName)
          .withEnv(List(
              "COUCHDB_USER=",
              "COUCHDB_PASSWORD="
          ).asJava)
          .withExposedPorts(new ExposedPort(COUCHDB_PORT, InternetProtocol.TCP))
          .withHostConfig(configHost)
          .exec().getId
        docker.startContainerCmd(couchDBContainerId).exec
        logger.info(s"Orderer container started with ID ${couchDBContainerId}")
        logger.info("====================================================")
        couchDBContainerId
    }

}