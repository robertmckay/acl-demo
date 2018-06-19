package net.corda.samples.acl.contracts.states

import com.sun.org.apache.xpath.internal.operations.Bool
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

data class ACLState(val members : List<Party>, val admin: Party) : ContractState {

    override val participants: List<AbstractParty>
        get() = listOf(admin)


}
