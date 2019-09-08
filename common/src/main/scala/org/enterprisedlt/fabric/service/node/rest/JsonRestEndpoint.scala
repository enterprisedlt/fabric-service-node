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
class JsonRestEndpoint(
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

    private val GetBindings = endpoints.flatMap { spec =>
        spec
          .getClass
          .getDeclaredMethods
          .filter(_.isAnnotationPresent(classOf[Get]))
          .map { m =>
              (
                m.getAnnotation(classOf[Get]).value(),
                createHandler(spec, m, mkGetParams)
              )
          }
    }.toMap

    private val PostBindings = endpoints.flatMap { spec =>
        spec
          .getClass
          .getDeclaredMethods
          .filter(_.isAnnotationPresent(classOf[Post]))
          .map { m =>
              (
                m.getAnnotation(classOf[Post]).value(),
                createHandler(spec, m, mkPostParams)
              )
          }
    }.toMap

    def start(): Unit = server.start()

    def stop(): Unit = server.stop()

    private def getCodec: Gson = new GsonBuilder().setPrettyPrinting().create()

    type HandleFunction = (HttpServletRequest, HttpServletResponse) => Unit
    type ExtractParametersFunction = (Method, HttpServletRequest, Gson) => Seq[AnyRef]

    private def createHandler(o: AnyRef, m: Method, parametersFunction: ExtractParametersFunction): (HttpServletRequest, HttpServletResponse) => Unit = {
        val eClazz = classOf[Either[String, Any]]
        m.getReturnType match {
            case x if x.isAssignableFrom(eClazz) =>
                (request, response) =>
                    try {
                        val codec = getCodec
                        val params: Seq[AnyRef] = parametersFunction(m, request, codec)
                        m.invoke(o, params: _*) match {
                            case Right(value) =>
                                if (response.getContentType == null) { // if content type already set do NOT overwrite
                                    response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                }
                                Option(value).filterNot(_.isInstanceOf[Unit]).foreach(v => response.getWriter.print(codec.toJson(v)))
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

    private def mkPostParams(m: Method, request: HttpServletRequest, codec: Gson): Seq[AnyRef] = {
        m.getParameters.headOption.map { p =>
            codec.fromJson(request.getReader, p.getType).asInstanceOf[AnyRef]
        }.toSeq
    }

    private def mkGetParams(m: Method, request: HttpServletRequest, codec: Gson): Seq[AnyRef] = {
        m.getParameters.map { p =>
            val v = Option(request.getParameter(p.getName)).getOrElse(throw new Exception("mandatory parameter absent"))
            codec.fromJson(v, p.getType).asInstanceOf[AnyRef]
        }
    }

    private object EndpointHandler extends AbstractHandler {

        override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
            RestEndpointContext.set(RestEndpointContext(request, response))
            request.getMethod match {
                case "GET" =>
                    processRequest(GetBindings, request, response)

                case "POST" =>
                    processRequest(PostBindings, request, response)

                case methodName =>
                    response.getWriter.println(s"Unsupported method type $methodName")
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)

            }
            RestEndpointContext.clean()
            baseRequest.setHandled(true)
        }

        private def processRequest(
            bindings: Map[String, HandleFunction],
            request: HttpServletRequest, response: HttpServletResponse
        ): Unit = {
            bindings.get(request.getPathInfo) match {
                case Some(f) =>
                    f(request, response)
                case None =>
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND)
            }
        }
    }

}
