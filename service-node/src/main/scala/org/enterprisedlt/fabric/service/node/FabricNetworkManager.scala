package org.enterprisedlt.fabric.service.node

import java.io.{File, FileReader, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.PrivateKey
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.{Collections, Properties}

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope}
import org.hyperledger.fabric.protos.common.Configtx
import org.hyperledger.fabric.protos.common.Configtx.ConfigUpdate
import org.hyperledger.fabric.sdk._
import org.hyperledger.fabric.sdk.identity.X509Enrollment
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
class FabricNetworkManager(
    val config: NetworkConfig
) {
    type TransactionEvent = BlockEvent#TransactionEvent

    private val logger = LoggerFactory.getLogger(this.getClass)
    private val organizationAdmin = loadUser(config.orgAdmin.name, config.orgID, config.orgAdmin.msp)
    private val orderingAdmin = loadUser(config.orderingAdmin.name, s"osn-${config.orgID}", config.orderingAdmin.msp)

    private val fabricClient = getHFClient(organizationAdmin)
    private val osnByName = config.orderingNodes.map { config => (config.name, mkOSN(config)) }.toMap
    private val peerByName = config.peerNodes.map { config => (config.name, mkPeer(config)) }.toMap

    private lazy val systemClient = getHFClient(orderingAdmin)
    private lazy val systemChannel = connectToSystemChannel

    //
    //
    //

    //=========================================================================
    def createChannel(channelName: String, channelTx: Envelope): Unit = {
        val osn = osnByName.head._2 // for now, just use first
        val chCfg = new ChannelConfiguration(channelTx.toByteArray)
        val sign = fabricClient.getChannelConfigurationSignature(chCfg, organizationAdmin)
        fabricClient.newChannel(channelName, osn, chCfg, sign)
    }

    //=========================================================================
    def defineChannel(channelName: String): Unit = {
        val channel = fabricClient.newChannel(channelName)
        channel.addOrderer(osnByName.head._2) // for now, add only first
    }

    //=========================================================================
    def fetchLatestChannelBlock(channelName: String): Either[String, Block] = {
        getChannel(channelName).map(fetchConfigBlock)
    }

    //=========================================================================
    def fetchLatestSystemBlock: Block = {
        fetchConfigBlock(systemChannel)
    }

    //=========================================================================
    def addPeerToChannel(channelName: String, peerName: String): Either[String, Peer] = {
        Option(fabricClient.getChannel(channelName))
          .toRight(s"Unknown channel $channelName")
          .flatMap { channel =>
              peerByName
                .get(peerName)
                .toRight(s"Unknown peer $peerName")
                .map { peer =>
                    channel.joinPeer(peer)
                    peer
                }
          }
    }

    //=========================================================================
    def addAnchorsToChannel(channelName: String, peerName: String): Either[String, Unit] = {
        getChannel(channelName)
          .flatMap { channel =>
              config.peerNodes
                .find(_.name == peerName)
                .toRight(s"Unknown peer $peerName")
                .map { peerConfig =>
                    applyChannelUpdate(channel, organizationAdmin, ChannelUpdate.AddAnchorPeer(config.orgID, peerConfig.host, peerConfig.port))
                }
          }
    }

    //=========================================================================
    def installChainCode(channelName: String, ccName: String, version: String, chainCodeTarGzStream: InputStream): Either[String, util.Collection[ProposalResponse]] = {
        getChannel(channelName)
          .map { channel =>
              val installProposal = fabricClient.newInstallProposalRequest()
              val chaincodeID =
                  ChaincodeID.newBuilder()
                    .setName(ccName)
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

    //=========================================================================
    def instantiateChainCode(
        channelName: String, ccName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    ): Either[String, BlockEvent#TransactionEvent] = {
        getChannel(channelName)
          .flatMap { channel =>
              val instantiateProposalRequest = fabricClient.newInstantiationProposalRequest
              val chaincodeID =
                  ChaincodeID.newBuilder()
                    .setName(ccName)
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
                  logger.debug("Sending transaction to orderer...")
                  val te = channel
                    .sendTransaction(toSend, organizationAdmin)
                    .get(5, TimeUnit.MINUTES)
                  Right(te)
              }
          }
    }

    //=========================================================================
    //        val endorsementPolicy = policyAnyOf(listMembers(client, channel, user))
    def upgradeChainCode(
        channelName: String, ccName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    ): Unit = {
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
              upgradeProposalRequest.setProposalWaitTime(TimeUnit.MINUTES.toMillis(5)) // TODO

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
                  logger.debug("Sending transaction to orderer...")
                  val te = channel
                    .sendTransaction(toSend, organizationAdmin)
                    .get(5, TimeUnit.MINUTES)
                  Right(te)
              }
          }
    }

    //=========================================================================
    def queryChainCode(channelName: String, chainCodeName: String, functionName: String, arguments: String*): Either[String, Iterable[String]] = {
        getChannel(channelName)
          .map { channel =>
              val request = fabricClient.newQueryProposalRequest()
              request.setFcn(functionName)
              request.setArgs(arguments: _*)
              request.setChaincodeID(ChaincodeID.newBuilder().setName(chainCodeName).build)
              val queryProposals = channel.queryByChaincode(request)
              queryProposals.asScala
                .flatMap(x => Option(x.getProposalResponse))
                .flatMap(x => Option(x.getResponse))
                .flatMap(x => Option(x.getPayload))
                .flatMap(x => Option(x.toStringUtf8))
                .map(_.trim)
                .filter(_.nonEmpty)
          }
    }

    //=========================================================================
    def invokeChainCode(channelName: String, chainCodeName: String, functionName: String, arguments: String*): Either[String, CompletableFuture[TransactionEvent]] = {
        getChannel(channelName)
          .flatMap { channel =>
              val transactionProposalRequest = fabricClient.newTransactionProposalRequest()
              transactionProposalRequest.setChaincodeID(ChaincodeID.newBuilder().setName(chainCodeName).build)
              transactionProposalRequest.setFcn(functionName)
              transactionProposalRequest.setArgs(arguments: _*)
              logger.debug(s"Sending transaction proposal: $functionName${arguments.mkString("(", ",", ")")}")
              val invokePropResp = channel.sendTransactionProposal(transactionProposalRequest).asScala
              val byStatus = invokePropResp.groupBy { response => response.getStatus }
              val successful = byStatus.getOrElse(ChaincodeResponse.Status.SUCCESS, List.empty)
              val failed = byStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
              logger.debug(s"Received ${invokePropResp.size} transaction proposal responses. Successful: ${successful.size}. Failed: ${failed.size}")
              if (failed.nonEmpty) {
                  failed.foreach { errorResponse =>
                      logger.error(s"Endorsement failed on [${errorResponse.getPeer.getName}] : ${errorResponse.getMessage}")
                  }
                  Left("Got endorsement errors.")
              } else {
                  logger.debug("Successfully received transaction proposal responses.")
                  val toSend = invokePropResp.asJavaCollection
                  val proposalConsistencySets = SDKUtils.getProposalConsistencySets(toSend)
                  if (proposalConsistencySets.size() != 1) {
                      Left(s"Got inconsistent proposal responses [${proposalConsistencySets.size}]")
                  } else {
                      logger.debug("Sending transaction to orderer...")
                      Right(channel.sendTransaction(toSend, fabricClient.getUserContext))
                  }
              }
          }
    }

    //=========================================================================
    def joinToNetwork(newOrgConfig: Configtx.Config): Unit = {
        logger.info("Adding application org...")
        val appOrg = newOrgConfig.getChannelGroup
          .getGroupsMap.get("Application")
          .getGroupsMap.entrySet().iterator().next()
        applyChannelUpdate(
            systemChannel, orderingAdmin,
            ChannelUpdate.AddApplicationOrg(appOrg.getKey, appOrg.getValue)
        )
        logger.info("Adding ordering org...")
        val orderingOrg = newOrgConfig.getChannelGroup
          .getGroupsMap.get("Orderer")
          .getGroupsMap.entrySet().iterator().next()
        applyChannelUpdate(
            systemChannel, orderingAdmin,
            ChannelUpdate.AddOrderingOrg(orderingOrg.getKey, orderingOrg.getValue)
        )
        logger.info("Adding peers org...")
        val consortiumOrg = newOrgConfig.getChannelGroup
          .getGroupsMap.get("Consortiums")
          .getGroupsMap.get("SampleConsortium")
          .getGroupsMap.entrySet().iterator().next()
        applyChannelUpdate(
            systemChannel, orderingAdmin,
            ChannelUpdate.AddConsortiumOrg(consortiumOrg.getKey, consortiumOrg.getValue)
        )
        logger.info("Adding OSN 1...")
        val metadata = Util.extractConsensusMetadata(newOrgConfig)
        applyChannelUpdate(
            systemChannel, orderingAdmin,
            ChannelUpdate.AddConsenter(metadata.getConsenters(0))
        )
    }

    //=========================================================================
    def joinToChannel(channelName: String, newOrgConfig: Configtx.Config): Either[String, Unit] = {
        getChannel(channelName)
          .map { channel =>
              logger.info("Adding application org...")
              val consortiumOrg = newOrgConfig.getChannelGroup
                .getGroupsMap.get("Consortiums")
                .getGroupsMap.get("SampleConsortium")
                .getGroupsMap.entrySet().iterator().next()
              applyChannelUpdate(
                  channel, organizationAdmin,
                  ChannelUpdate.AddApplicationOrg(consortiumOrg.getKey, consortiumOrg.getValue),
              )
              logger.info("Adding ordering org...")
              val orderingOrg = newOrgConfig.getChannelGroup
                .getGroupsMap.get("Orderer")
                .getGroupsMap.entrySet().iterator().next()
              applyChannelUpdate(
                  channel, orderingAdmin,
                  ChannelUpdate.AddOrderingOrg(orderingOrg.getKey, orderingOrg.getValue)
              )
              logger.info("Adding OSN 1...")
              val metadata = Util.extractConsensusMetadata(newOrgConfig)
              applyChannelUpdate(
                  channel, orderingAdmin,
                  ChannelUpdate.AddConsenter(metadata.getConsenters(0))
              )
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
    private def loadUser(usernName: String, orgName: String, mspPath: String): User = {
        val signedCert = loadSignedCertFromFile(mspPath)
        val privateKey = loadPrivateKeyFromFile(mspPath)
        val adminEnrollment = new X509Enrollment(privateKey, signedCert)
        FabricUserImpl(usernName, Collections.emptySet(), "", "", adminEnrollment, orgName)
    }

    //=========================================================================
    private def loadPrivateKeyFromFile(filePath: String): PrivateKey = {
        val fileName = new File(s"$filePath/keystore").listFiles(n => n.getAbsolutePath.endsWith("_sk"))(0)
        val pemReader = new FileReader(fileName)
        val pemParser = new PEMParser(pemReader)
        try {
            pemParser.readObject() match {
                case pemKeyPair: PEMKeyPair => new JcaPEMKeyConverter().getKeyPair(pemKeyPair).getPrivate
                case keyInfo: PrivateKeyInfo => new JcaPEMKeyConverter().getPrivateKey(keyInfo)
                case null => throw new Exception(s"Unable to read PEM object")
                case other => throw new Exception(s"Unsupported PEM object ${other.getClass.getCanonicalName}")
            }
        } finally {
            pemParser.close()
            pemReader.close()
        }
    }

    //=========================================================================
    private def loadSignedCertFromFile(filePath: String): String = {
        val fileName = new File(s"$filePath/signcerts").listFiles(n => n.getAbsolutePath.endsWith(".pem"))(0)
        val r = Files.readAllBytes(Paths.get(fileName.toURI))
        new String(r, StandardCharsets.UTF_8)
    }

    //=========================================================================
    private def getChannel(channelName: String): Either[String, Channel] =
        Option(fabricClient.getChannel(channelName))
          .toRight(s"Unknown channel $channelName")
          .map(_.initialize())

    //=========================================================================
    private def mkPeer(config: PeerConfig): Peer = {
        val properties = new Properties()
        properties.put("pemFile", config.tls)
        fabricClient.newPeer(config.name, s"grpcs://${config.host}:${config.port}", properties)
    }

    //=========================================================================
    private def mkOSN(config: OSNConfig): Orderer = {
        val properties = new Properties()
        properties.put("pemFile", config.tls)
        fabricClient.newOrderer(config.name, s"grpcs://${config.host}:${config.port}", properties)
    }

    //=========================================================================
    private def connectToSystemChannel: Channel = {
        val channel = systemClient.newChannel("system-channel")
        config.orderingNodes /*.headOption*/ .foreach { config => channel.addOrderer(mkOSN(config)) }
        channel.initialize()
    }

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
}
