package org.enterprisedlt.fabric.service.contract.contract

import com.github.apolubelov.fabric.contract.annotation.ContractOperation
import com.github.apolubelov.fabric.contract.{ContractContext, ContractResponse, Error, Success}
import org.enterprisedlt.fabric.service.contract.Main
import org.enterprisedlt.fabric.service.contract.model.{CCVersion, Organisation}

/**
  * @author pandelie
  */
trait OrgMethods {
    self: Main.type =>

    @ContractOperation
    def addOrganisation(context: ContractContext, code: String, name: String): ContractResponse = {
        if (!code.isEmpty) {
            context.store.put[Organisation](code, Organisation(code, name))
            Success(s"Organization $name was stored")
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
    def getOrganisation(context: ContractContext, organizationCode: String): ContractResponse = {
        context.store.get[Organisation](organizationCode)
          .map(Success(_))
          .getOrElse(Error(s"There is no organization with code $organizationCode"))
    }

    @ContractOperation
    def setCCVersion(context: ContractContext, ccVer: String, networkVer: String): ContractResponse = {
        context.store.put[CCVersion]("chaincode", CCVersion(ccVer, networkVer))
        Success()
    }

    @ContractOperation
    def getCCVersion(context: ContractContext): ContractResponse = {
        context.store.get[CCVersion]("chaincode")
          .map(Success(_))
          .getOrElse(Error(s"There is no chain code entity"))
    }

    @ContractOperation
    def delOrganisation(context: ContractContext, organizationCode: String): ContractResponse = {
        context.store.get[Organisation](organizationCode) match {
            case Some(asset) =>
                context.store.del[Organisation](organizationCode)
                Success()

            case _ => Error(s"No organization with code $organizationCode exist")
        }

    }

}
