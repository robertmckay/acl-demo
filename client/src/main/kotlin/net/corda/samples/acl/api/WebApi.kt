package net.corda.samples.acl.api

import io.swagger.annotations.Api
import io.swagger.jaxrs.config.BeanConfig
import io.swagger.jaxrs.listing.ApiListingResource
import net.corda.core.flows.FlowLogic
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

    @GET
    @Path ("test")
    @Produces(MediaType.TEXT_PLAIN)
    fun test() = "This is a test"

    @POST
    @Path ("test")
    @Produces(MediaType.TEXT_PLAIN)
    fun testp(@QueryParam("name") target: String) : String {
        return "Hello, ${target}"
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

        val retval = rpc.startTrackedFlowDynamic(NewACLFlow::class.java, listOf(party))

        return retval.toString()
    }



}