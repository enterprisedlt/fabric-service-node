package org.enterprisedlt.fabric.service.node

import com.google.protobuf.ByteString
import org.enterprisedlt.fabric.service.node.client.FabricNetworkManager
import org.enterprisedlt.fabric.service.node.model.ChaincodeRequest
import org.enterprisedlt.fabric.service.node.services.ProxyManager
import org.slf4j.LoggerFactory

/**
  * @author Maxim Fedin
  */
class ProxyRestEndpoint(
    fabricNetworkManager: FabricNetworkManager
) extends ProxyManager {
    private val logger = LoggerFactory.getLogger(this.getClass)


    override def queryChainCode(request: ChaincodeRequest): Either[String, Iterable[ByteString]] = {
        logger.info(s"Querying chaincode ...")
        fabricNetworkManager.queryChainCode(request.channelName, request.chainCodeName, request.functionName, request.arguments: _*)

    }

    override def invokeChainCode(request: ChaincodeRequest): Either[String, Unit] = {
        logger.info(s"Invoking chaincode ...")
        fabricNetworkManager.invokeChainCode(request.channelName, request.chainCodeName, request.functionName, request.arguments: _*)
        Right(())
    }
}
