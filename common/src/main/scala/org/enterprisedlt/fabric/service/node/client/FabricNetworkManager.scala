package org.enterprisedlt.fabric.service.node.client

import java.io.InputStream
import java.util.Properties
import java.util.concurrent.{CompletableFuture, TimeUnit}

import com.google.protobuf.ByteString
import org.enterprisedlt.fabric.service.model.Organization
import org.enterprisedlt.fabric.service.node.configuration.{OrganizationConfig, PeerConfig}
import org.enterprisedlt.fabric.service.node.model.{OSNDescriptor, OrganizationCertificates}
import org.enterprisedlt.fabric.service.node.proto._
import org.enterprisedlt.fabric.service.node.util.{PrivateCollectionConfiguration, Util}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope}
import org.hyperledger.fabric.protos.common.Configtx
import org.hyperledger.fabric.protos.common.Configtx.ConfigUpdate
import org.hyperledger.fabric.protos.common.MspPrincipal.MSPRole
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration.Consenter
import org.hyperledger.fabric.sdk.helper.Config
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.hyperledger.fabric.sdk.{BlockEvent, ChannelConfiguration, Peer, _}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap


/**
  * @author Alexey Polubelov
  */
class FabricNetworkManager(
    organization: OrganizationConfig,
    admin: User
) {
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
    private val osnByName = TrieMap.empty[String, OSNDescriptor]
    // ---------------------------------------------------------------------------------------------------------------
    private lazy val systemChannel: Channel = connectToSystemChannel

    //
    //
    //
    //=========================================================================
    def createChannel(channelName: String, channelTx: Envelope): Either[String, Unit] = {
        val bootstrapOsnName = mkOSN(osnByName.head._2)
        val chCfg = new ChannelConfiguration(channelTx.toByteArray)
        val sign = fabricClient.getChannelConfigurationSignature(chCfg, admin)
        fabricClient.newChannel(channelName, bootstrapOsnName, chCfg, sign)
        Right(())
    }

    //=========================================================================
    def defineChannel(channelName: String): Unit = {
        val bootstrapOsnName = mkOSN(osnByName.head._2)
        val channel = fabricClient.newChannel(channelName)
        channel.addOrderer(bootstrapOsnName)
    }

    //=========================================================================
    def fetchLatestChannelBlock(channelName: String): Either[String, Block] =
        getChannel(channelName).map(fetchConfigBlock)

    //=========================================================================
    def fetchLatestSystemBlock: Either[String, Block] =
        Right(fetchConfigBlock(systemChannel))


    def definePeer(peerNode: PeerConfig): Either[String, Unit] = {
        peerByName += (peerNode.name -> peerNode)
        Right(())
    }

    //=========================================================================
    def addPeerToChannel(channelName: String, peerName: String): Either[String, Peer] = {
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

    //=========================================================================
    def addAnchorsToChannel(channelName: String, peerName: String): Either[String, Unit] =
        getChannel(channelName)
          .flatMap { channel =>
              peerByName.get(peerName)
                .toRight(s"Unknown peer $peerName")
                .map { peer =>
                    applyChannelUpdate(channel, admin, FabricChannel.AddAnchorPeer(organization.name, s"${peer.name}.$organizationFullName", peer.port))
                }
          }

    //=========================================================================
    def installChainCode(channelName: String, chainCodeName: String, version: String, chainCodeTarGzStream: InputStream): Either[String, java.util.Collection[ProposalResponse]] = {
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

    //=========================================================================
    def instantiateChainCode(
        channelName: String, chainCodeName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    ): Either[String, BlockEvent#TransactionEvent] = {
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

    //=========================================================================
    //        val endorsementPolicy = policyAnyOf(listMembers(client, channel, user))
    def upgradeChainCode(
        channelName: String, ccName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    )(implicit timeout: OperationTimeout = OperationTimeout(5, TimeUnit.MINUTES))
    : Either[String, BlockEvent#TransactionEvent] = {
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
                  val te = channel
                    .sendTransaction(toSend, admin)
                    .get(5, TimeUnit.MINUTES)
                  Right(te)
              }
          }
    }

    //=========================================================================
    def queryChainCode
    (channelName: String, chainCodeName: String, functionName: String, arguments: String*)
      (implicit timeout: OperationTimeout = OperationTimeout(35, TimeUnit.SECONDS), user: Option[User] = None)
    : Either[String, Iterable[ByteString]] =
        getChannel(channelName)
          .map { channel =>
              val request = fabricClient.newQueryProposalRequest()
              request.setFcn(functionName)
              request.setArgs(arguments: _*)
              request.setProposalWaitTime(timeout.milliseconds)
              request.setChaincodeID(ChaincodeID.newBuilder().setName(chainCodeName).build)
              user.foreach(request.setUserContext)
              val queryProposals = channel.queryByChaincode(request)
              queryProposals.asScala
                .flatMap(x => Option(x.getProposalResponse))
                .flatMap(x => Option(x.getResponse))
                .flatMap(x => Option(x.getPayload))
          }

    //=========================================================================
    def setupBlockListener(channelName: String, listener: BlockListener): Either[String, String] =
        getChannel(channelName).map(_.registerBlockListener(listener))

    //=========================================================================
    def invokeChainCode(channelName: String, chainCodeName: String, functionName: String, arguments: String*)
      (implicit timeout: OperationTimeout = OperationTimeout(35, TimeUnit.SECONDS), user: Option[User] = None, transient: Option[java.util.Map[String, Array[Byte]]] = None)
    : Either[String, CompletableFuture[TransactionEvent]] = {
        getChannel(channelName)
          .flatMap { channel =>
              val request = fabricClient.newTransactionProposalRequest()
              request.setChaincodeID(ChaincodeID.newBuilder().setName(chainCodeName).build)
              request.setFcn(functionName)
              request.setArgs(arguments: _*)
              request.setProposalWaitTime(timeout.milliseconds)
              user.foreach(request.setUserContext)
              transient.foreach(request.setTransientMap)
              logger.debug(s"Sending transaction proposal: $functionName${arguments.mkString("(", ",", ")")}")
              val invokePropResp = channel.sendTransactionProposal(request).asScala
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

    def addOrgToChannel(organization: Organization, organizationCertificates: OrganizationCertificates, channelName: Option[String] = None): Either[String, Unit] = {
        val channel: Channel =
            channelName
              .flatMap { name =>
                  Option(fabricClient.getChannel(name))
                    .map(_.initialize())
              }
              .getOrElse(systemChannel)
        val organizationDefinition = OrganizationDefinition(
            mspId = organization.mspId,
            policies = PoliciesDefinition(
                admins = SignedByOneOf(MemberClassifier(organization.mspId, MSPRole.MSPRoleType.ADMIN)),
                writers = SignedByOneOf(MemberClassifier(organization.mspId, MSPRole.MSPRoleType.MEMBER)),
                readers = SignedByOneOf(MemberClassifier(organization.mspId, MSPRole.MSPRoleType.MEMBER))
            ),
            caCerts = Array(organizationCertificates.caCerts).map(Util.base64Decode).toSeq,
            tlsCACerts = Array(organizationCertificates.tlsCACerts).map(Util.base64Decode).toSeq,
            adminCerts = Array(organizationCertificates.adminCerts).map(Util.base64Decode).toSeq
        )
        val orderingOrganizationGroup = FabricBlock.newOrderingOrganizationGroup(organizationDefinition)
        logger.info("Adding application org...")
        applyChannelUpdate(
            channel, admin,
            FabricChannel.AddApplicationOrg(organizationDefinition.mspId, orderingOrganizationGroup)
        )
        logger.info("Adding ordering org...")
        applyChannelUpdate(
            channel, admin,
            FabricChannel.AddOrderingOrg(organizationDefinition.mspId, orderingOrganizationGroup)
        )
        Right(())
    }

    //=========================================================================
    def addOsnToChannel(osnName: String, channelName: Option[String] = None): Unit = {
        osnByName.get(osnName)
          .toRight(s"Unknown osn $osnName")
          .map { osnDescriptor =>
              val consenter = Consenter.newBuilder()
                .setHost(s"${osnDescriptor.name}.$organizationFullName")
                .setPort(osnDescriptor.port)
                .setClientTlsCert(Util.base64Decode(osnDescriptor.clientTLSCert))
                .setServerTlsCert(Util.base64Decode(osnDescriptor.serverTLSCert))
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
              val osn = mkOSN(osnDescriptor)
              channel.addOrderer(osn)
          }
    }

    def defineOsn(osnDescriptor: OSNDescriptor): Either[String, Unit] = {
        osnByName += (osnDescriptor.name -> osnDescriptor)
        Right(())
    }

    //=========================================================================
    def fetchCollectionsConfig(peerName: String, channelName: String, chainCodeName: String): Either[String, Iterable[PrivateCollectionConfiguration]] = {
        getChannel(channelName)
          .flatMap { channel =>
              peerByName
                .get(peerName)
                .toRight(s"Unknown peer $peerName")
                .map { peerConfig =>
                    val peer = mkPeer(peerConfig)
                    val collectionConfigPackage = channel.queryCollectionsConfig(chainCodeName, peer, admin)
                    Util.parseCollectionPackage(collectionConfigPackage)
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
    private def getChannel(channelName: String): Either[String, Channel] =
        Option(fabricClient.getChannel(channelName))
          .toRight(s"Unknown channel $channelName")
          .map(_.initialize())

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
    private def mkOSN(osnDescriptor: OSNDescriptor): Orderer = {
        val properties = new Properties()
        properties.put("pemFile", defaultOSNTLSPath(osnDescriptor.name))
        fabricClient.newOrderer(osnDescriptor.name, s"grpcs://${osnDescriptor.name}.$organizationFullName:${osnDescriptor.port}", properties)
    }

    //=========================================================================
    private def defaultOSNTLSPath(name: String): String = {
        s"/opt/profile/crypto/orderers/$name.$organizationFullName/tls/server.crt"
    }

    //=========================================================================
    private def connectToSystemChannel: Channel = {
        val bootstrapOsnName = mkOSN(osnByName.head._2)
        val channel = fabricClient.newChannel("system-channel")
        channel.addOrderer(bootstrapOsnName)
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

case class OperationTimeout(
    value: Long,
    timeUnit: TimeUnit = TimeUnit.MILLISECONDS
) {
    def milliseconds: Long = timeUnit.toMillis(value)
}
