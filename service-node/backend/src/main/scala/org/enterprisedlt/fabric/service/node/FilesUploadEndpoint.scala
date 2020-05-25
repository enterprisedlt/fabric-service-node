package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.rest.Get
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */
class FilesUploadEndpoint() {
    private val logger = LoggerFactory.getLogger(this.getClass)

    @Get("/admin/upload-contract-package")
    def uploadContractPackage = {
        logger.info("Uploading contract package...")

    }


}


