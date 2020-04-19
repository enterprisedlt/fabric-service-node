package org.enterprisedlt.fabric.service.node.rest

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

/**
 * @author Alexey Polubelov
 */
case class RestEndpointContext(
    request: HttpServletRequest,
    response: HttpServletResponse
)

object RestEndpointContext {
    private val contextHolder = new ThreadLocal[RestEndpointContext]()

    def get: Option[RestEndpointContext] = Option(contextHolder.get())

    def set(context: RestEndpointContext): Unit =
        contextHolder.set(context)

    def clean(): Unit = set(null)
}