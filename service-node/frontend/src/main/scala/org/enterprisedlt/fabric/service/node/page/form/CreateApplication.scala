package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.all.{className, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo, ReactEventFromInput}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.shared.{ContractParticipant, CreateApplicationRequest, Property}
import org.enterprisedlt.fabric.service.node.util.DataFunction._

/**
 * @author Maxim Fedin
 * @author Alexey Polubelov
 */

@Lenses case class CreateApplicationState(
    roles: Array[String],
    applicationProperties: Array[Property],
    participantCandidate: ContractParticipant,
    propertyCandidate: Property,
    filename: String
)

object CreateApplication extends StateFullFormExt[CreateApplicationRequest, Ready, CreateApplicationState]("create-application-form") {

    private def stateFor(appType: String, data: Ready): CreateApplicationState = {
        val firstMSPId = data.organizations.headOption.map(_.mspId).getOrElse("")
        data.events.applications.find(_.applicationType == appType).map { descriptor =>
            CreateApplicationState(
                roles = descriptor.applicationRoles,
                applicationProperties = descriptor.properties,
                participantCandidate = ContractParticipant(
                    firstMSPId,
                    descriptor.applicationRoles.headOption.getOrElse("")
                ),
                propertyCandidate = descriptor.properties.headOption.getOrElse(Property("","")), // TODO
                filename = descriptor.applicationType
            )
        }.getOrElse( // could be only if package list is empty or something got wrong :(
            CreateApplicationState(
                roles = Array.empty,
                applicationProperties = Array.empty,
                propertyCandidate = Property("", ""),
                participantCandidate = ContractParticipant(
                    firstMSPId,
                    ""
                ),
                filename = ""
            )
        )
    }

    override def initState(p: CreateApplicationRequest, data: Ready): CreateApplicationState = stateFor(p.applicationType, data)

    override def render(s: CreateApplicationState, p: CreateApplicationRequest, data: Ready)
      (implicit modP: (CreateApplicationRequest => CreateApplicationRequest) => Callback, modS: (CreateApplicationState => CreateApplicationState) => Callback)
    : VdomNode = {
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Name"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := CreateApplicationRequest.name
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Version"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := CreateApplicationRequest.version
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "contractPackages", ^.className := "col-sm-4 col-form-label", "Type"),
                <.div(^.className := "col-sm-8",
                    <.select(className := "form-control",
                        ^.value := p.applicationType,
                        ^.onChange ==> onPackageChange(s, data),
                        data.events.applications.map { application =>
                            val label = application.applicationType
                            val selected = p.applicationType
                            option((className := "selected").when(selected == label), label)
                        }.toTagMod
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Channel"),
                <.div(^.className := "col-sm-8",
                    <.select(
                        ^.className := "form-control",
                        bind(p) := CreateApplicationRequest.channelName,
                        data.channels.map { channel =>
                            option((className := "selected").when(p.channelName == channel), channel)
                        }.toTagMod
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
                    p.properties.map { property =>
                        <.tr(
                            <.td(property.key),
                            <.td(property.value),
                            <.td(
                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                                    ^.onClick --> removeProperty(property),
                                    <.i(^.className := "fas fa-minus-circle")
                                )
                            )
                        )
                    }.toTagMod,
                    <.tr(
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                ^.onChange ==> onPropertyCandidateChange(s.applicationProperties),
                                s.applicationProperties.map { property =>
                                    option((className := "selected").when(s.propertyCandidate.key == property.key), property.key)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                                bind(s) := CreateApplicationState.propertyCandidate / Property.value
                            )
                        ),
                        <.td(
                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                                ^.onClick --> addProperty(s.propertyCandidate),
                                <.i(^.className := "fas fa-plus-circle")
                            )
                        )
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Participants"))
            ),
            <.table(^.className := "table table-sm",
                <.thead(^.className := "thead-light",
                    <.tr(
                        <.th(^.scope := "col", "Organization", ^.width := "45%"),
                        <.th(^.scope := "col", "Role", ^.width := "45%"),
                        <.th(^.scope := "col", "", ^.width := "10%"),
                    )
                ),
                <.tbody(
                    p.parties.map { party =>
                        <.tr(
                            <.td(party.mspId),
                            <.td(party.role),
                            <.td(
                                <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                                    ^.onClick --> removeParticipant(party),
                                    <.i(^.className := "fas fa-minus-circle")
                                )
                            )
                        )
                    }.toTagMod,
                    <.tr(
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                bind(s) := CreateApplicationState.participantCandidate / ContractParticipant.mspId,
                                data.organizations.map { organization =>
                                    option((className := "selected").when(s.participantCandidate.mspId == organization.mspId), organization.mspId)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                bind(s) := CreateApplicationState.participantCandidate / ContractParticipant.role,
                                s.roles.map { role =>
                                    option((className := "selected").when(s.participantCandidate.role == role), role)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                                ^.onClick --> addParticipant(s.participantCandidate),
                                <.i(^.className := "fas fa-plus-circle")
                            )
                        )
                    )
                )
            ),
            <.hr()
        )
    }

    private def removeParticipant(participant: ContractParticipant)
      (implicit modState: (CreateApplicationRequest => CreateApplicationRequest) => Callback)
    : CallbackTo[Unit] =
        modState(
            CreateApplicationRequest.parties.modify(
                _.filter(p => !(p.mspId == participant.mspId && p.role == participant.role))
            )
        )

    private def addParticipant(participant: ContractParticipant)
      (implicit modState: (CreateApplicationRequest => CreateApplicationRequest) => Callback)
    : CallbackTo[Unit] =
        modState(CreateApplicationRequest.parties.modify(_ :+ participant))

    private def onPackageChange(s: CreateApplicationState, data: Ready)(event: ReactEventFromInput)
      (implicit modP: (CreateApplicationRequest => CreateApplicationRequest) => Callback, modS: (CreateApplicationState => CreateApplicationState) => Callback): Callback = {
        val v: String = event.target.value
        modP(_.copy(
            applicationType = v
        )) >> modS(_ => stateFor(v, data))
    }

    private def onPropertyCandidateChange(p: Array[Property])(event: ReactEventFromInput)
      (implicit modS: (CreateApplicationState => CreateApplicationState) => Callback): Callback = {
        val propertyKey: String = event.target.value
        val propertyValue = p.find(_.key == propertyKey).map(_.value).getOrElse("")

        modS(CreateApplicationState.propertyCandidate.modify(_.copy(value = propertyValue)))
    }

    private def removeProperty(environmentVariable: Property)
      (implicit modState: (CreateApplicationRequest => CreateApplicationRequest) => Callback)
    : CallbackTo[Unit] =
        modState(
            CreateApplicationRequest.properties.modify(
                _.filter(p => !(p.value == environmentVariable.value && p.key == environmentVariable.key))
            )
        )


    private def addProperty(property: Property)
      (implicit modP: (CreateApplicationRequest => CreateApplicationRequest) => Callback, modS: (CreateApplicationState => CreateApplicationState) => Callback)
    : CallbackTo[Unit] =
        modP(CreateApplicationRequest.properties.modify(_ :+ property)) >>
          modS(CreateApplicationState.propertyCandidate.modify(_ =>
              Property(
                  key = "",
                  value = ""
              ))
          )
}
