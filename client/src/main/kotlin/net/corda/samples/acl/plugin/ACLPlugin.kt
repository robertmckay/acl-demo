package net.corda.samples.acl.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.messaging.CordaRPCOps
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function
import net.corda.samples.acl.api.AclApi

class ACLPlugin : WebServerPluginRegistry {

    init {
        println("Adding webserver plugin")
    }

    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(
     Function(::AclApi)
    )

    override val staticServeDirs: Map<String, String> = mapOf(
           "swagger" to javaClass.classLoader.getResource("swaggerWeb").toExternalForm()
    )

    override fun customizeJSONSerialization(om: ObjectMapper) {
        om.registerSubtypes(java.util.List::class.java)
    }

}