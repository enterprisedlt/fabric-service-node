package org.enterprisedlt.fabric.service.node

import java.io.{File, InputStreamReader}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference

import com.google.gson.GsonBuilder
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.enterprisedlt.fabric.service.model.{Application, Contract}
import org.enterprisedlt.fabric.service.node.Util.withResources
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.FabricServiceStateHolder
import org.enterprisedlt.fabric.service.node.shared._
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.{ClassTag, _}
import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class EventsMonitor(
    eventPullingInterval: Long, //Ms
    networkManager: FabricNetworkManager
) extends Thread("EventsMonitor") {
    @volatile private var working = true
    private val logger: Logger = LoggerFactory.getLogger(this.getClass)
    private var delay: Long = -1L
    private val events: AtomicReference[Events] = new AtomicReference[Events](Events())

    def getEvents: Events = events.get()

    def updateEvents(): Either[String, Unit] = {
        for {
            _ <- updateContractInvitations()
            _ <- updateCustomComponentDescriptors()
            _ <- updateApplications()
        } yield ()
    }

    def updateContractInvitations(): Either[String, Unit] = {
        for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
            contracts <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            contractInvitations <- Try((new GsonBuilder).create().fromJson(contracts, classOf[Array[Contract]])).toEither.left.map(_.getMessage)
        } yield {
            val old = events.get()
            val next = old.copy(
                contractInvitations = contractInvitations.map { contract =>
                    ContractInvitation(
                        initiator = contract.founder,
                        name = contract.name,
                        chainCodeName = contract.contractType,
                        chainCodeVersion = contract.version,
                        participants = contract.participants,
                    )
                },
            )
            events.set(next)
            logger.info(s"got ${contractInvitations.length} contract records")
            if (
                old.contractInvitations.length != next.contractInvitations.length
            ) {
                FabricServiceStateHolder.incrementVersion()
            }
        }
    }

    def updateCustomComponentDescriptors(): Either[String, Unit] = Try(getCustomComponentDescriptors)
      .map { customComponentDescriptors =>
          val old = events.get()
          val next = old.copy(customComponentDescriptors = customComponentDescriptors)
          events.set(next)
          logger.info(s"got ${customComponentDescriptors.length} component descriptor")
          if (
              old.customComponentDescriptors.length != next.customComponentDescriptors.length
          ) {
              FabricServiceStateHolder.incrementVersion()
          }
      }.toEither.left.map(_.getMessage)


    def updateApplications(): Either[String, Unit] = for {
        applicationDescriptors <- Try(getApplicationDescriptors).toEither.left.map(_.getMessage)
        applications <- getApplications
    } yield {
        val applicationEventsMonitor =
            applicationDescriptors.map { applicationDescriptor =>
                val status = if (applications.exists(_.name == applicationDescriptor.name)) "Published" else "Installed"
                ApplicationEventsMonitor(
                    applicationDescriptor.name,
                    status
                )
            } ++ applications
              .filterNot { application =>
                  applicationDescriptors.exists(_.name == application.name)
              }.map {
                application =>
                    ApplicationEventsMonitor(
                        application.name,
                        "Not Installed"
                    )
            }
        val old = events.get()
        val next = old.copy(applications = applicationEventsMonitor)
        logger.info(s"got ${applicationEventsMonitor.length} for applications")
        events.set(next)
        if (
            old.applications.length != next.applications.length
        ) {
            FabricServiceStateHolder.incrementVersion()
        }
    }

    //    ================================================================================
    private def getApplications: Either[String, Array[Application]] = {
        for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listApplications")
            applications <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            contractInvitations <- Try((new GsonBuilder).create().fromJson(applications, classOf[Array[Application]])).toEither.left.map(_.getMessage)
        } yield contractInvitations
    }

    private def getApplicationDescriptors: Array[ApplicationDescriptor] = {
        val applicationsPath = "/opt/profile/applications"
        Util.mkDirs(applicationsPath)
        new File(applicationsPath)
          .getAbsoluteFile
          .listFiles()
          .filter(_.getName.endsWith(".tgz"))
          .flatMap { file =>
              logger.info(s"file is ${file.getName}")
              val filename = s"${file.getName.split('.')(0)}.json"
              getFileFromTar[ApplicationDescriptor](file.toPath, filename)
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
              getFileFromTar[CustomComponentDescriptor](file.toPath, filename)
          }
    }

    private def getFileFromTar[T: ClassTag](filePath: Path, filename: String): Option[T] = {
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

    override def run(): Unit = {
        logger.info("Events monitor started")
        while (working) {
            try {
                if (delay > 0) {
                    Thread.sleep(delay)
                }
                val start = System.currentTimeMillis()
                updateEvents().left.map { error =>
                    logger.warn(s"Failed to pull events: $error")
                }
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
