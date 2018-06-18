package net.corda.samples.acl

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.openHttpConnection
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.samples.acl.contracts.ACLContract
import net.corda.samples.acl.contracts.ACL_CONTRACT_ID
import net.corda.samples.acl.contracts.states.ACLState
import org.dom4j.tree.AbstractNode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.util.*


@InitiatingFlow
@StartableByService
class SendACL(val target: Party, val aclState: StateRef) : FlowLogic<Unit>() {
    override val progressTracker: ProgressTracker = ProgressTracker()
    @Suspendable
    override fun call() {
        val session = initiateFlow(target)
        println("Sending ACL to $target")

        val aclTx = serviceHub.validatedTransactions.getTransaction(aclState.txhash)
        val response = session.sendAndReceive<SignedTransaction>(aclTx!!).unwrap { it }

        println("Received $response from $target")
    }
}


class AcceptACL(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val stx = otherPartyFlow.receive<SignedTransaction>().unwrap { it }
        stx.tx.toLedgerTransaction(serviceHub).verify() // must pass contract validation
        return subFlow(FinalityFlow(stx))

    }
}

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
        //val acl = serviceHub.cordaService(AclService::class.java).list
        //if (otherSession.counterparty.name !in acl) {
        //    throw FlowException("${otherSession.counterparty} is not on the whitelist.")
        //}

        val response = otherSession.receive<String>()
        println("Received $response from ${otherSession.counterparty}")

        println("Sending Pong to ${otherSession.counterparty}!")
        otherSession.send("PONG")
    }
}

@InitiatingFlow
@StartableByService
class NewACLFlow(val initialParties : List<Party>) : FlowLogic<SignedTransaction>() {


    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction to create ACL")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
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

        val aclState = ACLState(initialParties)

        val txCommand = Command(ACLContract.Commands.Create(), aclState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(aclState, ACL_CONTRACT_ID), txCommand)

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
        return  subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))

    }

}

@CordaService
class AclDistribution(val services: AppServiceHub) : SingletonSerializeAsToken() {

    private lateinit var pinObserverble: Any

    private val task = object : TimerTask() {
        override fun run() {
            Thread.sleep(30000)
           //distributeACLState(services, getCurrentAcl(services))
        }
    }

    init {
        // PartyA is the ACL master.. it is the only node that needs to run the ACL distribution service
        val me = services.identityService.getAllIdentities().first().toString()

        if (me.startsWith("O=PartyA")) {

            //distributeACLState(services, getCurrentAcl(services))

            val (snapshot, updates) = services.networkMapCache.track()
            pinObserverble = updates

            println("Distributing ACL to all nodes in networkMap")
            snapshot.forEach { node ->
                //services.startTrackedFlow(SendACL(node.legalIdentities.first(), aclState))
                sendACL(node.legalIdentities.first())
            }

            // never exits - send acl to any node that joins the network
            println("Waiting for networkMap updates")

            updates.forEach({update ->
                sendACL(update.node.legalIdentities.first())
            })

        }

    }

    private fun sendACL(party: Party) {
        var acl = getCurrentAcl()
        //services.cordappProvider.getAppContext().cordapp.
        var stx = services.startFlow(NewACLFlow(listOf()))
        stx.returnValue.then { println(it) }

        //services.startTrackedFlow(SendACL(party, acl))
    }


    private fun getCurrentAcl() : StateRef? {
        val acls = services.vaultService.queryBy<ACLState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        var acl = acls.states.singleOrNull() // if there is more than one unconsumed ACLState then this is bad

        // There is no ACL yet, so create it with a new transaction
        //if (acl==null) {
            //val me = services.identityService.getAllIdentities().first().party
        //    var stx = services.startTrackedFlow(NewACLFlow(listOf())).returnValue.getOrThrow(timeout = Duration.ofSeconds(30))
        //    acl = stx.tx.outRef<ACLState>(0)
        //}


        if (acl!=null) {
            return acl!!.ref
        }

        return null

    }

    private fun getAclState(services: AppServiceHub) : Set<CordaX500Name> {
        val acls = services.vaultService.queryBy<ACLState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        val acl = acls.states.singleOrNull() // if there is more than one unconsumed ACLState then this is bad

        var cordaNames = setOf<CordaX500Name>()

        if (acl!=null) {
            acl.state.data.members.forEach() { party ->
                cordaNames += party.name
            }

        }

        return cordaNames

    }


    private fun distributeACLState(services: AppServiceHub, aclState: StateRef) : Unit {
        val (snapshot, updates) = services.networkMapCache.track()
        pinObserverble = updates

        println("Distributing ACL to all nodes in networkMap")
        snapshot.forEach { node ->
            //services.startTrackedFlow(SendACL(node.legalIdentities.first(), aclState))
            println("Would invoke SendACL for ${node.legalIdentities.first()} acl: ${aclState}")
        }

        // never exits - send acl to any node that joins the network
        println("Waiting for networkMap updates")

        updates.forEach({update ->
            println("Updating ACL for ${update.node.legalIdentities.first().toString()}")
        })


        //    services.startTrackedFlow(SendACL(update.node.legalIdentities.first(), aclState))

        addShutdownHook { println("Shutting down cordapp") }

    }


}