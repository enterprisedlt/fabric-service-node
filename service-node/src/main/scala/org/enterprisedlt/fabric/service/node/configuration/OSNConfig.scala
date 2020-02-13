package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Alexey Polubelov
  */

trait OSNConfig {
 def name: String
    def fullName(fullOrgName:String):String
    def port: Int
}

case class DomesticOSNConfig(
    name: String,
    port: Int
)extends  OSNConfig {
    override def fullName(fullOrgName: String): String = s"$name.$fullOrgName"
}

case class ExternalOSNConfig(
    host: String,
    port: Int
)extends  OSNConfig {
    override def name: String = host.substring(host.indexOf("."))
    override def fullName(fullOrgName: String): String = host
}