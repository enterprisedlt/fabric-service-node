package org.enterprisedlt.fabric.service.contract.contract

import org.enterprisedlt.fabric.service.contract.Main
import org.enterprisedlt.fabric.service.contract.model.{CCVersion, Organisation}
import com.github.apolubelov.fabric.contract.annotation.ContractOperation
import com.github.apolubelov.fabric.contract.{ContractContext, ContractResponse, Error, Success}

/**
  * @author pandelie
  */
trait OrgMethods {
    self: Main.type =>

    @ContractOperation
    def addOrganisation(context: ContractContext, code: String, name: String, orgNumber: Long): ContractResponse = {
        if (!code.isEmpty) {
            context.store.put[Organisation](code, Organisation(code, name, orgNumber))
            Success(s"org $name was stored")
        }
        else {
            Error(s"There isn't crucial key to store the org $name")
        }
    }

    @ContractOperation
    def listOrganisations(context: ContractContext): ContractResponse = {
        Success(context.store.list[Organisation].map(_.value).toArray)
    }


    @ContractOperation
    def getOrganisation(context: ContractContext, orgKey: String): ContractResponse = {
        context.store.get[Organisation](orgKey)
          .map(Success(_))
          .getOrElse(Error(s"There is no org with key $orgKey"))
    }

    @ContractOperation
    def setCCVersion(context: ContractContext, ccName: String, ccVer: String, networkVer: String): ContractResponse = {
        context.store.put[CCVersion](ccName, CCVersion(ccVer, networkVer))
        Success()
    }

    @ContractOperation
    def getCCVersion(context: ContractContext, ccName: String): ContractResponse = {
        context.store.get[CCVersion](ccName)
          .map(Success(_))
          .getOrElse(Error(s"There is no chaincode entity"))
    }

    @ContractOperation
    def delOrganisation(context: ContractContext, orgKey: String): ContractResponse = {
        context.store.get[Organisation](orgKey) match {
            case Some(asset) =>
                context.store.del[Organisation](orgKey)
                Success()

            case _ => Error(s"No Account with key $orgKey exist")
        }

    }

}
