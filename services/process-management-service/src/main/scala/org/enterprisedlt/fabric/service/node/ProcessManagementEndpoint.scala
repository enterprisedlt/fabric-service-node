package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.enterprisedlt.fabric.service.node.services.ProcessManagementManager
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
class ProcessManagementEndpoint(
    processManager: DockerBasedProcessManager
) extends ProcessManagementManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def startOrderingNode(name: String): Either[String, String] = {
        logger.info(s"Starting ordering node with name $name ...")
        processManager.startOrderingNode(name)
    }

    override def startPeerNode(name: String): Either[String, String] = {
        logger.info(s"Starting peer node with name $name ...")
        processManager.startPeerNode(name)
    }

    override def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit] = {
        logger.info(s"Terminating chaincode $chainCodeName version $chainCodeVersion ...")
        processManager.terminateChainCode(peerName, chainCodeName, chainCodeVersion)
        logger.info(s"Chaincode $chainCodeName version $chainCodeVersion terminated")
        Right(())
    }

    override def osnAwaitJoinedToRaft(name: String): Either[String, Unit] = {
        logger.info(s"Waiting osn $name joined to raft ...")
        processManager.osnAwaitJoinedToRaft(name)
        Right(())
    }

    override def osnAwaitJoinedToChannel(name: String, channelName: String): Either[String, Unit] = {
        logger.info(s"Waiting osn $name joined to channel ...")
        processManager.osnAwaitJoinedToChannel(name, channelName)
        Right(())
    }

    override def peerAwaitForBlock(name: String, blockNumber: Long): Either[String, Unit] = {
        logger.info(s"Waiting peer $name for block $blockNumber ...")
        processManager.peerAwaitForBlock(name, blockNumber)
        Right(())
    }
}
