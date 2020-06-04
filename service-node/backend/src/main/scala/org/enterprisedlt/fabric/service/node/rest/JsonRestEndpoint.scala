package org.enterprisedlt.fabric.service.node.rest

import java.lang.reflect.Method

import javax.servlet.MultipartConfigElement
import javax.servlet.http.{HttpServletRequest, HttpServletResponse, Part}
import org.eclipse.jetty.http.MimeTypes
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.node.UnitsHelper._
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */
class JsonRestEndpoint(
    createCodec: () => ServerCodec,
    endpoints: AnyRef*
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val tmpLocation = "/opt/profile/work/upload-parts" // the directory location where files will be stored
    private val maxFileSize = 500 MByte // the maximum size allowed for uploaded files
    private val maxRequestSize = 500 MByte // the maximum size allowed for multipart/form-data requests
    private val fileSizeThreshold = 1 MByte // the size threshold after which files will be written to disk
    private val multipartConfig: MultipartConfigElement = new MultipartConfigElement(tmpLocation, maxFileSize, maxRequestSize, fileSizeThreshold)

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

    private val PostBindings = (
      endpoints.flatMap { spec =>
          scanMethods(spec.getClass)
            .filter(_.isAnnotationPresent(classOf[Post]))
            .map { m =>
                (
                  m.getAnnotation(classOf[Post]).value(),
                  createHandler(spec, m, mkPostParams)
                )
            }
      } ++
        endpoints.flatMap { spec =>
            scanMethods(spec.getClass)
              .filter(_.isAnnotationPresent(classOf[PostMultipart]))
              .map { m =>
                  (
                    m.getAnnotation(classOf[PostMultipart]).value(),
                    createUploadHandler(spec, m)
                  )
              }
        }
      ).toMap

    private def scanMethods(c: Class[_]): Array[Method] = {
        c.getMethods ++
          c.getInterfaces.flatMap(scanMethods) ++
          Option(c.getSuperclass).map(scanMethods).getOrElse(Array.empty)
    }

    type HandleFunction = (HttpServletRequest, HttpServletResponse) => Unit
    type ExtractParametersFunction = (Method, HttpServletRequest, ServerCodec) => Seq[AnyRef]

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
                                  .foreach(v => codec.writeResult(v, response.getOutputStream))

                                response.setContentType(
                                    Option(m.getAnnotation(classOf[ResponseContentType]))
                                      .map(_.value())
                                      .getOrElse(MimeTypes.Type.APPLICATION_JSON.asString())
                                )
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(errorMsg) =>
                                response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString())
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                                codec.writeResult(errorMsg, response.getOutputStream)
                        }
                    } catch {
                        case ex: Throwable =>
                            logger.error(s"Exception during execution of ${m.getName}", ex)
                            response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString())
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


    private def createUploadHandler(o: AnyRef, m: Method): (HttpServletRequest, HttpServletResponse) => Unit = {
        logger.info(s"Creating handler for: ${m.getName}")
        val returnTypeClazz = classOf[Either[String, Any]]
        val parameterClazz = classOf[java.util.Collection[Part]]
        m.getParameterTypes match {
            case x if x.length != 1 =>
                throw new Exception(s"There should be only one supported parameter with type java.util.Collection[Part]")
            case x if x.head == parameterClazz =>
                m.getReturnType match {
                    case x if x.isAssignableFrom(returnTypeClazz) =>
                        val f: (HttpServletRequest, HttpServletResponse) => Unit = { (request, response) =>
                            try {
                                request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfig)
                                val params: Seq[AnyRef] = m
                                  .getParameters
                                  .headOption
                                  .map { _ => request.getParts }
                                  .toSeq
                                m.invoke(o, params: _*) match {
                                    case Right(_) =>
                                        response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString())
                                        response.setCharacterEncoding("utf-8")
                                        response.setStatus(HttpServletResponse.SC_OK)

                                    case Left(ex) =>
                                        val msg = s"Unable to process file: ${ex}"
                                        logger.warn(msg, ex)
                                        response.getWriter.println(msg)
                                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                                }
                            } catch {
                                case ex: Throwable =>
                                    logger.error(s"Exception during execution of ${m.getName}", ex)
                                    response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString())
                                    response.getWriter.println(ex.getMessage)
                                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                            }
                        }
                        logger.info(s"Added endpoint for ${m.getName}")
                        f
                    case other =>
                        throw new Exception(s"Unsupported return type ${other.getCanonicalName} the only supported return type is Either[String, Any+]")
                }
            case other =>
                throw new Exception(s"Unsupported parameter type ${other.head.getCanonicalName} the only supported parameter type is java.util.Collection[Part]")

        }
    }


    private def mkPostParams(m: Method, request: HttpServletRequest, codec: ServerCodec): Seq[AnyRef] = {
        m.getParameters.headOption.map { p =>
            codec.readParameter(request.getReader, p.getType).asInstanceOf[AnyRef]
        }.toSeq
    }

    private def mkGetParams(m: Method, request: HttpServletRequest, codec: ServerCodec): Seq[AnyRef] = {
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
