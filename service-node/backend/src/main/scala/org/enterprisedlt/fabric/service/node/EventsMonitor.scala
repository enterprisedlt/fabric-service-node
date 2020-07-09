package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets

import com.google.gson.GsonBuilder
import org.enterprisedlt.fabric.service.model.{ApplicationDistributive, ApplicationInvite, Contract}
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.FabricServiceStateHolder.StateChangeFunction
import org.enterprisedlt.fabric.service.node.model.{ApplicationDescriptor, FabricServiceStateFull, FabricServiceStateHolder}
import org.enterprisedlt.fabric.service.node.shared._
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

/**
 * @author Alexey Polubelov
 * @author Maxim Fedin
 */
class EventsMonitor(
    eventPullingInterval: Long, //Ms
    networkManager: FabricNetworkManager
) extends Thread("EventsMonitor") {
    @volatile private var working = true
    private val logger: Logger = LoggerFactory.getLogger(this.getClass)
    private var delay: Long = -1L

    val customComponentsPath: File = new File(s"/opt/profile/components/").getAbsoluteFile
    if (!customComponentsPath.exists()) customComponentsPath.mkdirs()

    def checkUpdateState(): Unit = FabricServiceStateHolder.updateStateFullOption(
        FabricServiceStateHolder.compose(
            updateApplicationInvitations(),
            updateContractInvitations(),
            updateCustomComponentDescriptors(),
            updateContractDescriptors(),
            updateApplications()
        )
    )

    def updateApplicationInvitations(): StateChangeFunction = { current: FabricServiceStateFull =>
        val applicationInvs = for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listApplicationInvites")
            contracts <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            applicationInvitations <- Util.try2EitherWithLogging((new GsonBuilder).create().fromJson(contracts, classOf[Array[ApplicationInvite]]))
        } yield {
            applicationInvitations.map { application =>
                ApplicationInvitation(
                    initiator = application.founder,
                    name = application.name,
                    applicationType = application.applicationType,
                    applicationVersion = application.version,
                    participants = application.participants,

                )
            }
        }
        applicationInvs match {
            case Right(applicationInvs) if current.events.applicationInvitations.length != applicationInvs.length =>
                Option(current.copy(events = current.events.copy(applicationInvitations = applicationInvs)))

            case Left(msg) =>
                logger.warn(msg)
                None

            case _ => None
        }

    }

    def updateContractInvitations(): StateChangeFunction = { current: FabricServiceStateFull =>
        val contractInvs = for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
            contracts <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            contractInvitations <- Util.try2EitherWithLogging((new GsonBuilder).create().fromJson(contracts, classOf[Array[Contract]]))
        } yield {
            contractInvitations.map {
                contract =>
                    ContractInvitation(
                        initiator = contract.founder,
                        name = contract.name,
                        chainCodeName = contract.contractType,
                        chainCodeVersion = contract.version,
                        participants = contract.participants,
                    )
            }
        }
        contractInvs match {
            case Right(contractInvs) if current.events.contractInvitations.length != contractInvs.length =>
                Option(current.copy(events = current.events.copy(contractInvitations = contractInvs)))

            case Left(msg) =>
                logger.warn(msg)
                None

            case _ => None
        }
    }

    def updateContractDescriptors(): StateChangeFunction = { current: FabricServiceStateFull =>
        Try(getContractDescriptors)
          .toEither
          .left.map(_.getMessage) match {
            case Right(descriptors) if current.contractDescriptors.length != descriptors.length =>
                Option(current.copy(contractDescriptors = descriptors))

            case Left(msg) =>
                logger.warn(msg)
                None

            case _ => None
        }
    }

    def updateCustomComponentDescriptors(): StateChangeFunction = { current: FabricServiceStateFull =>
        Try(getCustomComponentDescriptors)
          .toEither
          .left.map(_.getMessage) match {
            case Right(descriptors) if current.customComponentDescriptors.length != descriptors.length =>
                Option(current.copy(customComponentDescriptors = descriptors))

            case Left(msg) =>
                logger.warn(msg)
                None

            case _ => None
        }
    }


    def updateApplications(): StateChangeFunction = { current: FabricServiceStateFull =>
        val apps = for {
            applicationDescriptors <- Util.try2EitherWithLogging(getApplicationDescriptors)
            applications <- getApplications
        } yield {
            applicationDescriptors.map { applicationDescriptor =>
                val status = if (applications.exists(_.applicationType == applicationDescriptor.applicationType)) "Published" else "Downloaded"
                logger.info(s"status $status for ${applicationDescriptor.applicationType}")
                ApplicationState(
                    applicationName = applicationDescriptor.applicationName,
                    applicationType = applicationDescriptor.applicationType,
                    status = status,
                    applicationRoles = applicationDescriptor.roles,
                    properties = applicationDescriptor.properties,
                    contracts = applicationDescriptor.contracts,
                    components = applicationDescriptor.components
                )
            } ++ applications
              .filterNot { application =>
                  applicationDescriptors.exists(_.applicationName == application.applicationName)
              }.map { application =>
                ApplicationState(
                    applicationName = application.applicationName,
                    applicationType = application.applicationType,
                    status = "Not Downloaded",
                    distributorAddress = application.componentsDistributorAddress
                )
            }
        }
        apps match {
            case Right(apps) if current.applicationState.length != apps.length || current.applicationState.exists(app =>
                !apps.exists(application => application.applicationType == app.applicationType && application.status == app.status))  =>
                val applicationDescriptors = getApplicationDescriptors
                applicationDescriptors.foreach {
                    applicationDescriptor =>
                        applicationDescriptor.contracts.foreach {
                            chaincode =>
                                Util.extractFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.applicationType}.tgz", s"chain-code/${chaincode.contractType}.json", "/opt/profile")
                                Util.extractFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.applicationType}.tgz", s"chain-code/${chaincode.contractType}.tgz", "/opt/profile")
                        }
                        applicationDescriptor.components.foreach {
                            component =>
                                Util.extractFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.applicationType}.tgz", s"components/${component.componentType}.tgz", "/opt/profile")
                        }
                }
                Option(
                    current.copy(
                        applicationState = apps,
                        applications = applicationDescriptors)
                )

            case Left(msg) =>
                logger.warn(msg)
                None

            case _ => None
        }
    }

    //    ================================================================================
    private def getApplications: Either[String, Array[ApplicationDistributive]] = {
        for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listApplicationDistributives")
            applications <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            applications <- Util.try2EitherWithLogging((new GsonBuilder).create().fromJson(applications, classOf[Array[ApplicationDistributive]]))
        } yield applications
    }

    private def getApplicationDescriptors: Array[ApplicationDescriptor] = {
        val applicationsPath = "/opt/profile/application-distributives"
        Util.mkDirs(applicationsPath)
        new File(applicationsPath)
          .getAbsoluteFile
          .listFiles()
          .filter(_.getName.endsWith(".tgz"))
          .flatMap { file =>
              logger.info(s"file is ${file.getName}")
              val filename = file.getName.split('.')(0)
              Util.readFromTarAs[ApplicationDescriptor](file.toPath, s"$filename.json")
                .map(_.copy(applicationType = filename))
                .map { applicationDescriptor =>
                    val applicationDescriptorJson = Util.codec.toJson(applicationDescriptor)
                    val out = new FileOutputStream(s"/opt/profile/applications/$filename.json")
                    try {
                        val s = applicationDescriptorJson.getBytes(StandardCharsets.UTF_8)
                        out.write(s)
                        out.flush()
                    }
                    finally out.close()
                    applicationDescriptor
                }
          }
    }

    private def getContractDescriptors: Array[ContractDescriptor] = {
        val applicationsPath = "/opt/profile/chain-code"
        Util.mkDirs(applicationsPath)
        new File(applicationsPath)
          .getAbsoluteFile
          .listFiles()
          .filter(_.getName.endsWith(".tgz"))
          .flatMap { file =>
              val filename = file.getName.substring(0,file.getName.length - 4)
              Util.readFromTarAs[ContractDescriptor](file.toPath, s"$filename.json")
          }
    }

    private def getCustomComponentDescriptors: Array[CustomComponentDescriptor] = {
        customComponentsPath
          .listFiles()
          .filter(_.getName.endsWith(".tgz"))
          .flatMap { file =>
              logger.info(s"file is ${file.getName}")
              val filename = s"${file.getName.split('.')(0)}.json"
              Util.readFromTarAs[CustomComponentDescriptor](file.toPath, filename)
          }
    }

    override def run(): Unit = {
        logger.info("Events monitor started")
        while (working) {
            try {
                if (delay > 0) {
                    Thread.sleep(delay)
                }
                val start = System.currentTimeMillis()
                checkUpdateState()
                val duration = System.currentTimeMillis() - start
                logger.info(s"Pulling done in $duration ms")
                delay = eventPullingInterval - duration
            }
            catch {
                case _: InterruptedException =>
                    logger.debug("Interrupted, shutting down...")
                    working = false

                case exception: Throwable =>
                    logger.warn("Unable to pull events:", exception)
                    delay = eventPullingInterval
            }
        }
        logger.info("Events monitor stopped")
    }

    def startup(): EventsMonitor = {
        this.start()
        this
    }

    def shutdown(): Unit = {
        working = false
        this.join()
    }
}
