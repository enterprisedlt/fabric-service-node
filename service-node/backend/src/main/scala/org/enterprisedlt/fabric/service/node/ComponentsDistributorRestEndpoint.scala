package org.enterprisedlt.fabric.service.node

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

import org.enterprisedlt.fabric.service.node.process.ComponentsDistributor

/**
 * @author Maxim Fedin
 */
class ComponentsDistributorRestEndpoint(
    customComponentsPath: File,
    applicationPath: File
) extends ComponentsDistributor {

    override def getComponentTypeDistributive(componentName: String): Either[String, String] = {
        for {
            componentFile <- customComponentsPath.listFiles().find(_.getName == s"$componentName.tgz").toRight(s"File $componentName.tgz doesn't exist")
        } yield new String(Base64.getEncoder.encode(Files.readAllBytes(componentFile.toPath)), StandardCharsets.UTF_8)
    }

    override def getApplicationDistributive(applicationName: String): Either[String, String] = {
        for {
            componentFile <- applicationPath.listFiles().find(_.getName == s"$applicationName.tgz").toRight(s"File $applicationName.tgz doesn't exist")
        } yield new String(Base64.getEncoder.encode(Files.readAllBytes(componentFile.toPath)), StandardCharsets.UTF_8)
    }
}
