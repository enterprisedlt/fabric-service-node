package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.all.{className, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo, ReactEventFromInput}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.shared.{ContractParticipant, CreateApplicationRequest}
import org.enterprisedlt.fabric.service.node.util.DataFunction._

/**
 * @author Maxim Fedin
 * @author Alexey Polubelov
 */

@Lenses case class ApplicationState(
    roles: Array[String],
    participantCandidate: ContractParticipant,
    filename: String
)

object CreateApplication extends StateFullFormExt[CreateApplicationRequest, Ready, ApplicationState]("create-application-form") {

    private def stateFor(appType: String, data: Ready): ApplicationState = {
        val firstMSPId = data.organizations.headOption.map(_.mspId).getOrElse("")
        data.events.applications.find(_.filename == appType).map { descriptor =>
            ApplicationState(
                roles = descriptor.applicationRoles,
                participantCandidate = ContractParticipant(
                    firstMSPId,
                    descriptor.applicationRoles.headOption.getOrElse("")
                ),
                filename = descriptor.filename
            )
        }.getOrElse( // could be only if package list is empty or something got wrong :(
            ApplicationState(
                roles = Array.empty,
                participantCandidate = ContractParticipant(
                    firstMSPId,
                    ""
                ),
                filename = ""
            )
        )
    }

    override def initState(p: CreateApplicationRequest, data: Ready): ApplicationState = stateFor(p.applicationType, data)

    override def render(s: ApplicationState, p: CreateApplicationRequest, data: Ready)
      (implicit modP: (CreateApplicationRequest => CreateApplicationRequest) => Callback, modS: (ApplicationState => ApplicationState) => Callback)
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
                            val label = application.filename
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
                                bind(s) := ApplicationState.participantCandidate / ContractParticipant.mspId,
                                data.organizations.map { organization =>
                                    option((className := "selected").when(s.participantCandidate.mspId == organization.mspId), organization.mspId)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                bind(s) := ApplicationState.participantCandidate / ContractParticipant.role,
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

    private def onPackageChange(s: ApplicationState, data: Ready)(event: ReactEventFromInput)
      (implicit modP: (CreateApplicationRequest => CreateApplicationRequest) => Callback, modS: (ApplicationState => ApplicationState) => Callback): Callback = {
        val v: String = event.target.value
        modP(_.copy(
            applicationType = v
        )) >> modS(_ => stateFor(v, data))
    }

}
