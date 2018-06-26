package net.corda.samples.acl.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.acl.contracts.states.ACLState


@InitiatingFlow
class ACLDistibutionFlow(val transaction: SignedTransaction,
                         override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {

        companion object {
            object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to participants")

            @JvmStatic
            fun tracker() = ProgressTracker(BROADCASTING)
        }

    @Suspendable
    override fun call(): SignedTransaction {

        println("Attempting broadcast ACL distribution")
        val parties = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }
        progressTracker.currentStep = ACLDistibutionFlow.Companion.BROADCASTING
        for (party in parties) {
            println("Sending ACL to ${party.name}")
            if (!serviceHub.myInfo.isLegalIdentity(party)) {
                val session = initiateFlow(party)
                try {
                    subFlow(SendTransactionFlow(session, transaction))
                } catch (uefe: UnexpectedFlowEndException) {
                    println(uefe.toString())
                }
            }
        }

        return transaction

    }


}

@InitiatedBy(ACLDistibutionFlow::class)
class ACLDistributionReceiver(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val stx = subFlow(ReceiveTransactionFlow(otherSession,true, StatesToRecord.ALL_VISIBLE))
        println("Receiving NewACL tx: ${stx.tx.outputsOfType<ACLState>().first().members}")
        stx.tx.toLedgerTransaction(serviceHub).verify()
        println("verify didn't throw yet..")

        //serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(stx))

        println("Recorded tx: ${stx.tx.id}")

    }
}