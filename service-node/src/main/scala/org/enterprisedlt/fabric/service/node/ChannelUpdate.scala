package org.enterprisedlt.fabric.service.node

import org.hyperledger.fabric.protos.common.Configtx
import org.hyperledger.fabric.protos.common.Configtx.{ConfigUpdate, ConfigValue}
import org.hyperledger.fabric.protos.common.Configuration.OrdererAddresses
import org.hyperledger.fabric.protos.ext.orderer.Configuration.ConsensusType
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration.ConfigMetadata
import org.hyperledger.fabric.protos.peer.Configuration.{AnchorPeer, AnchorPeers}

/**
  * @author Alexey Polubelov
  */
object ChannelUpdate {

    //=========================================================================
    def AddAnchorPeer(orgName: String, host: String, port: Int)
    : Configtx.Config => ConfigUpdate.Builder = { currentConfig =>
        val currentApplicationGroup = currentConfig.getChannelGroup.getGroupsMap.get("Application")

        // ================= read set =================
        val readSet = Configtx.ConfigGroup.newBuilder()
        readSet.putGroups("Application", currentApplicationGroup)

        // ================= write set =================
        val writeSet = Configtx.ConfigGroup.newBuilder()

        val newAnchorPeer = AnchorPeer.newBuilder()
        newAnchorPeer.setHost(host)
        newAnchorPeer.setPort(port)

        val anchorPeers = Option(
            currentApplicationGroup
              .getGroupsMap.get(orgName)
              .getValuesMap.get("AnchorPeers"))

        // Get or create AnchorPeers
        val anchorPeersValue = anchorPeers
          .map(_.getValue)
          .map(AnchorPeers.parseFrom)
          .map(_.toBuilder)
          .getOrElse(AnchorPeers.newBuilder)

        anchorPeersValue.addAnchorPeers(newAnchorPeer)

        val newAnchorPeers = anchorPeers
          .map(existing =>
              existing
                .toBuilder
                .setVersion(existing.getVersion + 1)
          )
          .getOrElse(
              ConfigValue.newBuilder()
                .setModPolicy("Admins")
          )
        newAnchorPeers.setValue(anchorPeersValue.build.toByteString)

        val newOrgGroup = currentApplicationGroup.getGroupsMap.get(orgName).toBuilder
        newOrgGroup.putValues("AnchorPeers", newAnchorPeers.build)
        newOrgGroup.setVersion(newOrgGroup.getVersion + 1)

        val newApplicationGroup = currentApplicationGroup.toBuilder
        newApplicationGroup.putGroups(orgName, newOrgGroup.build)

        writeSet.putGroups("Application", newApplicationGroup.build)

        ConfigUpdate.newBuilder()
          .setReadSet(readSet.build)
          .setWriteSet(writeSet.build)
    }

    //=========================================================================
    // newOrgConfig.getChannelGroup.getGroupsMap.get("Orderer").getGroupsMap
    def AddOrderingOrg(orgName: String, newOrgConfig: Configtx.ConfigGroup)
    : Configtx.Config => ConfigUpdate.Builder = { currentConfig =>
        // ================= read set =================
        val readSet = Configtx.ConfigGroup.newBuilder()
        readSet.putGroups(
            "Orderer",
            currentConfig.getChannelGroup.getGroupsMap.get("Orderer")
        )
        // ================= write set =================
        val writeSet = Configtx.ConfigGroup.newBuilder()
        val newOrdererGroup = currentConfig.getChannelGroup.getGroupsMap.get("Orderer").toBuilder
        newOrdererGroup.putGroups(orgName, newOrgConfig)

        newOrdererGroup.setVersion(currentConfig.getChannelGroup.getGroupsMap.get("Orderer").getVersion + 1)
        writeSet.putGroups("Orderer", newOrdererGroup.build)

        // ================= the update =================
        ConfigUpdate.newBuilder()
          .setReadSet(readSet.build())
          .setWriteSet(writeSet.build())
    }

