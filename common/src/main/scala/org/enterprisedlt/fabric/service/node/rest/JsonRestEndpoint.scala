package org.enterprisedlt.fabric.service.node.rest

import java.io.{Reader, Writer}
import java.lang.reflect.Method

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class JsonRestEndpoint(
    createCodec: () => Codec,
    endpoints: AnyRef*
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(getClass)

    private val GetBindings = endpoints.flatMap { spec =>
        scanMethods(spec.getClass)
          .filter(_.isAnnotationPresent(classOf[Get]))
          .map { m =>
              (
                m.getAnnotation(classOf[Get]).value(),
                createHandler(spec, m, mkGetParams)
              )
          }
    }.toMap

    private val PostBindings = endpoints.flatMap { spec =>
        scanMethods(spec.getClass)
          .filter(_.isAnnotationPresent(classOf[Post]))
          .map { m =>
              (
                m.getAnnotation(classOf[Post]).value(),
                createHandler(spec, m, mkPostParams)
              )
          }
    }.toMap

    private def scanMethods(c: Class[_]): Array[Method] = {
        c.getMethods ++
          c.getInterfaces.flatMap(scanMethods) ++
          Option(c.getSuperclass).map(scanMethods).getOrElse(Array.empty)
    }

    type HandleFunction = (HttpServletRequest, HttpServletResponse) => Unit
    type ExtractParametersFunction = (Method, HttpServletRequest, Codec) => Seq[AnyRef]

    private def createHandler(o: AnyRef, m: Method, parametersFunction: ExtractParametersFunction): (HttpServletRequest, HttpServletResponse) => Unit = {
        logger.info(s"Creating handler for: ${m.getName}")
        val eClazz = classOf[Either[String, Any]]
        m.getReturnType match {
            case x if x.isAssignableFrom(eClazz) =>
                val f: (HttpServletRequest, HttpServletResponse) => Unit = { (request, response) =>
                    try {
                        val codec = createCodec()
                        val params: Seq[AnyRef] = parametersFunction(m, request, codec)
                        m.invoke(o, params: _*) match {
                            case Right(value) =>
                                Option(value)
                                  .filterNot(_.isInstanceOf[Unit])
                                  .foreach(v => codec.writeResult(v, response.getWriter))

                                response.setContentType(
                                    Option(m.getAnnotation(classOf[ResponseContentType]))
                                      .map(_.value())
                                      .getOrElse(ContentType.APPLICATION_JSON.getMimeType)
                                )
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(errorMsg) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                                codec.writeResult(errorMsg, response.getWriter)
                        }
                    } catch {
                        case ex: Throwable =>
                            logger.error(s"Exception during execution of ${m.getName}", ex)
                            response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                            response.getWriter.println(ex.getMessage)
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    }
                }
                logger.info(s"Added endpoint for ${m.getName}")
                f
            case other =>
                throw new Exception(s"Unsupported return type ${other.getCanonicalName} the only supported return type is Either[String, Any+]")
        }
    }

    private def mkPostParams(m: Method, request: HttpServletRequest, codec: Codec): Seq[AnyRef] = {
        m.getParameters.headOption.map { p =>
            codec.readParameter(request.getReader, p.getType).asInstanceOf[AnyRef]
        }.toSeq
    }

    private def mkGetParams(m: Method, request: HttpServletRequest, codec: Codec): Seq[AnyRef] = {
        m.getParameters.map { p =>
            val v = Option(request.getParameter(p.getName)).getOrElse(throw new Exception("mandatory parameter absent"))
            codec.readParameter(v, p.getType).asInstanceOf[AnyRef]
        }
    }

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

trait Codec {
    def writeResult(x: Any, stream: Writer)

    def readParameter[T](p: String, pType: Class[T]): T

    def readParameter[T](p: Reader, pType: Class[T]): T
}
