package org.enterprisedlt.fabric.service.node.administration

import java.io.InputStream
import java.util
import java.util.Properties
import java.util.concurrent.TimeUnit

import org.enterprisedlt.fabric.service.node.configuration.{OSNConfig, OrganizationConfig, PeerConfig}
import org.enterprisedlt.fabric.service.node.proto.FabricChannel
import org.enterprisedlt.fabric.service.node.services.{AdministrationManager, OperationTimeout}
import org.enterprisedlt.fabric.service.node.util.Util
import org.hyperledger.fabric.protos.common.Common.Block
import org.hyperledger.fabric.protos.common.Configtx.ConfigUpdate
import org.hyperledger.fabric.protos.common.{Common, Configtx}
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration.Consenter
import org.hyperledger.fabric.sdk._
import org.hyperledger.fabric.sdk.helper.Config
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
  * @author Maxim Fedin
  */
class AdministrationNetworkManager(organization: OrganizationConfig,
    bootstrapOsn: OSNConfig, admin: User) extends AdministrationManager {

    type TransactionEvent = BlockEvent#TransactionEvent

    // ---------------------------------------------------------------------------------------------------------------
    // Setup some defaults for Fabric SDK
    // time out for block fetch operations (default 5 Second):
    System.setProperty(Config.GENESISBLOCK_WAIT_TIME, TimeUnit.MINUTES.toMillis(5).toString)
    // time out for OSN response (default 10 Second):
    System.setProperty(Config.ORDERER_WAIT_TIME, TimeUnit.MINUTES.toMillis(1).toString)
    // ---------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val organizationFullName = s"${organization.name}.${organization.domain}"
    private val fabricClient = getHFClient(admin)

    private val peerByName = TrieMap.empty[String, PeerConfig]
    private val osnByName = TrieMap(bootstrapOsn.name -> bootstrapOsn)
    // ---------------------------------------------------------------------------------------------------------------
    private lazy val systemChannel: Channel = connectToSystemChannel


    //=========================================================================
    // Interface methods
    //=========================================================================

    //=========================================================================


    override def definePeer(peerNode: PeerConfig): Either[String, Unit] = {
        if (peerByName.contains(peerNode.name)) {
            Left("Already defined")
        } else {
            peerByName += (peerNode.name -> peerNode)
            Right(())
        }
    }

    override def addPeerToChannel(channelName: String, peerName: String): Either[String, Peer] = {
        Option(fabricClient.getChannel(channelName))
          .toRight(s"Unknown channel $channelName")
          .flatMap { channel =>
              peerByName
                .get(peerName)
                .toRight(s"Unknown peer $peerName")
                .map { peerConfig =>
                    val peer = mkPeer(peerConfig)
                    channel.joinPeer(peer)
                    peer
                }
          }
    }

    override def addAnchorsToChannel(channelName: String, peerName: String): Either[String, Unit] =
        getChannel(channelName)
          .flatMap { channel =>
              peerByName.get(peerName)
                .toRight(s"Unknown peer $peerName")
                .map { peer =>
                    applyChannelUpdate(channel, admin, FabricChannel.AddAnchorPeer(organization.name, s"${peer.name}.$organizationFullName", peer.port))
                }
          }

    override def defineChannel(channelName: String): Either[String, Unit] = {
        val bootstrapOsnName = mkOSN(osnByName.head._2)
        val channel = fabricClient.newChannel(channelName)
        channel.addOrderer(bootstrapOsnName)
        Right(())
    }

    override def createChannel(channelName: String, channelTx: Common.Envelope): Either[String, Unit] = {
        val bootstrapOsnName = mkOSN(osnByName.head._2)
        val chCfg = new ChannelConfiguration(channelTx.toByteArray)
        val sign = fabricClient.getChannelConfigurationSignature(chCfg, admin)
        fabricClient.newChannel(channelName, bootstrapOsnName, chCfg, sign)
        Right(())
    }

    override def defineOsn(osnConfig: OSNConfig): Either[String, Unit] = {
        if (peerByName.contains(osnConfig.name)) {
            Left("Already defined")
        } else {
            osnByName += (osnConfig.name -> osnConfig)
            Right(())
        }
    }

    override def addOsnToChannel(osnName: String, cryptoPath: String, channelName: Option[String]): Either[String, Unit] = {
        osnByName.get(osnName)
          .toRight(s"Unknown osn $osnName")
          .map { osnConfig =>
              val consenter = Consenter.newBuilder()
                .setHost(s"${osnConfig.name}.$organizationFullName")
                .setPort(osnConfig.port)
                .setClientTlsCert(Util.readAsByteString(s"$cryptoPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt"))
                .setServerTlsCert(Util.readAsByteString(s"$cryptoPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt"))
                .build()
              val channel: Channel =
                  channelName
                    .flatMap { name =>
                        Option(fabricClient.getChannel(name))
                          .map(_.initialize())
                    }
                    .getOrElse(systemChannel)
              applyChannelUpdate(
                  channel, admin,
                  FabricChannel.AddConsenter(consenter)
              )
              val osn = mkOSN(osnConfig)
              channel.addOrderer(osn)
          }
    }

    override def fetchLatestChannelBlock(channelName: String): Either[String, Common.Block] = getChannel(channelName).map(fetchConfigBlock)


    override def fetchLatestSystemBlock: Either[String, Common.Block] = Right(fetchConfigBlock(systemChannel))

    override def installChainCode(channelName: String, chainCodeName: String, version: String, chainCodeTarGzStream: InputStream): Either[String, util.Collection[ProposalResponse]] = {
        getChannel(channelName)
          .map { channel =>
              val installProposal = fabricClient.newInstallProposalRequest()
              val chaincodeID =
                  ChaincodeID.newBuilder()
                    .setName(chainCodeName)
                    .setVersion(version)
                    .build

              installProposal.setChaincodeID(chaincodeID)
              installProposal.setChaincodeVersion(version)
              installProposal.setChaincodeLanguage(TransactionRequest.Type.JAVA) // TODO
              installProposal.setProposalWaitTime(TimeUnit.MINUTES.toMillis(5)) // TODO
              installProposal.setChaincodeInputStream(chainCodeTarGzStream)
              //TODO: will install to all known peers in channel, but probably this has to be configurable thru parameters
              fabricClient.sendInstallProposal(installProposal, channel.getPeers)
              // TODO: the results from peers has to be handled?
          }
    }

    override def instantiateChainCode(channelName: String, chainCodeName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy], collectionConfig: Option[ChaincodeCollectionConfiguration],
        arguments: Array[String]): Either[String, BlockEvent#TransactionEvent] = {
        getChannel(channelName)
          .flatMap { channel =>
              val instantiateProposalRequest = fabricClient.newInstantiationProposalRequest
              val chaincodeID =
                  ChaincodeID.newBuilder()
                    .setName(chainCodeName)
                    .setVersion(version)
                    .build
              instantiateProposalRequest.setChaincodeID(chaincodeID)
              instantiateProposalRequest.setChaincodeVersion(version)
              instantiateProposalRequest.setChaincodeLanguage(TransactionRequest.Type.JAVA) // TODO
              instantiateProposalRequest.setProposalWaitTime(TimeUnit.MINUTES.toMillis(5)) // TODO

              instantiateProposalRequest.setFcn("init")
              instantiateProposalRequest.setArgs(arguments: _*)

              instantiateProposalRequest.setChaincodeEndorsementPolicy(endorsementPolicy.getOrElse(new ChaincodeEndorsementPolicy))
              collectionConfig.foreach { collections =>
                  instantiateProposalRequest.setChaincodeCollectionConfiguration(collections)
              }

              //TODO: will trigger instantiate on all known peers in channel, but probably this has to be configurable thru parameters
              val responses = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers()).asScala

              val byStatus = responses.groupBy { response => response.getStatus }
              val successful = byStatus.getOrElse(ChaincodeResponse.Status.SUCCESS, List.empty)
              val failed = byStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
              logger.debug(s"Received ${responses.size} transaction proposal responses. Successful: ${successful.size}. Failed: ${failed.size}")
              if (failed.nonEmpty) {
                  val errors =
                      failed
                        .map(errorResponse => s"Proposal failed on [${errorResponse.getPeer.getName}] : ${errorResponse.getMessage}")
                        .mkString("\n")
                  logger.error(errors)
                  Left(errors)
              } else {
                  logger.debug("Successfully received transaction proposal responses.")
                  val toSend = responses.asJavaCollection
                  logger.debug("Sending transaction to osn...")
                  val te = channel
                    .sendTransaction(toSend, admin)
                    .get(5, TimeUnit.MINUTES)
                  Right(te)
              }
          }
    }

    override def upgradeChainCode(channelName: String, ccName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy], collectionConfig: Option[ChaincodeCollectionConfiguration],
        arguments: Array[String])(implicit timeout: OperationTimeout): Either[String, Unit] = {
        getChannel(channelName)
          .flatMap { channel =>
              val upgradeProposalRequest = fabricClient.newUpgradeProposalRequest
              val chaincodeID =
                  ChaincodeID.newBuilder()
                    .setName(ccName)
                    .setVersion(version)
                    .build
              upgradeProposalRequest.setChaincodeID(chaincodeID)
              upgradeProposalRequest.setChaincodeVersion(version)

              upgradeProposalRequest.setChaincodeLanguage(TransactionRequest.Type.JAVA) // TODO
              upgradeProposalRequest.setProposalWaitTime(timeout.milliseconds)

              upgradeProposalRequest.setFcn("init")
              upgradeProposalRequest.setArgs(arguments: _*)

              upgradeProposalRequest.setChaincodeEndorsementPolicy(endorsementPolicy.getOrElse(new ChaincodeEndorsementPolicy))
              collectionConfig.foreach { collections =>
                  upgradeProposalRequest.setChaincodeCollectionConfiguration(collections)
              }
              val responses = channel.sendUpgradeProposal(upgradeProposalRequest, channel.getPeers).asScala

              val byStatus = responses.groupBy { response => response.getStatus }
              val successful = byStatus.getOrElse(ChaincodeResponse.Status.SUCCESS, List.empty)
              val failed = byStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
              logger.debug(s"Received ${responses.size} transaction proposal responses. Successful: ${successful.size}. Failed: ${failed.size}")
              if (failed.nonEmpty) {
                  val errors =
                      failed
                        .map(errorResponse => s"Proposal failed on [${errorResponse.getPeer.getName}] : ${errorResponse.getMessage}")
                        .mkString("\n")
                  logger.error(errors)
                  Left(errors)
              } else {
                  logger.debug("Successfully received transaction proposal responses.")
                  val toSend = responses.asJavaCollection
                  logger.debug("Sending transaction to osn...")
                  channel
                    .sendTransaction(toSend, admin)
                    .get(5, TimeUnit.MINUTES)
                  Right(())
              }
          }
    }

    //=========================================================================
    // Private utility functions
    //=========================================================================

    //=========================================================================
    private def getCryptoSuite: CryptoSuite = CryptoSuite.Factory.getCryptoSuite()

    //=========================================================================
    private def getHFClient(user: User): HFClient = {
        val client = HFClient.createNewInstance()
        client.setCryptoSuite(getCryptoSuite)
        client.setUserContext(user)
        client
    }

    //=========================================================================
    private def connectToSystemChannel: Channel = {
        val bootstrapOsnName = mkOSN(osnByName.head._2)
        val channel = fabricClient.newChannel("system-channel")
        channel.addOrderer(bootstrapOsnName)
        channel.initialize()
    }

    //=========================================================================
    private def mkOSN(config: OSNConfig): Orderer = {
        val properties = new Properties()
        properties.put("pemFile", defaultOSNTLSPath(config.name))
        fabricClient.newOrderer(config.name, s"grpcs://${config.name}.$organizationFullName:${config.port}", properties)
    }

    //=========================================================================
    private def defaultOSNTLSPath(name: String): String = {
        s"/opt/profile/crypto/orderers/$name.$organizationFullName/tls/server.crt"
    }

    //=========================================================================
    private def mkPeer(config: PeerConfig): Peer = {
        val properties = new Properties()
        properties.put("pemFile", defaultPeerTLSPath(config.name))
        fabricClient.newPeer(config.name, s"grpcs://${config.name}.$organizationFullName:${config.port}", properties)
    }

    //=========================================================================
    private def defaultPeerTLSPath(name: String): String = {
        s"/opt/profile/crypto/peers/$name.$organizationFullName/tls/server.crt"
    }

    //=========================================================================
    private def getChannel(channelName: String): Either[String, Channel] =
        Option(fabricClient.getChannel(channelName))
          .toRight(s"Unknown channel $channelName")
          .map(_.initialize())

    //=========================================================================
    private def applyChannelUpdate(channel: Channel, user: User, update: Configtx.Config => ConfigUpdate.Builder): Unit = {
        logger.info(s"[${channel.getName}] - Fetching configuration...")
        val channelConfig = fetchConfigBlock(channel)
        val currentConfig = Util.extractConfig(channelConfig)
        logger.info(s"[${channel.getName}] - Preparing config update...")
        val configUpdate = update(currentConfig)
        configUpdate.setChannelId(channel.getName)
        logger.info(s"[${channel.getName}] - Applying config update...")
        val theUpdate = configUpdate.build()
        signAndSendConfigUpdate(channel, user, theUpdate)
        logger.info(s"[${channel.getName}] - Config update applied.")
    }

    //=========================================================================
    private def fetchConfigBlock(channel: Channel): Block = {
        val getConfigurationBlockMethod = classOf[Channel].getDeclaredMethod("getConfigurationBlock")
        getConfigurationBlockMethod.setAccessible(true)
        getConfigurationBlockMethod.invoke(channel).asInstanceOf[Block]
    }

    //=========================================================================
    private def signAndSendConfigUpdate(channel: Channel, user: User, configUpdate: ConfigUpdate): Unit = {
        val update = new UpdateChannelConfiguration()
        update.setUpdateChannelConfiguration(configUpdate.toByteArray)
        val sign = channel.getUpdateChannelConfigurationSignature(update, user)
        channel.updateChannelConfiguration(update, sign)
    }

    //=========================================================================


}
