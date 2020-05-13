package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Alexey Polubelov
  */
case class PeerConfig(
    box: String,
    name: String,
    port: Int,
    couchDB: CouchDBConfig
)

