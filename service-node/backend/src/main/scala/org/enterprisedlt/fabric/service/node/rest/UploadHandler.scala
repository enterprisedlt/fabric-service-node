package org.enterprisedlt.fabric.service.node.rest

import java.lang.reflect.Method
import java.nio.file.{Files, Path, StandardOpenOption}

import javax.servlet.MultipartConfigElement
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.http.MimeTypes
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.util.IO
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */


class UploadHandler(
    createCodec: () => ServerCodec,
    endpoints: AnyRef*
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)
    val location: String = "/location"; // the directory location where files will be stored
    val maxFileSize: Int = 100 * 1024 * 1024; // the maximum size allowed for uploaded files
    val maxRequestSize: Int = 100 * 1024 * 1024; // the maximum size allowed for multipart/form-data requests
    val fileSizeThreshold: Int = 1024; // the size threshold after which files will be written to disk
    val multipartConfig: MultipartConfigElement = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);


    private val Bindings: Map[String, (HttpServletRequest, HttpServletResponse) => Unit] = endpoints.flatMap { spec =>
        scanMethods(spec.getClass)
          .map { m =>
              (
                m.getAnnotation(classOf[Post]).value(),
                createHandler(spec, m, mkFileUploadParams)
              )
          }
    }.toMap

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

    private def mkFileUploadParams(m: Method, request: HttpServletRequest, codec: ServerCodec): Seq[AnyRef] = {
        m.getParameters.map { p =>
            codec.readParameter(request.getReader, p.getType).asInstanceOf[AnyRef]
        }.toSeq
    }


    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        RestEndpointContext.set(RestEndpointContext(request, response))
        request.getMethod match {

            case "POST" =>
                request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfig)
                processRequest(Bindings, request, response)

            case methodName =>
                response.getWriter.println(s"Unsupported method type $methodName")
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED)

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


    private def processParts(request: HttpServletRequest, response: HttpServletResponse, outputDir: Path): Unit = {
        val parts = request.getParts.iterator()
        while (!parts.hasNext) {
            val part = parts.next()
            val filename = part.getSubmittedFileName
            logger.info(s"Got Part ${part.getName} with size = ${part.getSize}, contentType = ${part.getContentType}, submittedFileName ${filename}")
            if (filename.nonEmpty) {
                val outputFile = outputDir.resolve(filename)
                val is = part.getInputStream
                val os = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                try {
                    IO.copy(is, os)
                    logger.info(s"Saved Part ${part.getName} to ${outputFile.toString}")
                }
            }
        }
        response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString())
        response.setCharacterEncoding("utf-8")

    }
}
