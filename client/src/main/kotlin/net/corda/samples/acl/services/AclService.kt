package net.corda.samples.acl.services

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.samples.acl.contracts.states.ACLState

@CordaService
class AclService(val services: AppServiceHub) : SingletonSerializeAsToken() {


    fun getAcls() = getAclState(services)

    fun getAclState(services: AppServiceHub) : Set<CordaX500Name> {
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

}