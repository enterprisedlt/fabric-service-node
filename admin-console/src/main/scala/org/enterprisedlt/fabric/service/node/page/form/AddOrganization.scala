package org.enterprisedlt.fabric.service.node.page.form

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.FieldBinder
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{JoinRequest, KnownHostRecord, Organization, OrganizationCertificates}
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{File, FileReader}

import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

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

    class Backend(val $: BackendScope[Unit, AddOrganizationState]) extends FieldBinder[AddOrganizationState] {

        private val KnownHosts = AddOrganizationState.joinRequest / JoinRequest.organization / Organization.knownHosts

        def doJoinNetwork(addOrganizationState: AddOrganizationState): Callback = Callback {
            val adminCertReader = new FileReader()
            adminCertReader.onload = _ => {
                val adminCertBytes: ByteBuffer = TypedArrayBuffer.wrap(adminCertReader.result.asInstanceOf[ArrayBuffer])
                val adminCertB64 = Base64.getEncoder.encode(adminCertBytes)
                val adminCert = StandardCharsets.UTF_8.decode(adminCertB64).toString
                //
                val caCertReader = new FileReader()
                caCertReader.onload = _ => {
                    val caCertBytes = TypedArrayBuffer.wrap(caCertReader.result.asInstanceOf[ArrayBuffer])
                    val caCertB64 = Base64.getEncoder.encode(caCertBytes)
                    val caCert =  StandardCharsets.UTF_8.decode(caCertB64).toString
                    //
                    val tlsCaCertReader = new FileReader()
                    tlsCaCertReader.onload = _ => {
                        val tlsCaCertBytes = TypedArrayBuffer.wrap(tlsCaCertReader.result.asInstanceOf[ArrayBuffer])
                        val tlsCaCertB64 = Base64.getEncoder.encode(tlsCaCertBytes)
                        val tlsCert = StandardCharsets.UTF_8.decode(tlsCaCertB64).toString
                        //
                        val request = addOrganizationState.joinRequest
                          .copy(
                              organizationCertificates = OrganizationCertificates(
                                  caCerts = Array(caCert),
                                  tlsCACerts = Array(tlsCert),
                                  adminCerts = Array(adminCert)
                              )
                          )
                        ServiceNodeRemote.joinNetwork(request)
                    }
                    tlsCaCertReader.readAsArrayBuffer(addOrganizationState.tlsCaCertFile)
                }
                caCertReader.readAsArrayBuffer(addOrganizationState.caCertFile)
            }
            adminCertReader.readAsArrayBuffer(addOrganizationState.adminCertFile)
        }

        def addCaCertFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(caCertFileName = file.name, caCertFile = file))
        }

        def addTlsCaCertFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(tlsCaCertFileName = file.name, tlsCaCertFile = file))
        }

        def addAdminCertFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(adminCertFileName = file.name, adminCertFile = file))
        }


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
                    <.label(^.className := "col-sm-2 col-form-label", "Ca Cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "caCerts", ^.onChange ==> addCaCertFile),
                            <.label(^.`class` := "custom-file-label", s.caCertFileName)
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "TLS Ca Cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "tlsCACerts", ^.onChange ==> addTlsCaCertFile),
                            <.label(^.`class` := "custom-file-label", s.tlsCaCertFileName)
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Admin cert:"),
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
                    <.label(^.className := "col-sm-2 col-form-label", "Organization msp Id"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                            bind(s) := AddOrganizationState.joinRequest / JoinRequest.organization / Organization.mspId)
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Organization name"),
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
