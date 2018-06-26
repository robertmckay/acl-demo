package net.corda.samples.acl.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.acl.services.AclService

@InitiatingFlow
@StartableByRPC
class PingFlow(val target: Party) : FlowLogic<Unit>() {
    override val progressTracker: ProgressTracker = ProgressTracker()
    @Suspendable
    override fun call() {
        // We only check that the target is on the whitelist.
        // We don't check whether _WE_ are on the whitelist!

        checkACL("I must not send a 'PING' to unknown parties: $target is not on the whitelist.", target, serviceHub)

        val session = initiateFlow(target)
        println("Sending PING to $target")
        val response = session.sendAndReceive<String>("PING").unwrap { it }
        println("Received $response from $target")
    }
}

@InitiatedBy(PingFlow::class)
class PongFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    override val progressTracker: ProgressTracker = ProgressTracker()
    @Suspendable
    override fun call() {
        checkACL("I must not answer with 'PONG': ${otherSession.counterparty} is not on the whitelist.", otherSession.counterparty, serviceHub)

        val response = otherSession.receive<String>()
        println("Received $response from ${otherSession.counterparty}")

        println("Sending Pong to ${otherSession.counterparty}!")
        otherSession.send("PONG")
    }

}

fun checkACL(msg: String, otherParty: Party, serviceHub: ServiceHub) {
    // In case a node is not on the whitelist.
    val acl = serviceHub.cordaService(AclService::class.java).getAcls()
    println("acl: ${acl}")
    println("otherParty.name: ${otherParty.name}")
    if (otherParty.name !in acl) {
        throw FlowException(msg)
    }
    return
}
