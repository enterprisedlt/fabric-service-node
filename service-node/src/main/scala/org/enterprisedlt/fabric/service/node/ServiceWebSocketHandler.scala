package org.enterprisedlt.fabric.service.node

import java.io.IOException

import org.eclipse.jetty.websocket.api.{Session, WebSocketListener}
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage

/**
  * @author Andrew Pudovikov
  */

class ServiceWebSocketHandler extends WebSocketListener {


//    @OnWebSocketClose def onClose(statusCode: Int, reason: String): Unit = {
//        println("Close: statusCode=" + statusCode + ", reason=" + reason)
//    }
//
//    @OnWebSocketError def onError(t: Throwable): Unit = {
//        println("Error: " + t.getMessage)
//    }
//
//    @OnWebSocketConnect def onConnect(session: Session): Unit = {
//        println("Connect: " + session.getRemoteAddress.getAddress)
//        try
//            session.getRemote.sendString("Hello Web Browser")
//        catch {
//            case e: IOException =>
//                e.printStackTrace()
//        }
//    }
//
//    @OnWebSocketMessage def onMessage(message: String): Unit = {
//        println("Message: " + message)
//    }

    override def onWebSocketBinary(payload: Array[Byte], offset: Int, len: Int): Unit = {

    }

    override def onWebSocketText(message: String): Unit = {
        println("Message: " + message)
    }

    override def onWebSocketClose(statusCode: Int, reason: String): Unit = {
        println("Close: statusCode=" + statusCode + ", reason=" + reason)
    }

    override def onWebSocketConnect(session: Session): Unit = {
        println("Connect: " + session.getRemoteAddress.getAddress)
                try
                    session.getRemote.sendString("Hello Web Browser")
                catch {
                    case e: IOException =>
                        e.printStackTrace()
                }
    }

    override def onWebSocketError(cause: Throwable): Unit = {
        println("Error: " + cause.getMessage)
    }
}
