package net.corda.samples.acl.contracts.states

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

data class ACLState(val members : List<Party>) : ContractState {

    override val participants: List<AbstractParty>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

}
