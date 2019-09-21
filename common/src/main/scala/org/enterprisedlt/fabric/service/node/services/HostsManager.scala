package org.enterprisedlt.fabric.service.node.services

import java.io.FileWriter

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
        try {
            if (organization.knownHosts.nonEmpty) {
                writer.append(s"\n#Known hosts for $organizationFullName:\n")
                organization.knownHosts.foreach { host =>
                    writer.append(s"${host.ipAddress}\t${host.dnsName}\n")
                }
            }
        } finally {
            writer.flush()
            writer.close()
        }
    }
}
