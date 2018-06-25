package net.corda.samples.acl.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.acl.AclService

@InitiatingFlow
@StartableByRPC
class PingFlow(val target: Party) : FlowLogic<Unit>() {
    override val progressTracker: ProgressTracker = ProgressTracker()
    @Suspendable
    override fun call() {
        // We only check that the target is on the whitelist.
        // We don't check whether _WE_ are on the whitelist!
        //val acl = serviceHub.cordaService(AclService::class.java).list
        //if (target.name !in acl) {
        //    throw FlowException("$target is not on the whitelist.")
        //}

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
        // In case a "pinging" node is not on the whitelist.
        val acl = serviceHub.cordaService(AclService::class.java).getAcls()
        println("${otherSession.counterparty.name} !in ${acl}")
        if (otherSession.counterparty.name !in acl) {
            throw FlowException("${otherSession.counterparty} is not on the whitelist.")
        }

        val response = otherSession.receive<String>()
        println("Received $response from ${otherSession.counterparty}")

        println("Sending Pong to ${otherSession.counterparty}!")
        otherSession.send("PONG")
    }
}
