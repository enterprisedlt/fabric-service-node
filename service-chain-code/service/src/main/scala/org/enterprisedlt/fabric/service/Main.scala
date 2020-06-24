package org.enterprisedlt.fabric.service

import org.enterprisedlt.fabric.contract.{ContractBase, OperationContext}
import org.enterprisedlt.fabric.service.model.Organization
import org.enterprisedlt.fabric.service.operations._
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author Andrew Pudovikov
 */
object Main extends ContractBase
  with App
  with ContractInitialize
  with ServiceVersionOperations
  with OrganizationOperations
  with ServiceOperations
  with MessagingOperations
  with ContractOperations
  with ApplicationDistributivesOperations
  with ApplicationInviteOperations
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

    //    ================================================================================
    def getOwnOrganization: Either[String, Organization] = {
        val mspId = OperationContext.clientIdentity.mspId
        OperationContext.store.get[Organization](mspId).toRight(s"There isn't such org")
    }

}
