package org.enterprisedlt.fabric.service.node.util

import scala.scalajs.js.Date

/**
  * @author Maxim Fedin
  */
object Util {
    def mkDate(timestamp: Long): String = new Date(timestamp).formatted("yyyy-MM-dd HH:mm:ss z")

}
