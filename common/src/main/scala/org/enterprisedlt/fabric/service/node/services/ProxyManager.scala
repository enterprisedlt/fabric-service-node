package org.enterprisedlt.fabric.service.node.services

import com.google.protobuf.ByteString
import org.enterprisedlt.fabric.service.node.model.ChaincodeRequest
import org.enterprisedlt.fabric.service.node.rest.Post

/**
  * @author Maxim Fedin
  */
trait ProxyManager {

    @Post("/query-chaincode")
    def queryChainCode(request: ChaincodeRequest): Either[String, Iterable[ByteString]]

    @Post("/invoke-chaincode")
    def invokeChainCode(request: ChaincodeRequest): Either[String, Unit]

}
