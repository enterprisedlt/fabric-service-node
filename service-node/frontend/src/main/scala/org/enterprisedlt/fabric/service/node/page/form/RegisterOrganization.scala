package org.enterprisedlt.fabric.service.node.page.form

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo, ReactEventFromInput}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.Ready
import org.enterprisedlt.fabric.service.node.model.{JoinRequest, KnownHostRecord, Organization, OrganizationCertificates}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.raw.{Blob, File, FileReader}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.UndefOr
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scala.util.Try

/**
 * @author Maxim Fedin
 */

@Lenses case class RegisterOrganizationState(
    knownHostsCandidate: KnownHostRecord,
    caCertFileName: String,
    tlsCaCertFileName: String,
    adminCertFileName: String,
)

object RegisterOrganization extends StateFullFormExt[JoinRequest, Ready, RegisterOrganizationState]("register-organization-form") {
    override def initState(p: JoinRequest, data: Ready): RegisterOrganizationState = {
        RegisterOrganizationState(
            KnownHostRecord(
                ipAddress = "",
                dnsName = ""
            ),
            "Choose file",
            "Choose file",
            "Choose file",
        )
    }

    override def render(s: RegisterOrganizationState, p: JoinRequest, data: Ready)
      (implicit modP: (JoinRequest => JoinRequest) => Callback, modS: (RegisterOrganizationState => RegisterOrganizationState) => Callback): VdomNode = {
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "MSP ID"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := JoinRequest.organization / Organization.mspId
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Certificates"))
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "CA certificate:"),
                <.div(^.className := "input-group input-group-sm col-sm-8",
                    <.div(^.className := "custom-file",
                        <.input(^.`type` := "file", ^.className := "custom-file-input", ^.id := "caCerts", ^.onChange ==> addCaCertFile),
                        <.label(^.className := "custom-file-label", s.caCertFileName)
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "TLS CA certificate:"),
                <.div(^.className := "input-group input-group-sm col-sm-8",
                    <.div(^.className := "custom-file",
                        <.input(^.`type` := "file", ^.className := "custom-file-input", ^.id := "tlsCACerts", ^.onChange ==> addTlsCaCertFile),
                        <.label(^.className := "custom-file-label", s.tlsCaCertFileName)
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Admin certificate:"),
                <.div(^.className := "input-group input-group-sm col-sm-8",
                    <.div(^.className := "custom-file",
                        <.input(^.`type` := "file", ^.className := "custom-file-input", ^.id := "adminCerts", ^.onChange ==> addAdminCertFile),
                        <.label(^.className := "custom-file-label", s.adminCertFileName)
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Known hosts"))
            ),
            <.table(^.className := "table table-sm",
                <.thead(^.className := "thead-light",
                    <.tr(
                        <.th(^.scope := "col", "DNS name", ^.width := "45%"),
                        <.th(^.scope := "col", "IP address", ^.width := "45%"),
                        <.th(^.scope := "col", "", ^.width := "10%"),
                    )
                ),
                <.tbody(
                    p.organization.knownHosts.map { record =>
                        <.tr(
                            <.td(record.dnsName),
                            <.td(record.ipAddress),
                            <.td(
                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                                    ^.onClick --> removeKnownHost(record),
                                    <.i(^.className := "fas fa-minus-circle")
                                )
                            )
                        )
                    }.toTagMod,
                    <.tr(
                        <.td(
                            <.input(^.`type` := "text", ^.className := "form-control form-control-sm", ^.id := "dnsName",
                                bind(s) := RegisterOrganizationState.knownHostsCandidate / KnownHostRecord.dnsName
                            )
                        ),
                        <.td(
                            <.input(^.`type` := "text", ^.className := "form-control form-control-sm", ^.id := "ipAddress",
                                bind(s) := RegisterOrganizationState.knownHostsCandidate / KnownHostRecord.ipAddress
                            ),
                        ),
                        <.td(
                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                                ^.onClick --> addKnownHost(s.knownHostsCandidate),
                                <.i(^.className := "fas fa-plus-circle")
                            )
                        )
                    )
                )
            ),
        )
    }

    private def addCaCertFile(event: ReactEventFromInput)
      (implicit
          modS: (RegisterOrganizationState => RegisterOrganizationState) => Callback,
          modP: (JoinRequest => JoinRequest) => Callback)
    : Callback =
        processFileEvent(event) { (name, content) =>
            modS(x => x.copy(caCertFileName = name)) <<
              modP(JoinRequest.organizationCertificates / OrganizationCertificates.caCerts set Array(content))
        }

    private def addTlsCaCertFile(event: ReactEventFromInput)
      (implicit
          modS: (RegisterOrganizationState => RegisterOrganizationState) => Callback,
          modP: (JoinRequest => JoinRequest) => Callback)
    : Callback =
        processFileEvent(event) { (name, content) =>
            modS(x => x.copy(tlsCaCertFileName = name)) <<
              modP(JoinRequest.organizationCertificates / OrganizationCertificates.tlsCACerts set Array(content))
        }

    private def addAdminCertFile(event: ReactEventFromInput)
      (implicit
          modS: (RegisterOrganizationState => RegisterOrganizationState) => Callback,
          modP: (JoinRequest => JoinRequest) => Callback)
    : Callback =
        processFileEvent(event) { (name, content) =>
            modS(x => x.copy(adminCertFileName = name)) <<
              modP(JoinRequest.organizationCertificates / OrganizationCertificates.adminCerts set Array(content))
        }

    // (name, content) => Callback
    private def processFileEvent(event: ReactEventFromInput)(f: (String, String) => Callback): Callback =
        Callback.sequenceOption(
            event.target.files(0).asInstanceOf[UndefOr[File]]
              .toOption
              .map { file =>
                  Callback.future(
                      readAsArrayBuffer(file)
                        .map(toByteBuffer)
                        .map(encodeToB64)
                        .map(asUTF8)
                        .map(content => f(file.name, content))
                  )
              }
        )

    private def removeKnownHost(host: KnownHostRecord)
      (implicit modState: (JoinRequest => JoinRequest) => Callback): CallbackTo[Unit] =
        modState(
            (JoinRequest.organization / Organization.knownHosts)
              .modify(
                  _.filter(record =>
                      !(record.dnsName == host.dnsName && record.ipAddress == host.ipAddress)
                  )
              )
        )

    private def addKnownHost(host: KnownHostRecord)
      (implicit modState: (JoinRequest => JoinRequest) => Callback): CallbackTo[Unit] =
        modState(
            (JoinRequest.organization / Organization.knownHosts)
              .modify(_ :+ host)
        )


    private def readAsArrayBuffer(blob: Blob): Future[ArrayBuffer] = {
        val promise = Promise[ArrayBuffer]()
        val fileReader = new FileReader()
        fileReader.onerror = _ => promise.failure(new Exception("Unable to read from blob"))
        fileReader.onloadend = _ => promise.complete(Try(fileReader.result.asInstanceOf[ArrayBuffer]))
        fileReader.readAsArrayBuffer(blob)
        promise.future
    }

    private def toByteBuffer(ab: ArrayBuffer): ByteBuffer = TypedArrayBuffer.wrap(ab)

    private def encodeToB64(bb: ByteBuffer): ByteBuffer = Base64.getEncoder.encode(bb)

    private def asUTF8(bb: ByteBuffer): String = StandardCharsets.UTF_8.decode(bb).toString
}
