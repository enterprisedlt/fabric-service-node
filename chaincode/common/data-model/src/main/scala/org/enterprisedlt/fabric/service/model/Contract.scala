package org.enterprisedlt.fabric.service.model

case class Contract(
    name: String,
    participants: Array[String],
    orgIssuer: String
)

case class InviteRequest(
    from: String,
    participants: Array[String],
    app: String
)

case class InviteResponse(
    responder: String,
    inviteRequester: String,
    app: String
)

case class ContractConfirm(
    from: String,
    participants: Array[String],
    app: String,
    link: String            //endpoint to Application package
)

case class ResponseCheck(    //entity for returning successful count procedure result to MN
    key: String,
    participants: Array[String]
)

case class App(
    name: String
)


