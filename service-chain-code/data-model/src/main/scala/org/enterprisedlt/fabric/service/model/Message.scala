package org.enterprisedlt.fabric.service.model

/**
  * @author Andrew Pudovikov
  */
case class Message(
  from: String,
  to: String,
  body: String,
  timestamp: Long
)
