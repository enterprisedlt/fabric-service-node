package org.enterprisedlt.fabric.service

import org.enterprisedlt.fabric.contract.ContractBase
import org.enterprisedlt.fabric.service.operations.{ContractInitialize, MessagingOperations, OrganizationOperations, ServiceOperations, ServiceVersionOperations}
import org.slf4j.{Logger, LoggerFactory}

/**
  * @author pandelie
  */
object Main extends ContractBase
  with App
  with ContractInitialize
  with ServiceVersionOperations
  with OrganizationOperations
  with ServiceOperations
  with MessagingOperations
  //
{

    // start SHIM chain code
    start(args)

    // setup log levels
    LoggerFactory
      .getLogger(Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.INFO)
    LoggerFactory
      .getLogger(this.getClass.getPackage.getName)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.INFO)
    LoggerFactory
      .getLogger(classOf[ContractBase].getPackage.getName)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.INFO)

}
