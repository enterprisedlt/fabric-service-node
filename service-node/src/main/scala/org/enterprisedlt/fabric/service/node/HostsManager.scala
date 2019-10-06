package org.enterprisedlt.fabric.service.node

import java.io.FileWriter

import org.enterprisedlt.fabric.service.model.Organization
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig

/**
  * @author Alexey Polubelov
  */
class HostsManager(
    hostsFileName: String,
    organizationConfig: OrganizationConfig
) {

    def addOrganization(organization: Organization): Unit = {
        val writer = new FileWriter(hostsFileName, true)
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
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
