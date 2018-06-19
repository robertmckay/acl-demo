package net.corda.samples.acl.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.acl.contracts.ACLContract
import net.corda.samples.acl.contracts.ACL_CONTRACT_ID
import net.corda.samples.acl.contracts.states.ACLState

@InitiatingFlow
@StartableByService
@StartableByRPC
class ACLUpdateFlow(val initialParties : List<Party>) : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction to create ACL")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
        object BROADCASTING_TRANSACTION : ProgressTracker.Step("Sending transaction to non-participants (everyone)") {
            override fun childProgressTracker() = ACLDistibutionFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION,
                BROADCASTING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val me = serviceHub.identityService.getAllIdentities().first().party

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.

        val aclState = ACLState(initialParties, me)

        val oldAclStates = serviceHub.vaultService.queryBy<ACLState>(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.single()

        val txCommand = Command(ACLContract.Commands.Update(), aclState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(oldAclStates, StateAndContract(aclState, ACL_CONTRACT_ID), txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val fullySignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in both parties' vaults.
        val stx = subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))

        // Stage 6.
        // Each transaction has its own set of recipients, but ACL transactions should
        // be sent to everyone on the network, not just participants.

        subFlow(ACLDistibutionFlow(fullySignedTx, BROADCASTING_TRANSACTION.childProgressTracker()))

        return stx

    }

}


@InitiatedBy(ACLUpdateFlow::class)
class ACLUpdateReceiver(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val stx = subFlow(ReceiveTransactionFlow(otherSession,true, StatesToRecord.ALL_VISIBLE))
        println("Receiving NewACL tx: ${stx.tx.outputsOfType<ACLState>().first().members}")
        stx.tx.toLedgerTransaction(serviceHub).verify()
        println("verify didn't throw yet..")

        //serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(stx))

        println("Recorded tx: ${stx.tx.id.toString()}")

    }
}
