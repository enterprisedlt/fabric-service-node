package org.enterprisedlt.fabric.service.node

import java.io.File
import java.nio.file.Files
import java.util.Base64

import org.enterprisedlt.fabric.service.node.process.ComponentsDistributor

/**
  * @author Maxim Fedin
  */
class ComponentsDistributorRestEndpoint() extends ComponentsDistributor {

    override def getComponentTypeDistributive(componentName: String): Either[String, String] = {
        val customComponentsPath = new File(s"/opt/profile/components/").getAbsoluteFile
        if (!customComponentsPath.exists()) customComponentsPath.mkdirs()
        for {
            componentFile <-customComponentsPath.listFiles().find(_.getName == s"$componentName.tgz").toRight(s"File $componentName.tgz doesn't exist")
        } yield Base64.getEncoder.encodeToString(Files.readAllBytes(componentFile.toPath))
    }

}
