package org.enterprisedlt.fabric.service.node.cryptography

/**
  * @author Maxim Fedin
  */
case class OrganizationCryptoMaterial(
    caCert: CertAndKey,
    tlscaCert: CertAndKey,
    adminCert: CertAndKey
)
