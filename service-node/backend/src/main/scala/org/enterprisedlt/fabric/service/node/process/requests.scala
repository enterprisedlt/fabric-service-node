package org.enterprisedlt.fabric.service.node.process

/**
 * @author Alexey Polubelov
 */
// =================================================================================================================

case class StartCustomNodeDescriptor(
    boxName: String,
    componentType: String,
    containerName: String,
    image: Image,
    environmentVariables: Array[EnvironmentVariable],
    ports: Array[PortBind],
    volumes: Array[VolumeBind],
    command: String,
    workingDir: String
)

case class StartCustomNodeRequest(
    descriptor: StartCustomNodeDescriptor,
    crypto: CustomComponentCerts
)


case class Image(
    name: String,
    tag: String = "latest"
) {
    def getName = s"$name:$tag"
}

case class PortBind(
    externalPort: String,
    internalPort: String
)

case class VolumeBind(
    externalHost: String,
    internalHost: String
)

case class EnvironmentVariable(
    key: String,
    value: String
)


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
