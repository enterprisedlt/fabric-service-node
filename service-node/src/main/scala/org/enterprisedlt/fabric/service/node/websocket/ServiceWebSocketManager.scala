package org.enterprisedlt.fabric.service.node.websocket

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.servlet.{ServletUpgradeRequest, ServletUpgradeResponse, WebSocketCreator}

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
object ServiceWebSocketManager extends WebSocketCreator {
    private val wsClients = Collections.newSetFromMap[WSSessionHolder](new ConcurrentHashMap()).asScala

    override def createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): AnyRef = {
        val socket = new WSSessionHolder
        wsClients.add(socket)
        socket
    }

    def broadcastText(message: String): Unit = wsClients.foreach(_.sendText(message))

    class WSSessionHolder extends WebSocketAdapter {

        override def onWebSocketClose(statusCode: Int, reason: String): Unit = {
            wsClients.remove(this)
            super.onWebSocketClose(statusCode, reason)
        }

        def sendText(message: String): Unit = {
            if (isConnected) {
                Option(getRemote).foreach(_.sendString(message))
            }
        }
    }

}
