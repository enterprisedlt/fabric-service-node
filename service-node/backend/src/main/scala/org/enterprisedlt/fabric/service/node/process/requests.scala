package org.enterprisedlt.fabric.service.node.process

import org.enterprisedlt.fabric.service.node.shared.CustomComponentConfig

/**
 * @author Alexey Polubelov
 */
// =================================================================================================================

case class StartCustomComponentRequest(
    serviceNodeName: String,
    request: CustomComponentConfig,
    crypto: CustomComponentCerts
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
