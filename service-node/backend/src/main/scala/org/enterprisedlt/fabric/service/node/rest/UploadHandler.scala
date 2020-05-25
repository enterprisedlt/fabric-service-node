package org.enterprisedlt.fabric.service.node.rest

import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import javax.servlet.MultipartConfigElement
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.http.MimeTypes
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.util.IO
import org.enterprisedlt.fabric.service.node.Util
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */


class UploadHandler(
    endpoints: Array[FileUploadEndpoint],
    createCodec: () => ServerCodec,
) extends AbstractHandler {

    private val logger = LoggerFactory.getLogger(this.getClass)

    val tmpLocation: String = "/location" // the directory location where files will be stored
    val maxFileSize: Int = 100 * 1024 * 1024 // the maximum size allowed for uploaded files
    val maxRequestSize: Int = 100 * 1024 * 1024 // the maximum size allowed for multipart/form-data requests
    val fileSizeThreshold: Int = 1024 // the size threshold after which files will be written to disk
    val multipartConfig: MultipartConfigElement = new MultipartConfigElement(tmpLocation, maxFileSize, maxRequestSize, fileSizeThreshold);


    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        RestEndpointContext.set(RestEndpointContext(request, response))
        request.getMethod match {
            case "POST" =>
                request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfig)
                processRequest(endpoints, request, response)

            case methodName =>
                response.getWriter.println(s"Unsupported method type $methodName")
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED)

        }
        RestEndpointContext.clean()
        baseRequest.setHandled(true)
    }

    private def processRequest(
        endpoints: Array[FileUploadEndpoint],
        request: HttpServletRequest, response: HttpServletResponse
    ): Unit = {
        if (endpoints.exists(_.uri == request.getPathInfo)) {
            val storeLocation = endpoints
              .filter(_.uri == request.getPathInfo)
              .head
              .location
            Util.ensureDirExists(storeLocation)
            processParts(request, response, Paths.get(storeLocation))
        }
        else
            response.setStatus(HttpServletResponse.SC_NOT_FOUND)
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
