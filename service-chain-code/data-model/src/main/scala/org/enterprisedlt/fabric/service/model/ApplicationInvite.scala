package org.enterprisedlt.fabric.service.model

/**
 * @author Maxim Fedin
 */
case class ApplicationInvite(
    founder: String,
    name: String,
    channel: String,
    applicationType: String,
    version: String,
    participants: Array[String],
    timestamp: Long
)
