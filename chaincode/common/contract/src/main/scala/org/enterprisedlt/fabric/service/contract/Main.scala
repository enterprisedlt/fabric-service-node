package org.enterprisedlt.fabric.service.contract

import org.enterprisedlt.fabric.service.contract.contract._
import com.github.apolubelov.fabric.contract.{ContractBase, ContractContext}
import org.enterprisedlt.fabric.service.contract.model.{Organisation, OrganizationsOrdering}
import org.slf4j.{Logger, LoggerFactory}


/**
  * @author pandelie
  */
object Main extends ContractBase
  with App
  with ContractInitialize
  with OrgMethods
{

    // start SHIM chain code
    start(args)

    // setup log levels
    LoggerFactory
      .getLogger(Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.TRACE)
    LoggerFactory
      .getLogger(this.getClass.getPackage.getName)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.TRACE)
    LoggerFactory
      .getLogger(classOf[ContractBase].getPackage.getName)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.TRACE)

    // Utility's

    def findAllSharedCollections(context: ContractContext): Seq[String] = {
        val orgs = context.store.list[Organisation].map(_.value).toSeq.sorted(OrganizationsOrdering).map(e => e.code)
        orgs.foldLeft((List.empty[String], List.empty[String])) {
            case ((ogrsList, collectionsList), org) =>
                (
                  ogrsList :+ org,
                  collectionsList ++ delta(ogrsList, org)
                )
        }._2
    }

    def findSharedCollections(context: ContractContext, org: String): Seq[String] = {
        findAllSharedCollections(context)
          .filter(e => e.endsWith(s"-$org") || e.startsWith(s"$org-"))
    }


    def delta(orgList: Iterable[String], newOrg: String): Iterable[String] = {
        orgList.map(e => s"$newOrg-$e")
    }

    def getSharedCollection(context: ContractContext, org1: String, org2: String): Either[String, String] =
        context.store.get[Organisation](org1)
          .toRight(s"Unknown organization $org1")
          .flatMap { o1 =>
              context.store.get[Organisation](org2)
                .toRight(s"Unknown organization $org2")
                .map { o2 =>
                    if (OrganizationsOrdering.compare(o1, o2) > 0) {
                        s"${o2.code}-${o1.code}"
                    } else {
                        s"${o1.code}-${o2.code}"
                    }
                }
          }

}
