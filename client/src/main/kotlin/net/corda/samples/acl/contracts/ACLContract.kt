package net.corda.samples.acl.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.isValid
import net.corda.core.crypto.keys
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.PartyInfo
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.toBase58String
import net.corda.samples.acl.AclService
import net.corda.samples.acl.contracts.states.ACLState
import net.corda.webserver.converters.CordaX500NameConverter
import java.math.BigDecimal
import java.util.*


val ACL_CONTRACT_ID = "net.corda.samples.acl.contracts.ACLContract"

class ACLContract : Contract {

    override fun verify(tx: LedgerTransaction) {


        val command = tx.commands.requireSingleCommand<Commands>()
        //val timestamp: Timestamp? = tx.timestamp
        //timestamp?.before ?: throw IllegalArgumentException("All transactions must be timestamped")

        val tradeInputs = tx.inputsOfType<ACLState>() //tx.inputs.filterIsInstance<TradeState>()
        val tradeOutputs = tx.outputsOfType<ACLState>() //tx.outputs.filterIsInstance<TradeState>()

        when (command.value) {
            is Commands.Create -> {
                verifyIssue(tx, tradeInputs, tradeOutputs)
            }

            is Commands.Update -> {
                verifyConfirm(tx, tradeInputs, tradeOutputs)
            }

            is Commands.Cancel -> {
                verifyCancel(tx, tradeInputs, tradeOutputs)
            }
            else -> throw IllegalArgumentException("Unrecognised command")
        }

    }

    private fun verifyIssue(tx: LedgerTransaction, tradeInputs: List<ACLState>, tradeOutputs: List<ACLState>) {

        // this admin check needs to use the public key, not the name which could be forged.
        val admin = CordaX500Name("PartyA", "London","GB")


        requireThat {
            "No Inputs should be consumed when issuing a Contract." using (tradeInputs.isEmpty())
            "Exactly one output state should be created." using (tradeOutputs.size == 1)
            "Admin is PartyA" using (tradeOutputs.first().admin.name.equals(admin))

        }

        val command = tx.commands.requireSingleCommand<Commands.Create>()
        val output = tradeOutputs.single()
        requireThat {
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, tradeInputs: List<ACLState>, tradeOutputs: List<ACLState>) {

        // this admin check needs to use the public key, not the name which could be forged.
        val admin = CordaX500Name("PartyA", "London","C=GB")

        requireThat {
            "Exactly one input state should be consumed." using (tradeInputs.size == 1)
            "Exactly one output state should be created." using (tradeOutputs.size == 1)
            "Admin is PartyA on input" using (tradeInputs.first().admin.name.equals(admin))
            "Admin is PartyA on output" using (tradeOutputs.first().admin.name.equals(admin))

        }

        val command = tx.commands.requireSingleCommand<Commands.Create>()
        val output = tradeOutputs.single()

    }

    private fun verifyConfirm(tx: LedgerTransaction, tradeInputs: List<ACLState>, tradeOutputs: List<ACLState>) {
        requireThat {
            "Exactly one input state, which should be confirmed." using (tradeInputs.size == 1)
            "Exactly one output state should be created." using (tradeOutputs.size == 1)

        }

        val command = tx.commands.requireSingleCommand<Commands.Update>()
        val output = tradeOutputs.single()

        requireThat {
            // Generic constraints around initiation of a tradeContract transaction:

        }
    }

    private fun verifyCancel(tx: LedgerTransaction, tradeInputs: List<ACLState>, tradeOutputs: List<ACLState>) {
        //val command = tx.commands.requireSingleCommand<Commands.Cancel>()
        requireThat {
            "Exactly one input state, one contract should be redeemed." using (tradeInputs.size == 1)
            "Exactly zero output states should be created." using (tradeOutputs.isEmpty())
        }
    }


    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Create : Commands
        class Update : Commands
        class Cancel : TypeOnlyCommandData(), Commands  // What to do here?: redeem contract, when both parties agree?
    }
}
