package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.all.{className, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo, ReactEventFromInput}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.shared.{ContractParticipant, CreateContractRequest}
import org.enterprisedlt.fabric.service.node.util.DataFunction._

import scala.language.higherKinds

/**
 * @author Maxim Fedin
 * @author Alexey Polubelov
 */

@Lenses case class ContractState(
    roles: Array[String],
    initArgsNames: Array[String],
    participantCandidate: ContractParticipant,
)

object CreateContract extends StateFullFormExt[CreateContractRequest, Ready, ContractState]("ContractForm") {

    private def stateFor(ct: String, data: Ready) = {
        val firstMSPId = data.organizations.headOption.map(_.mspId).getOrElse("")
        data.packages.find(_.name == ct).map { descriptor =>
            ContractState(
                roles = descriptor.roles,
                initArgsNames = descriptor.initArgsNames,
                ContractParticipant(
                    firstMSPId,
                    descriptor.roles.headOption.getOrElse("")
                ),
            )
        }.getOrElse( // could be only if package list is empty or something got wrong :(
            ContractState(
                roles = Array.empty,
                initArgsNames = Array.empty,
                ContractParticipant(
                    firstMSPId,
                    ""
                ),
            )
        )
    }

    override def initState(p: CreateContractRequest, data: Ready): ContractState = stateFor(p.contractType, data)

    override def render(s: ContractState, p: CreateContractRequest, data: Ready)
      (implicit modP: (CreateContractRequest => CreateContractRequest) => Callback, modS: (ContractState => ContractState) => Callback)
    : VdomNode = {
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Name"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := CreateContractRequest.name
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Version"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := CreateContractRequest.version
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "contractPackages", ^.className := "col-sm-4 col-form-label", "Type"),
                <.div(^.className := "col-sm-8",
                    <.select(className := "form-control",
                        ^.value := p.contractType,
                        ^.onChange ==> onPackageChange(s, data),
                        data.packages.map { cPackage =>
                            val label = cPackage.name
                            val selected = p.contractType
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
                        bind(p) := CreateContractRequest.channelName,
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
                                bind(s) := ContractState.participantCandidate / ContractParticipant.mspId,
                                data.organizations.map { organization =>
                                    option((className := "selected").when(s.participantCandidate.mspId == organization.mspId), organization.mspId)
                                }.toTagMod
                            ),
                        ),
                        <.td(
                            <.select(className := "form-control form-control-sm",
                                bind(s) := ContractState.participantCandidate / ContractParticipant.role,
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
            <.div(
                <.div(^.className := "form-group row",
                    <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Init arguments"))
                ),
                <.table(^.className := "table table-sm",
                    <.thead(^.className := "thead-light",
                        <.tr(
                            <.th(^.scope := "col", "Name", ^.width := "30%"),
                            <.th(^.scope := "col", "Value", ^.width := "70%"),
                        )
                    ),
                    <.tbody(
                        s.initArgsNames.zipWithIndex.map { case (name, index) =>
                            <.tr(
                                <.td(name),
                                <.td(
                                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                                        bind(p) := CreateContractRequest.initArgs / ForElement[String](index)
                                    )
                                ),
                            )
                        }.toTagMod,
                    ),
                )
            ).when(s.initArgsNames.nonEmpty),
            <.hr()
        )
    }

    private def removeParticipant(participant: ContractParticipant)
      (implicit modState: (CreateContractRequest => CreateContractRequest) => Callback)
    : CallbackTo[Unit] =
        modState(
            CreateContractRequest.parties.modify(
                _.filter(p => !(p.mspId == participant.mspId && p.role == participant.role))
            )
        )


    private def addParticipant(participant: ContractParticipant)
      (implicit modState: (CreateContractRequest => CreateContractRequest) => Callback)
    : CallbackTo[Unit] =
        modState(CreateContractRequest.parties.modify(_ :+ participant))

    private def onPackageChange(s: ContractState, data: Ready)(event: ReactEventFromInput)
      (implicit modP: (CreateContractRequest => CreateContractRequest) => Callback, modS: (ContractState => ContractState) => Callback): Callback = {
        val v: String = event.target.value
        modP(_.copy(contractType = v)) >> modS(_ => stateFor(v, data))
    }

}
