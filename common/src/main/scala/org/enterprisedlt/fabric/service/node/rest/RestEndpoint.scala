package org.enterprisedlt.fabric.service.node.rest

import java.lang.reflect.Method

import com.google.gson.{Gson, GsonBuilder}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Request, Server}
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class RestEndpoint(
    port: Int,
    endpoints: AnyRef*
) {
    private val logger = LoggerFactory.getLogger(getClass)
    private val server: Server = createServer

    private def createServer: Server = {
        val server = new Server(port)
        server.setHandler(EndpointHandler)
        server
    }

    private val bindings = endpoints.flatMap { spec =>
        spec
          .getClass
          .getDeclaredMethods
          .filter(_.isAnnotationPresent(classOf[Get]))
          .map { m =>
              (
                m.getAnnotation(classOf[Get]).value(),
                createHandler(spec, m)
              )
          }
    }.toMap

    def start(): Unit = server.start()

    def stop(): Unit = server.stop()

    private def getCodec: Gson = new GsonBuilder().setPrettyPrinting().create()

    private def createHandler(o: AnyRef, m: Method): (HttpServletRequest, HttpServletResponse) => Unit = {
        val eClazz = classOf[Either[String, Any]]
        m.getReturnType match {
            case x if x.isAssignableFrom(eClazz) =>
                (request, response) =>
                    try {
                        val codec = getCodec
                        val params = m.getParameters.map { p =>
                            val v = Option(request.getParameter(p.getName)).getOrElse(throw new Exception("mandatory parameter absent"))
                            codec.fromJson(v, p.getType).asInstanceOf[AnyRef]
                        }
                        m.invoke(o, params: _*) match {
                            case Right(value) =>
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.getWriter.print(codec.toJson(value))
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(errorMsg) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                                response.getWriter.print(errorMsg)

                            case _ =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

                        }
                    } catch {
                        case ex: Throwable =>
                            logger.error(s"Exception during execution of ${m.getName}", ex)
                            response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                            response.getWriter.println(ex.getMessage)
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    }

            case other =>
                throw new Exception(s"Unsupported return type ${other.getCanonicalName} the only supported return type is Either[String, Any+]")
        }
    }

    private object EndpointHandler extends AbstractHandler {

        override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
            val handler = bindings.get(request.getPathInfo)
            if (handler.isDefined) {
                response.setStatus(HttpServletResponse.SC_OK)
                handler.foreach(_ (request, response))
                response.getWriter.flush()
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND)
            }
            baseRequest.setHandled(true)
        }
    }

}
