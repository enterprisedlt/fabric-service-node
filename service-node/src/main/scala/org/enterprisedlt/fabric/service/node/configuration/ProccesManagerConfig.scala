package org.enterprisedlt.fabric.service.node.configuration

/**
 * @author Andrew Pudovikov
 */

trait ProccesManagerConfig

case class DockerConfig
(
    dockerSocket: String,
    logFileSize: String,
    logMaxFiles: String
) extends ProccesManagerConfig