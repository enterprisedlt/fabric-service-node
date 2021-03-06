package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.all.{className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.Ready
import org.enterprisedlt.fabric.service.node.model.ComponentCandidate
import org.enterprisedlt.fabric.service.node.shared.{Box, PortBind, Property, VolumeBind}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.html.Select

/**
 * @author Alexey Polubelov
 */

@Lenses case class ComponentFormState(
    environmentVariable: Property,
    port: PortBind,
    volume: VolumeBind,

)

object ComponentFormDashboard extends StateFullFormExt[ComponentCandidate, Ready, ComponentFormState]("register-component-form") {

    override def initState(c: ComponentCandidate, data: Ready): ComponentFormState = {
        ComponentFormState(
            environmentVariable = new Property(
                key = "",
                value = ""
            ),
            port = new PortBind(
                externalPort = "",
                internalPort = ""
            ),
            volume = new VolumeBind(
                externalHost = "",
                internalHost = ""
            )
        )
    }

    override def render(s: ComponentFormState, p: ComponentCandidate, data: Ready)
      (implicit modP: (ComponentCandidate => ComponentCandidate) => Callback, modS: (ComponentFormState => ComponentFormState) => Callback)
    : VdomNode =
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentType", ^.className := "col-sm-4 col-form-label", "Type"),
                <.div(^.className := "col-sm-8", renderComponentType(p, data.customComponentDescriptors.map(_.componentType)))
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentBox", ^.className := "col-sm-4 col-form-label", "Server"),
                <.div(^.className := "col-sm-8", renderBoxesList(p, data.info.boxes))
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentName", ^.className := "col-sm-4 col-form-label", "Name"),
                <.div(^.className := "input-group input-group-sm col-sm-8", ^.id := "componentName",
                    <.input(^.`type` := "text", ^.className := "form-control",
                        bind(p) := ComponentCandidate.name
                    ),
                    <.div(^.className := "input-group-append",
                        <.span(^.className := "input-group-text form-control-sm", data.info.orgFullName)
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Properties"))
            ),
            <.table(^.className := "table table-sm",
                <.thead(^.className := "thead-light",
                    <.tr(
                        <.th(^.scope := "col", "Name", ^.width := "45%"),
                        <.th(^.scope := "col", "Value", ^.width := "45%"),
                        <.th(^.scope := "col", "", ^.width := "10%"),
                    )
                ),
                <.tbody(
                    p.properties.map { envVar =>
                        <.tr(
                            <.td(envVar.key),
                            <.td(envVar.value),
                            <.td(
                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                                    ^.onClick --> removeProperty(envVar),
                                    <.i(^.className := "fas fa-minus-circle")
                                )
                            )
                        )
                    }.toTagMod,
                    <.tr(
                        <.td(
                            <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                                bind(s) := ComponentFormState.environmentVariable / Property.key
                            )
                        ),
                        <.td(
                            <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                                bind(s) := ComponentFormState.environmentVariable / Property.value
                            )
                        ),
                        <.td(
                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                                ^.onClick --> addProperty(s),
                                <.i(^.className := "fas fa-plus-circle")
                            )
                        )
                    )
                )
            )
        )

    private def removeProperty(environmentVariable: Property)
      (implicit modState: (ComponentCandidate => ComponentCandidate) => Callback)
    : CallbackTo[Unit] =
        modState(
            ComponentCandidate.properties.modify(
                _.filter(p => !(p.value == environmentVariable.value && p.key == environmentVariable.key))
            )
        )


    private def addProperty(s: ComponentFormState)
      (implicit modP: (ComponentCandidate => ComponentCandidate) => Callback, modS: (ComponentFormState => ComponentFormState) => Callback)
    : CallbackTo[Unit] =
        modP(ComponentCandidate.properties.modify(_ :+ s.environmentVariable)) >>
          modS(ComponentFormState.environmentVariable.modify(_ =>
              Property(
                  key = "",
                  value = ""
              ))
          )


    private def renderComponentType(p: ComponentCandidate, componentTypes: Array[String])(implicit modState: (ComponentCandidate => ComponentCandidate) => Callback): VdomTagOf[Select] = {
        <.select(className := "form-control form-control-sm",
            id := "componentType",
            bind(p) := ComponentCandidate.componentType,
            componentTypeOptions(p, componentTypes)
        )
    }

    private def componentTypeOptions(p: ComponentCandidate, componentTypes: Array[String]): TagMod = {
        (ComponentCandidate.Types ++ componentTypes).map { name =>
            option((className := "selected").when(p.componentType == name), name)
        }.toTagMod
    }

    private def renderBoxesList(p: ComponentCandidate, boxes: Array[Box])(implicit modState: (ComponentCandidate => ComponentCandidate) => Callback): VdomTagOf[Select] = {
        <.select(className := "form-control form-control-sm",
            id := "componentType",
            bind(p) := ComponentCandidate.box,
            boxOptions(p, boxes)
        )
    }

    private def boxOptions(p: ComponentCandidate, boxes: Array[Box]): TagMod = {
        boxes.map { box =>
            option((className := "selected").when(p.box == box.name), box.name)
        }.toTagMod
    }


}
