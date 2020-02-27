package org.enterprisedlt.fabric.service.node.util

import scala.scalajs.js.Date

/**
  * @author Maxim Fedin
  */
object Util {
    def mkDate(timestamp: Long): String = new Date(timestamp * 1000L).toLocaleDateString()

}
