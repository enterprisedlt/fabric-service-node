package org.enterprisedlt.fabric.service.model

case class Contract(
    name: String,
    lang: String,
    chainCodeName: String,
    chainCodeVersion: String,
    founder: String,
    participants: Array[String],
    timestamp: Long
)