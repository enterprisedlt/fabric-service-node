package org.enterprisedlt.fabric.service.node.rest

import java.io.File
import java.nio.file.{Files, Paths, StandardOpenOption}

import javax.servlet.MultipartConfigElement
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.http.MimeTypes
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.util.IO
import org.enterprisedlt.fabric.service.node.Util
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Maxim Fedin
 */
class UploadHandler(
    tmpLocation: String,
    maxFileSize: Int,
    maxRequestSize: Int,
    fileSizeThreshold: Int,
    endpoints: Array[FileUploadEndpoint]
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val multipartConfig: MultipartConfigElement = new MultipartConfigElement(tmpLocation, maxFileSize, maxRequestSize, fileSizeThreshold);

    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        endpoints
          .find(_.uri == request.getPathInfo)
          .foreach { endpoint =>
              if (request.getMethod == "POST") {
                  request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfig)
                  processRequest(endpoint, request, response)
                  baseRequest.setHandled(true)
              }
          }
    }

    private def processRequest(
        endpoint: FileUploadEndpoint,
        request: HttpServletRequest, response: HttpServletResponse
    ): Unit = {
        Util.mkDirs(endpoint.fileDir)
        val outputDir = Paths.get(endpoint.fileDir)
        Try {
            request.getParts.forEach { part =>
                Option(part.getSubmittedFileName).foreach { filename =>
                    logger.debug(s"Got Part ${part.getName} with size = ${part.getSize}, contentType = ${part.getContentType}, submittedFileName $filename")
                    val outputFile = outputDir.resolve(filename)
                    val is = part.getInputStream
                    val os = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                    IO.copy(is, os)
                    logger.debug(s"Saved Part ${part.getName} to ${outputFile.toString}")
                    is.close()
                    os.close()
                }
            }
        }.toEither match {
            case Right(_) =>
                response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString())
                response.setCharacterEncoding("utf-8")
                response.setStatus(HttpServletResponse.SC_OK)

            case Left(ex) =>
                val msg = s"Unable to process file: ${ex.getMessage}"
                logger.warn(msg, ex)
                response.getWriter.println(msg)
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }
}
