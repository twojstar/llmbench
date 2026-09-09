package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class BuiltInBenchCapabilitiesTest {
    @Test
    fun accountWebChatToolsRemainExplicitUserBridges() {
        builtInBenchToolsForSurface(BenchToolSurface.ACCOUNT_WEB_CHAT).forEach { tool ->
            val capabilities = tool.capabilities()
            assertTrue(BenchToolInvocationMode.EXPLICIT_USER_ACTION in capabilities.invocationModes)
            assertFalse(BenchToolInvocationMode.MODEL_TOOL_CALL in capabilities.invocationModes)
        }
    }

    @Test
    fun accountWebChatCapabilityRejectsMixedModelInvocation() {
        assertFailsWith<IllegalArgumentException> {
            capability(
                surfaces = setOf(BenchToolSurface.ACCOUNT_WEB_CHAT),
                invocationModes = setOf(
                    BenchToolInvocationMode.EXPLICIT_USER_ACTION,
                    BenchToolInvocationMode.MODEL_TOOL_CALL
                )
            )
        }
    }

    @Test
    fun noBuiltInBenchClaimsModelToolCallingBeforeTransportSupportExists() {
        BuiltInBenchTool.entries.forEach { tool ->
            assertFalse(BenchToolInvocationMode.MODEL_TOOL_CALL in tool.capabilities().invocationModes)
        }
    }

    @Test
    fun localBenchToolsDoNotRequestNetwork() {
        listOf(
            BuiltInBenchTool.DOCBENCH_DOCUMENT,
            BuiltInBenchTool.DOCBENCH_TEXT_INSPECTOR,
            BuiltInBenchTool.CODEBENCH_QR_BARCODE
        ).forEach { tool ->
            val capabilities = tool.capabilities()
            assertEquals(BenchToolNetworkBehavior.LOCAL_ONLY, capabilities.networkBehavior)
            assertFalse(BenchToolPermission.NETWORK in capabilities.requiredPermissions)
            assertFalse(BenchToolPermission.NETWORK in capabilities.optionalPermissions)
        }
    }

    @Test
    fun localOnlyCapabilityRejectsOptionalNetworkPermission() {
        assertFailsWith<IllegalArgumentException> {
            capability(
                optionalPermissions = setOf(BenchToolPermission.NETWORK),
                networkBehavior = BenchToolNetworkBehavior.LOCAL_ONLY
            )
        }
    }

    @Test
    fun streambenchRemainsACompanionSurfaceWithExplicitNetworkScope() {
        val capabilities = BuiltInBenchTool.STREAMBENCH_PLAYER.capabilities()

        assertEquals(setOf(BenchToolSurface.COMPANION_UI), capabilities.surfaces)
        assertEquals(BenchToolNetworkBehavior.NETWORK_REQUIRED, capabilities.networkBehavior)
        assertTrue(BenchToolPermission.NETWORK in capabilities.requiredPermissions)
        assertEquals(
            setOf(BenchToolInvocationMode.EXPLICIT_USER_ACTION),
            capabilities.invocationModes
        )
    }

    @Test
    fun capabilityCallsReturnIndependentCollectionSnapshots() {
        val first = BuiltInBenchTool.DOCBENCH_DOCUMENT.capabilities()
        val second = BuiltInBenchTool.DOCBENCH_DOCUMENT.capabilities()

        assertEquals(first.inputs, second.inputs)
        assertEquals(first.surfaces, second.surfaces)
        assertNotSame(first.inputs, second.inputs)
        assertNotSame(first.surfaces, second.surfaces)
    }

    private fun capability(
        optionalPermissions: Set<BenchToolPermission> = emptySet(),
        networkBehavior: BenchToolNetworkBehavior = BenchToolNetworkBehavior.LOCAL_ONLY,
        surfaces: Set<BenchToolSurface> = setOf(BenchToolSurface.NATIVE_CHAT),
        invocationModes: Set<BenchToolInvocationMode> =
            setOf(BenchToolInvocationMode.EXPLICIT_USER_ACTION)
    ) = BuiltInBenchToolCapabilities(
        displayName = "Test tool",
        inputs = setOf(BenchToolDataKind.TEXT),
        outputs = setOf(BenchToolDataKind.TEXT),
        requiredPermissions = emptySet(),
        optionalPermissions = optionalPermissions,
        networkBehavior = networkBehavior,
        surfaces = surfaces,
        invocationModes = invocationModes
    )
}
