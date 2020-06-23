package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.google.gson.GsonBuilder
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.eclipse.jetty.util.IO
import org.enterprisedlt.fabric.service.model.{ApplicationInvite, ApplicationDistributive, Contract}
import org.enterprisedlt.fabric.service.node.Util.withResources
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.FabricServiceStateHolder.StateChangeFunction
import org.enterprisedlt.fabric.service.node.model.{ApplicationDescriptor, FabricServiceStateFull, FabricServiceStateHolder}
import org.enterprisedlt.fabric.service.node.shared._
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.{ClassTag, _}
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

    def checkUpdateState(): Unit = FabricServiceStateHolder.updateStateFullOption(
        FabricServiceStateHolder.compose(
            updateApplicationInvitations(),
            updateContractInvitations(),
            updateCustomComponentDescriptors(),
            updateApplications()
        )
    )

    def updateApplicationInvitations(): StateChangeFunction = { current: FabricServiceStateFull =>
        val applicationInvs = for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listApplicationInvites")
            contracts <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            applicationInvitations <- Try((new GsonBuilder).create().fromJson(contracts, classOf[Array[ApplicationInvite]])).toEither.left.map(_.getMessage)
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
            contractInvitations <- Try((new GsonBuilder).create().fromJson(contracts, classOf[Array[Contract]])).toEither.left.map(_.getMessage)
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

    def updateCustomComponentDescriptors(): StateChangeFunction = { current: FabricServiceStateFull =>
        Try(getCustomComponentDescriptors)
          .toEither
          .left.map(_.getMessage) match {
            case Right(descriptors) if current.events.customComponentDescriptors.length != descriptors.length =>
                Option(current.copy(events = current.events.copy(customComponentDescriptors = descriptors)))

            case Left(msg) =>
                logger.warn(msg)
                None

            case _ => None
        }
    }


    def updateApplications(): StateChangeFunction = { current: FabricServiceStateFull =>
        val apps = for {
            applicationDescriptors <- Try(getApplicationDescriptors).toEither.left.map(_.getMessage)
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
            case Right(apps) if current.events.applications.length != apps.length || !current.events.applications.exists(app =>
                apps.exists(application => application.applicationType == app.applicationType && application.status == app.status)) =>
                logger.info(s"apps ${apps.mkString(" ")}")
                val applicationDescriptors = getApplicationDescriptors
                applicationDescriptors.foreach {
                    applicationDescriptor =>
                        applicationDescriptor.contracts.foreach {
                            chaincode =>
                                extractFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.applicationType}.tgz", s"chain-code/${chaincode.contractType}.json", "/opt/profile")
                                extractFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.applicationType}.tgz", s"chain-code/${chaincode.contractType}.tgz", "/opt/profile")
                        }
                        applicationDescriptor.components.foreach {
                            component =>
                                extractFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.applicationType}.tgz", s"components/${component.componentType}.tgz", "/opt/profile")
                        }
                }
                Option(
                    current.copy(
                        events = current.events.copy(applications = apps),
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
            applications <- Try((new GsonBuilder).create().fromJson(applications, classOf[Array[ApplicationDistributive]])).toEither.left.map(_.getMessage)
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
              readFromTarAs[ApplicationDescriptor](file.toPath, s"$filename.json")
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

    private def getCustomComponentDescriptors: Array[CustomComponentDescriptor] = {
        val customComponentsPath = "/opt/profile/components"
        Util.mkDirs(customComponentsPath)
        new File(customComponentsPath)
          .getAbsoluteFile
          .listFiles()
          .filter(_.getName.endsWith(".tgz"))
          .flatMap { file =>
              logger.info(s"file is ${file.getName}")
              val filename = s"${file.getName.split('.')(0)}.json"
              readFromTarAs[CustomComponentDescriptor](file.toPath, filename)
          }
    }

    private def readFromTarAs[T: ClassTag](filePath: Path, filename: String): Option[T] = {
        val targetClazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        withResources(
            new TarArchiveInputStream(
                new GzipCompressorInputStream(
                    Files.newInputStream(filePath)
                )
            )
        ) { inputStream =>
            Util.findInTar(inputStream, filename)(descriptorInputStream =>
                Util.codec.fromJson(new InputStreamReader(descriptorInputStream), targetClazz)
            )
        }
    }

    private def extractFileFromTar(tarPath: String, filename: String, destPath: String): Unit = {
        withResources(
            new TarArchiveInputStream(
                new GzipCompressorInputStream(
                    Files.newInputStream(Paths.get(tarPath))
                )
            )
        ) { inputStream =>
            Util.findInTar(inputStream, filename) { in =>
                val out = new FileOutputStream(s"$destPath/$filename")
                try {
                    IO.copy(in, out)
                }
                finally {
                    out.close()
                }
            }
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
