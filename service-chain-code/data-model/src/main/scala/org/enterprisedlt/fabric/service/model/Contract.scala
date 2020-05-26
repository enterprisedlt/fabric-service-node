package org.enterprisedlt.fabric.service.model

case class Contract(
    founder: String,
    name: String,
    channel: String,
    lang: String,
    contractType: String,
    version: String,
    participants: Array[String],
    timestamp: Long
)
case class UpgradeContract(
    name: String,
    lang: String,
    chainCodeName: String,
    chainCodeVersion: String,
    founder: String,
    participants: Array[String],
    timestamp: Long
)