package org.enterprisedlt.fabric.service.node

import java.io.{BufferedReader, FileReader, FileWriter}
import java.util.NoSuchElementException
import java.util.concurrent.locks.ReentrantLock

import org.enterprisedlt.fabric.service.model.KnownHostRecord

import scala.collection.mutable
import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class HostsManager(
    hostsFileName: String,
    selfAddress: Option[String]
) {
    private val hostsUpdateLock = new ReentrantLock()
    private val hosts = {
        hostsUpdateLock.lock()
        try {
            readHostsFile
        } finally {
            hostsUpdateLock.unlock()
        }
    }

    def updateHosts(updatedHosts: Array[KnownHostRecord]): Unit = {
        hostsUpdateLock.lock()
        try {
            val update = updatedHosts.filter(h => !selfAddress.contains(h.ipAddress)) // skip records that belong to us
            hosts ++= update.map(kh => kh.dnsName -> kh.ipAddress).toMap
            updateHostsFile(hosts)
        } finally {
            hostsUpdateLock.unlock()
        }
    }

    private def readHostsFile: mutable.Map[String, String] = {
        mutable.Map.empty ++
          new FileLinesIterator(hostsFileName)
            .map(_.trim)
            .filter(!_.startsWith("#"))
            .flatMap { line =>
                Try {
                    //for real dns this should be (space | tab)* , here we expect the hosts managed by us only
                    val Array(address, name) = line.split('\t')
                    name -> address
                }.toOption
                // in real dns there could be several addresses for name and several names for address,
                // but in our case we expect only one-to-one mapping
            }.toMap
    }

    private def updateHostsFile(hosts: mutable.Map[String, String]): Unit = {
        val writer = new FileWriter(hostsFileName)
        try {
            hosts.foreach { host =>
                writer.append(s"${host._2}\t${host._1}\n")
            }
        } finally {
            writer.flush()
            writer.close()
        }
    }

    // WARN: not thread safe
    class FileLinesIterator(file: String) extends Iterator[String] {
        private val reader = new BufferedReader(new FileReader(file))
        private var line: String = _

        override def hasNext: Boolean = {
            if (line != null) {
                true
            } else {
                line = reader.readLine()
                line != null
            }
        }

        override def next(): String = {
            if (line == null && !this.hasNext) {
                throw new NoSuchElementException
            } else {
                val r = line
                line = null
                r
            }
        }
    }

}
