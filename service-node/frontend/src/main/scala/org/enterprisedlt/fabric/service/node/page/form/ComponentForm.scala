package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.all.{className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.Ready
import org.enterprisedlt.fabric.service.node.model.ComponentCandidate
import org.enterprisedlt.fabric.service.node.shared.{Box, EnvironmentVariable}
import org.scalajs.dom.html.Select
import org.enterprisedlt.fabric.service.node.util.DataFunction._

/**
 * @author Alexey Polubelov
 */

@Lenses case class ComponentFormState(
    environmentVariable: EnvironmentVariable
)

object ComponentForm extends StateFullFormExt[ComponentCandidate, Ready, ComponentFormState]("register-component-form") {

    override def initState(c: ComponentCandidate, data: Ready): ComponentFormState = {
        ComponentFormState(
            environmentVariable = EnvironmentVariable.Defaults
        )
    }

    override def render(s: ComponentFormState, p: ComponentCandidate, data: Ready)
      (implicit modP: (ComponentCandidate => ComponentCandidate) => Callback, modS: (ComponentFormState => ComponentFormState) => Callback)
    : VdomNode =
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.`for` := "componentType", ^.className := "col-sm-4 col-form-label", "Type"),
                <.div(^.className := "col-sm-8", renderComponentType(p))
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
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Environment variables"))
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
                    p.environmentVariables.map { envVar =>
                        <.tr(
                            <.td(envVar.key),
                            <.td(envVar.value),
                            <.td(
                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                                    ^.onClick --> removeEnvironmentVariable(envVar),
                                    <.i(^.className := "fas fa-minus-circle")
                                )
                            )
                        )
                    }.toTagMod,
                    <.tr(
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                bind(s) := ComponentFormState.environmentVariable / EnvironmentVariable.key,
                                p.environmentVariables.map { envVar =>
                                    option((className := "selected").when(s.environmentVariable.key == envVar.key), envVar.key)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                bind(s) := ComponentFormState.environmentVariable / EnvironmentVariable.value,
                                p.environmentVariables.map { envVar =>
                                    option((className := "selected").when(s.environmentVariable.value == envVar.value), envVar.value)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                                ^.onClick --> addEnvironmentVariable(s.environmentVariable),
                                <.i(^.className := "fas fa-plus-circle")
                            )
                        )
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Ports"))
            ),
            <.table(^.className := "table table-sm",
                <.thead(^.className := "thead-light",
                    <.tr(
                        <.th(^.scope := "col", "External port", ^.width := "45%"),
                        <.th(^.scope := "col", "Internal port", ^.width := "45%"),
                        <.th(^.scope := "col", "", ^.width := "10%"),
                    )
                ),
                //                <.tbody(
                //                    p.parties.map { party =>
                //                        <.tr(
                //                            <.td(party.mspId),
                //                            <.td(party.role),
                //                            <.td(
                //                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                //                                    ^.onClick --> removeParticipant(party),
                //                                    <.i(^.className := "fas fa-minus-circle")
                //                                )
                //                            )
                //                        )
                //                    }.toTagMod,
                //                    <.tr(
                //                        <.td(
                //                            <.select(className := "form-control form-control-sm",
                //                                bind(s) := ContractState.participantCandidate / ContractParticipant.mspId,
                //                                data.organizations.map { organization =>
                //                                    option((className := "selected").when(s.participantCandidate.mspId == organization.mspId), organization.mspId)
                //                                }.toTagMod
                //                            ),
                //                        ),
                //                        <.td(
                //                            <.select(className := "form-control form-control-sm",
                //                                bind(s) := ContractState.participantCandidate / ContractParticipant.role,
                //                                s.roles.map { role =>
                //                                    option((className := "selected").when(s.participantCandidate.role == role), role)
                //                                }.toTagMod
                //                            ),
                //                        ),
                //                        <.td(
                //                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                //                                ^.onClick --> addParticipant(s.participantCandidate),
                //                                <.i(^.className := "fas fa-plus-circle")
                //                            )
                //                        )
                //                    )
                //                )
            ),
            <.div(^.className := "form-group row",
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Volumes"))
            ),
            <.table(^.className := "table table-sm",
                <.thead(^.className := "thead-light",
                    <.tr(
                        <.th(^.scope := "col", "External volume", ^.width := "45%"),
                        <.th(^.scope := "col", "Internal volume", ^.width := "45%"),
                        <.th(^.scope := "col", "", ^.width := "10%"),
                    )
                ),
                //                <.tbody(
                //                    p.parties.map { party =>
                //                        <.tr(
                //                            <.td(party.mspId),
                //                            <.td(party.role),
                //                            <.td(
                //                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                //                                    ^.onClick --> removeParticipant(party),
                //                                    <.i(^.className := "fas fa-minus-circle")
                //                                )
                //                            )
                //                        )
                //                    }.toTagMod,
                //                    <.tr(
                //                        <.td(
                //                            <.select(className := "form-control form-control-sm",
                //                                bind(s) := ContractState.participantCandidate / ContractParticipant.mspId,
                //                                data.organizations.map { organization =>
                //                                    option((className := "selected").when(s.participantCandidate.mspId == organization.mspId), organization.mspId)
                //                                }.toTagMod
                //                            ),
                //                        ),
                //                        <.td(
                //                            <.select(className := "form-control form-control-sm",
                //                                bind(s) := ContractState.participantCandidate / ContractParticipant.role,
                //                                s.roles.map { role =>
                //                                    option((className := "selected").when(s.participantCandidate.role == role), role)
                //                                }.toTagMod
                //                            ),
                //                        ),
                //                        <.td(
                //                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                //                                ^.onClick --> addParticipant(s.participantCandidate),
                //                                <.i(^.className := "fas fa-plus-circle")
                //                            )
                //                        )
                //                    )
                //                )
            )
        )

    private def removeEnvironmentVariable(environmentVariable: EnvironmentVariable)
      (implicit modState: (ComponentCandidate => ComponentCandidate) => Callback)
    : CallbackTo[Unit] =
        modState(
            ComponentCandidate.environmentVariables.modify(
                _.filter(p => !(p.value == environmentVariable.value && p.key == environmentVariable.key))
            )
        )


    private def addEnvironmentVariable(environmentVariable: EnvironmentVariable)
      (implicit modState: (ComponentCandidate => ComponentCandidate) => Callback)
    : CallbackTo[Unit] =
        modState(ComponentCandidate.environmentVariables.modify(_ :+ environmentVariable))


    private def renderComponentType(p: ComponentCandidate)(implicit modState: (ComponentCandidate => ComponentCandidate) => Callback): VdomTagOf[Select] = {
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
