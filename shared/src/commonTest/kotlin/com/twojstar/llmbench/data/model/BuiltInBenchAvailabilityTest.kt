package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltInBenchAvailabilityTest {
    @Test
    fun localDocbenchRequiresContentGrantButNotNetworkAvailability() {
        val blocked = BuiltInBenchTool.DOCBENCH_DOCUMENT.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false
        )

        assertFalse(blocked.canOffer)
        assertEquals(
            setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            blocked.missingRequiredPermissions
        )
        assertEquals(
            setOf(BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION),
            blocked.blockers
        )

        val available = BuiltInBenchTool.DOCBENCH_DOCUMENT.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            networkAvailable = false
        )

        assertTrue(available.canOffer)
    }

    @Test
    fun currentRegistryNeverOffersModelToolCalling() {
        BuiltInBenchTool.entries.forEach { tool ->
            val availability = tool.availability(
                surface = tool.capabilities().surfaces.first(),
                invocationMode = BenchToolInvocationMode.MODEL_TOOL_CALL,
                isEnabled = true,
                grantedPermissions = BenchToolPermission.entries.toSet(),
                networkAvailable = true
            )

            assertFalse(availability.canOffer)
            assertTrue(
                BenchToolAvailabilityBlocker.UNSUPPORTED_INVOCATION_MODE in availability.blockers
            )
        }
    }

    @Test
    fun streambenchSeparatesNetworkScopeFromActualConnectivity() {
        val missingScopeAndOffline = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.COMPANION_UI,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false
        )

        assertEquals(
            setOf(BenchToolPermission.NETWORK),
            missingScopeAndOffline.missingRequiredPermissions
        )
        assertEquals(
            setOf(
                BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION,
                BenchToolAvailabilityBlocker.NETWORK_UNAVAILABLE
            ),
            missingScopeAndOffline.blockers
        )

        val online = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.COMPANION_UI,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true
        )

        assertTrue(online.canOffer)
    }

    @Test
    fun unsupportedSurfaceAndDisabledStateAreIndependentBlockers() {
        val availability = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            isEnabled = false,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true
        )

        assertFalse(availability.canOffer)
        assertEquals(
            setOf(
                BenchToolAvailabilityBlocker.DISABLED,
                BenchToolAvailabilityBlocker.UNSUPPORTED_SURFACE
            ),
            availability.blockers
        )
    }

    @Test
    fun optionalPermissionsNeverBlockTheBaseAction() {
        val availability = BuiltInBenchTool.CODEBENCH_QR_BARCODE.availability(
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false
        )

        assertTrue(availability.canOffer)
        assertTrue(availability.missingRequiredPermissions.isEmpty())
        assertTrue(availability.blockers.isEmpty())
    }
}
