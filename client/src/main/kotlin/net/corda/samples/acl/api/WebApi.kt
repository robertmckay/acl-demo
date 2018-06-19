package net.corda.samples.acl.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.jaxrs.config.BeanConfig
import io.swagger.jaxrs.listing.ApiListingResource
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.filterStatesOfType
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.loggerFor
import net.corda.samples.acl.NewACLFlow
import net.corda.samples.acl.PingFlow
import net.corda.samples.acl.contracts.states.ACLState
import net.corda.samples.acl.flows.ACLCreateFlow
import net.corda.samples.acl.flows.ACLUpdateFlow
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.Ping
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("acl-demo")
@Api("acl-demo")

class AclApi(val rpc : CordaRPCOps) : ApiListingResource() {

    init {
        val bean = BeanConfig()
        bean.version = "0.0.1"
        bean.title = "REST API for ACL Admin"
        bean.description = "REST API to add/remove node permissions"
        bean.schemes = arrayOf("http", "https")
        bean.basePath = "/api"
        bean.resourcePackage = "net.corda.samples.acl.api"
        bean.scan = true
    }


    companion object {
        private val logger: Logger = loggerFor<net.corda.samples.acl.api.AclApi>()
    }

    private val myLegalName: CordaX500Name = rpc.nodeInfo().legalIdentities.first().name

    /**
     * Returns the node's name.
     */
    @GET
    @ApiOperation(
            value = "Get the name of this node"
    )
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @ApiOperation(
            value = "Get a list of peers",
            notes = "This returns a list of connected peer nodes"
    )
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpc.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (myLegalName.organisation) })
    }
    

    @POST
    @Path ("ping")
    @Produces(MediaType.TEXT_PLAIN)
    fun ping(@QueryParam("target") target: String) : String {
        val party = rpc.partiesFromName(target, false).first()
        val retval = rpc.startTrackedFlowDynamic(PingFlow::class.java, party)

        return retval.toString()
    }

    @GET
    @Path("allNodes")
    @Produces(MediaType.TEXT_PLAIN)
    fun getNodes() : String {
        return rpc.vaultQueryBy<ACLState>(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.map { it.state.data }.toString()
    }


    @POST
    @Path("addNode")
    @Produces(MediaType.TEXT_PLAIN)
    fun addNode(@QueryParam("target") target :String) : String {
        val party = rpc.partiesFromName(target, false).first()

        val currentMembers = rpc.vaultQueryBy<ACLState>(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states


        if (currentMembers.isNotEmpty()) {
            // Update
            val parties = currentMembers.first().state.data.members.toList() + party
            return rpc.startTrackedFlowDynamic(ACLUpdateFlow::class.java, parties).toString()
        } else {
            // Create initial ACL
            return  rpc.startTrackedFlowDynamic(ACLCreateFlow::class.java, listOf(party)).toString()
        }

    }


}