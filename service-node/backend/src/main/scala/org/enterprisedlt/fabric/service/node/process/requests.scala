package org.enterprisedlt.fabric.service.node.process

import org.enterprisedlt.fabric.service.node.shared.{EnvironmentVariable, PortBind, VolumeBind}

/**
 * @author Alexey Polubelov
 */
// =================================================================================================================

case class CustomComponentDescriptor(
    image: Image,
    command: String,
    workingDir: String
)


case class CustomComponentRequest(
    boxName: String,
    componentType: String,
    containerName: String,
    environmentVariables: Array[EnvironmentVariable],
    ports: Array[PortBind],
    volumes: Array[VolumeBind],
)

case class StartCustomComponentRequest(
    serviceNodeName: String,
    request: CustomComponentRequest,
    crypto: CustomComponentCerts
)


case class Image(
    name: String,
    tag: String = "latest"
) {
    def getName = s"$name:$tag"
}




case class StartOSNRequest(
    port: Int,
    genesis: String, //b64
    organization: OrganizationConfig,
    component: ComponentConfig
)

case class StartPeerRequest(
    port: Int,
    organization: OrganizationConfig,
    component: ComponentConfig
)

case class OrganizationConfig(
    mspId: String,
    fullName: String,
    cryptoMaterial: OrganizationCryptoMaterialPEM
)

case class OrganizationCryptoMaterialPEM(
    ca: CertAndKeyPEM,
    tlsca: CertAndKeyPEM,
    admin: CertAndKeyPEM
)

case class ComponentConfig(
    fullName: String,
    cryptoMaterial: ComponentCryptoMaterialPEM
)

case class ComponentCryptoMaterialPEM(
    msp: CertAndKeyPEM,
    tls: CertAndKeyPEM
)

case class CertAndKeyPEM(
    certificate: String,
    key: String
)

case class CustomComponentCerts(
    tlsPeer: String,
    tlsOsn: String,
    customComponentCerts: CertAndKeyPEM
)
