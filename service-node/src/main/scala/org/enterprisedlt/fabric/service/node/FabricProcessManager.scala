package org.enterprisedlt.fabric.service.node

/**
  * @author Alexey Polubelov
  */
trait FabricProcessManager {
    def startOrderingNode(name: String, port: Int): String
    def awaitOrderingJoinedRaft(name: String)
    def startPeerNode(name: String, port: Int): String
}
