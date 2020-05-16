package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Alexey Polubelov
  */
case class OrganizationConfig(
    name: String,
    domain: String,
    location: String,
    state: String,
    country: String,
    certificateDuration: String
)
