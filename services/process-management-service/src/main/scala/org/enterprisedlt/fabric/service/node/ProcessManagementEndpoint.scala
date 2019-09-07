package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.enterprisedlt.fabric.service.node.rest.Get
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
class ProcessManagementEndpoint(processManager: DockerBasedProcessManager) {
    private val logger = LoggerFactory.getLogger(this.getClass)

    @Get("/services/process-management/start-ordering-node")
    def startOrderingNode(name: String): Either[String, String] = {
        logger.info(s"Starting ordering node with name $name ...")
        processManager.startOrderingNode(name)
    }

    @Get("/services/process-management/start-peer-node")
    def startPeerNode(name: String): Either[String, String] = {
        logger.info(s"Starting peer node with name $name ...")
        processManager.startPeerNode(name)
    }

    @Get("/services/process-management/terminate-chaincode")
    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, String] = {
        logger.info(s"Terminating chaincode $chainCodeName version $chainCodeVersion ...")
        processManager.terminateChainCode(peerName, chainCodeName, chainCodeVersion)
        logger.info(s"Chaincode $chainCodeName version $chainCodeVersion terminated")
        Right("OK")
    }

    @Get("/services/process-management/osn-await-joined-to-raft")
    def osnAwaitJoinedToRaft(name: String): Either[String, String] = {
        logger.info(s"Waiting osn $name joined to raft ...")
        processManager.osnAwaitJoinedToRaft(name)
        Right("OK")
    }

    @Get("/services/process-management/osn-await-joined-to-channel")
    def osnAwaitJoinedToChannel(name: String, channelName: String): Either[String, String] = {
        logger.info(s"Waiting osn $name joined to channel ...")
        processManager.osnAwaitJoinedToChannel(name, channelName)
        Right("OK")
    }

    @Get("/services/process-management/peer-await-for-block")
    def peerAwaitForBlock(name: String, blockNumber: Long): Either[String, String] = {
        logger.info(s"Waiting peer $name for block $blockNumber ...")
        processManager.peerAwaitForBlock(name, blockNumber)
        Right("OK")
    }
}
