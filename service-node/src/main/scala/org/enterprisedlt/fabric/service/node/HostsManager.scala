package org.enterprisedlt.fabric.service.node

import java.io.{BufferedReader, FileReader, FileWriter}
import java.util.regex.Pattern

import org.enterprisedlt.fabric.service.model.Organization
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig

/**
  * @author Alexey Polubelov
  */
class HostsManager(
    hostsFileName: String,
    config: ServiceConfig
) {

    def addOrganization(organization: Organization): Unit = {
        val writer = new FileWriter(hostsFileName, true)
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        try{
            writer.append(s"\n#Known hosts for $organizationFullName:\n")
            organization.knownHosts.foreach { host =>
                writer.append(s"${host.ipAddress}\t${host.dnsName}\n")
            }
        } finally {
            writer.flush()
            writer.close()
        }
    }
}
