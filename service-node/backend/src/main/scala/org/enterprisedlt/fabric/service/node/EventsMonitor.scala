package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicReference

import com.google.gson.GsonBuilder
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.eclipse.jetty.util.IO
import org.enterprisedlt.fabric.service.model.{ApplicationDistributive, Contract}
import org.enterprisedlt.fabric.service.node.Util.withResources
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.{ApplicationDescriptor, FabricServiceStateHolder}
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
                val status = if (applications.exists(_.name == applicationDescriptor.name)) "Published" else "Downloaded"
                ApplicationEventsMonitor(
                    name = applicationDescriptor.name,
                    filename = applicationDescriptor.filename,
                    status = status,
                    contracts = applicationDescriptor.contracts,
                    components = applicationDescriptor.components
                )
            } ++ applications
              .filterNot { application =>
                  applicationDescriptors.exists(_.name == application.name)
              }.map {
                application =>
                    ApplicationEventsMonitor(
                        name = application.name,
                        filename = application.filename,
                        status = "Not Downloaded",
                        distributorAddress = application.componentsDistributorAddress
                    )
            }
        val old = events.get()
        val next = old.copy(applications = applicationEventsMonitor)
        logger.info(s"got ${applicationEventsMonitor.length} for applications")
        events.set(next)
        if (old.applications.length != next.applications.length || old.applications.flatMap(_.contracts).length != next.applications.flatMap(_.contracts).length) {
            applicationDescriptors.foreach { applicationDescriptor =>
                applicationDescriptor.contracts.foreach { chaincode =>
                    saveFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.filename}.tgz", s"chain-code/${chaincode.name}.json", "/opt/profile")
                    saveFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.filename}.tgz", s"chain-code/${chaincode.name}.tgz", "/opt/profile")
                }
                applicationDescriptor.components.foreach { component =>
                    saveFileFromTar(s"/opt/profile/application-distributives/${applicationDescriptor.filename}.tgz", s"components/${component.componentType}.tgz", "/opt/profile")
                }
            }
            FabricServiceStateHolder.incrementVersion()
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
              val applicationDescriptor = getObjectFromTar[ApplicationDescriptor](file.toPath, s"$filename.json").map(_.copy(filename = filename))
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
              getObjectFromTar[CustomComponentDescriptor](file.toPath, filename)
          }
    }

    private def getObjectFromTar[T: ClassTag](filePath: Path, filename: String): Option[T] = {
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

    private def saveFileFromTar(tarPath: String, filename: String, destPath: String): Unit = {
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
                    in.close()
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