    //=========================================================================
    // newOrgConfig.getChannelGroup.getGroupsMap.get("Application")
    // from consortium: newOrgConfig.getChannelGroup.getGroupsMap.get("Consortiums").getGroupsMap.get("SampleConsortium").getGroupsMap
    def AddApplicationOrg(orgName: String, newOrgConfig: Configtx.ConfigGroup)
    : Configtx.Config => ConfigUpdate.Builder = { currentConfig =>
        val currentAppGroup = currentConfig.getChannelGroup.getGroupsMap.get("Application")
        // ================= read set =================
        val readSet = Configtx.ConfigGroup.newBuilder()
        readSet.putGroups("Application", currentAppGroup)
        // ================= write set =================
        val writeSet = Configtx.ConfigGroup.newBuilder()
          .putGroups("Application",
              currentAppGroup.toBuilder
                .putGroups(orgName, newOrgConfig)
                .setVersion(currentAppGroup.getVersion + 1)
                .build
          )
        // ================= the update =================
        ConfigUpdate.newBuilder()
          .setReadSet(readSet.build())
          .setWriteSet(writeSet.build())
    }

    //=========================================================================
    // newOrgConfig.getChannelGroup.getGroupsMap.get("Consortiums").getGroupsMap.get("SampleConsortium")
    def AddConsortiumOrg(orgName: String, newOrgConfig: Configtx.ConfigGroup)
    : Configtx.Config => ConfigUpdate.Builder = { currentConfig =>
        val currentConsortium = currentConfig.getChannelGroup.getGroupsMap.get("Consortiums").getGroupsMap.get("SampleConsortium")
        // ================= read set =================
        val readSet = Configtx.ConfigGroup.newBuilder()
        val consortiumsGroup = Configtx.ConfigGroup.newBuilder()
        consortiumsGroup.putGroups("SampleConsortium", currentConsortium)
        readSet.putGroups("Consortiums", consortiumsGroup.build())

        // ================= write set =================
        val writeSet = Configtx.ConfigGroup.newBuilder()
        val newSampleConsortiumGroup = currentConsortium.toBuilder
          .putGroups(orgName, newOrgConfig)
          .setVersion(currentConsortium.getVersion + 1)

        val newConsortiumsGroup = Configtx.ConfigGroup.newBuilder()
          .putGroups("SampleConsortium", newSampleConsortiumGroup.build())

        writeSet.putGroups("Consortiums", newConsortiumsGroup.build())

        // ================= the update =================
        ConfigUpdate.newBuilder()
          .setReadSet(readSet.build())
          .setWriteSet(writeSet.build())
    }

    // =================================================
    def AddConsenter(newConsenter: Configuration.Consenter)
    : Configtx.Config => ConfigUpdate.Builder = { currentConfig =>
        val channelGroup = currentConfig.getChannelGroup
        val orderer = channelGroup.getGroupsMap.get("Orderer")
        val ordererAddresses = channelGroup.getValuesMap.get("OrdererAddresses")
        val consensusTypeValue = orderer.getValuesMap.get("ConsensusType")
        val consensusType = ConsensusType.parseFrom(consensusTypeValue.getValue)

        val metadata = ConfigMetadata.parseFrom(consensusType.getMetadata)

        // ================= read set =================
        val readSet = Configtx.ConfigGroup.newBuilder()
        readSet.putGroups("Orderer", orderer)
        readSet.putValues("OrdererAddresses", ordererAddresses)

        // ================= write set =================
        val newMetaData = metadata.toBuilder
        newMetaData.addConsenters(newConsenter)

        val newConsensusType = consensusType.toBuilder
        newConsensusType.setMetadata(newMetaData.build.toByteString)

        val newConsensusTypeValue = consensusTypeValue.toBuilder
        newConsensusTypeValue.setValue(newConsensusType.build.toByteString)
        newConsensusTypeValue.setVersion(newConsensusTypeValue.getVersion + 1)

        val newOrderer = orderer.toBuilder
        newOrderer.putValues("ConsensusType", newConsensusTypeValue.build)

        val writeSet = Configtx.ConfigGroup.newBuilder()
        writeSet.putGroups("Orderer", newOrderer.build)

        // addresses
        val newOrdererAddressesValue = OrdererAddresses.parseFrom(ordererAddresses.getValue).toBuilder
        newOrdererAddressesValue.addAddresses(s"${newConsenter.getHost}:${newConsenter.getPort}")

        val newOrdererAddresses = ordererAddresses.toBuilder
        newOrdererAddresses.setValue(newOrdererAddressesValue.build.toByteString)
        newOrdererAddresses.setVersion(newOrdererAddresses.getVersion + 1)
        writeSet.putValues("OrdererAddresses", newOrdererAddresses.build)

        //
        val configUpdate = ConfigUpdate.newBuilder()
        configUpdate.setReadSet(readSet.build())
        configUpdate.setWriteSet(writeSet.build)
    }

}
