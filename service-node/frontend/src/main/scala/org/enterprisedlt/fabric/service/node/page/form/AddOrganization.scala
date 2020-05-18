package org.enterprisedlt.fabric.service.node.page.form

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.{FieldBinder, StateFieldBinder}
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{JoinRequest, KnownHostRecord, Organization, OrganizationCertificates}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{Blob, File, FileReader}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scala.util.Try

/**
  * @author Maxim Fedin
  */
object AddOrganization {

    @Lenses case class AddOrganizationState(
        joinRequest: JoinRequest,
        knownHostsCandidate: KnownHostRecord,
        caCertFileName: String,
        caCertFile: File,
        tlsCaCertFileName: String,
        tlsCaCertFile: File,
        adminCertFileName: String,
        adminCertFile: File
    )

    object AddOrganizationState {
        val Defaults: AddOrganizationState = {
            AddOrganizationState(
                JoinRequest.Defaults,
                KnownHostRecord(
                    ipAddress = "",
                    dnsName = ""
                ),
                "Choose file",
                null,
                "Choose file",
                null,
                "Choose file",
                null
            )
        }
    }

    private val component = ScalaComponent.builder[Unit]("AddOrganizationForm")
      .initialState(AddOrganizationState.Defaults)
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Unit, AddOrganizationState]) extends StateFieldBinder[AddOrganizationState] {

        private val KnownHosts = AddOrganizationState.joinRequest / JoinRequest.organization / Organization.knownHosts

        def doJoinNetwork(addOrganizationState: AddOrganizationState): Callback = Callback.future {
            for {
                caCert <- readAsArrayBuffer(addOrganizationState.caCertFile).map(toByteBuffer).map(encodeToB64).map(asUTF8)
                tlsCert <- readAsArrayBuffer(addOrganizationState.tlsCaCertFile).map(toByteBuffer).map(encodeToB64).map(asUTF8)
                adminCert <- readAsArrayBuffer(addOrganizationState.adminCertFile).map(toByteBuffer).map(encodeToB64).map(asUTF8)
                request = addOrganizationState.joinRequest
                  .copy(
                      organizationCertificates = OrganizationCertificates(
                          caCerts = Array(caCert),
                          tlsCACerts = Array(tlsCert),
                          adminCerts = Array(adminCert)
                      )
                  )
                response <- ServiceNodeRemote.joinNetwork(request)
            } yield Callback(response)
        }

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

        private val addCaCertFile: ReactEventFromInput => Callback = processFileEvent { file =>
            $.modState(x => x.copy(caCertFileName = file.name, caCertFile = file))
        }

        private val addTlsCaCertFile: ReactEventFromInput => Callback = processFileEvent { file =>
            $.modState(x => x.copy(tlsCaCertFileName = file.name, tlsCaCertFile = file))
        }

        private val addAdminCertFile: ReactEventFromInput => Callback = processFileEvent { file =>
            $.modState(x => x.copy(adminCertFileName = file.name, adminCertFile = file))
        }

        private def processFileEvent(f: File => Callback)(event: ReactEventFromInput): Callback =
            Callback.sequenceOption(Option(event.target.files(0)).map(f))

        def deleteComponent(host: KnownHostRecord): CallbackTo[Unit] = {
            val state = KnownHosts.modify(_.filter(_.dnsName != host.dnsName))
            $.modState(state)
        }

        def addKnownHostComponent(joinState: AddOrganizationState): CallbackTo[Unit] = {
            $.modState(
                addComponent(joinState) andThen AddOrganizationState.knownHostsCandidate.set(AddOrganizationState.Defaults.knownHostsCandidate)
            )
        }

        private def addComponent(dashboardState: AddOrganizationState): AddOrganizationState => AddOrganizationState = {
            val componentCandidate = dashboardState.knownHostsCandidate
            KnownHosts.modify(_ :+ componentCandidate)
        }

        def render(s: AddOrganizationState): VdomTagOf[Div] =
            <.div(
                <.h4("Add organization"),
                <.div(^.float.right, ^.verticalAlign.`text-top`,
                    <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> doJoinNetwork(s), "Add organization")
                ),
                <.span(<.br()),
                <.h5("Certificates"),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "CA certificate:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "caCerts", ^.onChange ==> addCaCertFile),
                            <.label(^.`class` := "custom-file-label", s.caCertFileName)
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "TLS CA certificate:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "tlsCACerts", ^.onChange ==> addTlsCaCertFile),
                            <.label(^.`class` := "custom-file-label", s.tlsCaCertFileName)
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Admin certificate:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "adminCerts", ^.onChange ==> addAdminCertFile),
                            <.label(^.`class` := "custom-file-label", s.adminCertFileName)
                        )
                    )
                ),
                <.hr(),
                <.h5("Organization Info"),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "MSP ID"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                            bind(s) := AddOrganizationState.joinRequest / JoinRequest.organization / Organization.mspId)
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Name"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                            bind(s) := AddOrganizationState.joinRequest / JoinRequest.organization / Organization.name)
                    )
                ),
                <.hr(),
                <.h5("Known hosts"),

                <.div(^.className := "form-group row",
                    <.table(^.className := "table table-hover table-sm",
                        <.thead(
                            <.tr(
                                <.th(^.scope := "col", "#"),
                                <.th(^.scope := "col", "IP address"),
                                <.th(^.scope := "col", "DNS name"),
                                <.th(^.scope := "col", "Actions"),
                            )
                        ),
                        <.tbody(
                            s.joinRequest.organization.knownHosts.zipWithIndex.map { case (host, index) =>
                                <.tr(
                                    <.td(^.scope := "row", s"${index + 1}"),
                                    <.td(host.ipAddress),
                                    <.td(host.dnsName),
                                    <.td(
                                        <.button(
                                            ^.className := "btn btn-primary",
                                            "Remove",
                                            ^.onClick --> deleteComponent(host))
                                    )
                                )
                            }.toTagMod
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "IP address"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control", ^.id := "ipAddress",
                            bind(s) := AddOrganizationState.knownHostsCandidate / KnownHostRecord.ipAddress)
                    )),
                <.div(^.className := "form-group row",
                    <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "DNS name"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control", ^.id := "dnsName",
                            bind(s) := AddOrganizationState.knownHostsCandidate / KnownHostRecord.dnsName))),
                <.div(^.className := "form-group row",
                    <.button(
                        ^.className := "btn btn-primary",
                        ^.onClick --> addKnownHostComponent(s),
                        "Add host")
                ),
            )
    }

    def apply(): Unmounted[Unit, AddOrganizationState, Backend] = component()
}
