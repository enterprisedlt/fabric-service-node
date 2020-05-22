package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.all.{className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import org.enterprisedlt.fabric.service.node.BaseInfo
import org.enterprisedlt.fabric.service.node.model.ComponentCandidate
import org.enterprisedlt.fabric.service.node.shared.Box
import org.scalajs.dom.html.Select

/**
 * @author Alexey Polubelov
 */
object ComponentForm extends StatelessFormExt[ComponentCandidate, BaseInfo]("ComponentForm") {

    override def render(p: ComponentCandidate, data: BaseInfo)(implicit modState: CallbackFunction): VdomNode =
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentType", ^.className := "col-sm-4 col-form-label", "Type"),
                <.div(^.className := "col-sm-8", renderComponentType(p))
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentBox", ^.className := "col-sm-4 col-form-label", "Server"),
                <.div(^.className := "col-sm-8", renderBoxesList(p, data.boxes))
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentName", ^.className := "col-sm-4 col-form-label", "Name"),
                <.div(^.className := "input-group input-group-sm col-sm-8", ^.id := "componentName",
                    <.input(^.`type` := "text", ^.className := "form-control",
                        bind(p) := ComponentCandidate.name
                    ),
                    <.div(^.className := "input-group-append",
                        <.span(^.className := "input-group-text form-control-sm", data.orgFullName)
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "port", ^.className := "col-sm-4 col-form-label", "Port"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm", ^.id := "port",
                        bind(p) := ComponentCandidate.port
                    )
                )
            ),
        )

    private def renderComponentType(p: ComponentCandidate)(implicit modState: CallbackFunction): VdomTagOf[Select] = {
        <.select(className := "form-control form-control-sm",
            id := "componentType",
            bind(p) := ComponentCandidate.componentType,
            componentTypeOptions(p)
        )
    }

    private def componentTypeOptions(p: ComponentCandidate): TagMod = {
        ComponentCandidate.Types.map { name =>
            option((className := "selected").when(p.componentType == name), name)
        }.toTagMod
    }

    private def renderBoxesList(p: ComponentCandidate, boxes: Array[Box])(implicit modState: CallbackFunction): VdomTagOf[Select] = {
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