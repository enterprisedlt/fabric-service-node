package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, CallbackTo, ReactEventFromInput, ScalaComponent}
import org.scalajs.dom.html.Div

/**
 * @author Maxim Fedin
 */
object AddOrganization {

    case class State()

    private val component = ScalaComponent.builder[Unit]("AddOrganizationForm")
      .initialState(State())
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Unit, State]) {

        def addFile(event: ReactEventFromInput): CallbackTo[Unit] = ???

        def render(s: State): VdomTagOf[Div] =
            <.div(
                <.h5("Certificates"),

                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Ca Cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "caCerts", ^.onChange ==> addFile),
                            <.label(^.`class` := "custom-file-label", "Choose file")
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "TLS Ca Cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "tlsCACerts", ^.onChange ==> addFile),
                            <.label(^.`class` := "custom-file-label", "Choose file")
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Admin cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "adminCerts", ^.onChange ==> addFile),
                            <.label(^.`class` := "custom-file-label", "Choose file")
                        )
                    )
                ),
                <.hr(),
                <.h5("Organization Info"),

                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Organization msp Id"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                        )
                    )
                ),

                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Organization name"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Member number"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                        )
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
                        <.tbody()
                    )
                ),

                <.div(^.className := "form-group row",
                    <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "IP address"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control", ^.id := "ipAddress")
                    )),
                <.div(^.className := "form-group row",
                    <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "DNS name"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control", ^.id := "dnsName"))),
                <.div(^.className := "form-group row",
                    <.button(
                        ^.className := "btn btn-primary",
                        "Add host")
                ),
            )

    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}
