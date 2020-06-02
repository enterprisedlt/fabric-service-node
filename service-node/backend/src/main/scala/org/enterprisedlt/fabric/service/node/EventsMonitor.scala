package org.enterprisedlt.fabric.service.node

import java.io.{ByteArrayInputStream, File, InputStreamReader}
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

import com.google.gson.GsonBuilder
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.enterprisedlt.fabric.service.model.Contract
import org.enterprisedlt.fabric.service.node.Util.withResources
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.FabricServiceStateHolder
import org.enterprisedlt.fabric.service.node.shared.{ContractInvitation, CustomComponentDescriptor, Events}
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.classTag
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
    private val events = new AtomicReference[Events](Events(Array.empty, Array.empty, Array.empty))

    def updateEvents(): Either[String, Unit] = {
        for {
            queryResult <- networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
            contracts <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            contractInvitations <- Try((new GsonBuilder).create().fromJson(contracts, classOf[Array[Contract]])).toEither.left.map(_.getMessage)
            customComponentDescriptors <- Try(getCustomComponentDescriptors).toEither.left.map(_.getMessage)
        } yield {
            val old = events.get()
            val next = Events(
                messages = Array.empty,
                contractInvitations = contractInvitations.map { contract =>
                    ContractInvitation(
                        initiator = contract.founder,
                        name = contract.name,
                        chainCodeName = contract.contractType,
                        chainCodeVersion = contract.version,
                        participants = contract.participants,
                    )
                },
                customComponentDescriptors = customComponentDescriptors
            )
            events.set(next)
            logger.info(s"got ${contractInvitations.length} contract records")
            logger.info(s"got ${customComponentDescriptors.length} component descriptor")

            // TODO: implement more correct diff/check
            if (
                old.messages.length != next.messages.length ||
                  old.contractInvitations.length != next.contractInvitations.length ||
                  old.customComponentDescriptors.length != next.customComponentDescriptors.length
            ) {
                FabricServiceStateHolder.incrementVersion()
            }
        }
    }

    def getEvents: Events = events.get()

    def getCustomComponentDescriptors: Array[CustomComponentDescriptor] = {
        val customComponentsPath = new File("/opt/profile/components").getAbsoluteFile
        customComponentsPath
          .listFiles()
          .filter(_.getName.endsWith(".tgz"))
          .flatMap { file =>
            logger.info(s"file is ${file.getName}")
            val tarByteArray = Files.readAllBytes(file.toPath)
            val filename = file.getName.split('.')(0)
            getCustomComponentDescriptorFromTar(tarByteArray, s"$filename.json")
        }
    }

    private def getCustomComponentDescriptorFromTar(tarFile: Array[Byte], filename: String): Option[CustomComponentDescriptor] =
    withResources(
        new TarArchiveInputStream(
            new GzipCompressorInputStream(
                new ByteArrayInputStream(tarFile)
            )
        )
    ) { tarIterator =>
        new TarIterator(tarIterator)
          .find(_.name == filename)
          .map { tarRecord =>
              Util.codec.fromJson(new InputStreamReader(tarRecord.tarArchiveInputStream), classTag[CustomComponentDescriptor].runtimeClass)
                .asInstanceOf[CustomComponentDescriptor]
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
