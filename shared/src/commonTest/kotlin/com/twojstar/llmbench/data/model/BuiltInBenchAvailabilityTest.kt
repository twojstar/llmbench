package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltInBenchAvailabilityTest {
    @Test
    fun localDocbenchRequiresContentGrantButNotNetworkAvailability() {
        val blocked = BuiltInBenchTool.DOCBENCH_DOCUMENT.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.DOCUMENT,
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
            inputKind = BenchToolDataKind.DOCUMENT,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            networkAvailable = false
        )

        assertTrue(available.canOffer)
    }

    @Test
    fun currentRegistryNeverOffersModelToolCalling() {
        BuiltInBenchTool.entries.forEach { tool ->
            val capabilities = tool.capabilities()
            val availability = tool.availability(
                surface = capabilities.surfaces.first(),
                invocationMode = BenchToolInvocationMode.MODEL_TOOL_CALL,
                inputKind = capabilities.inputs.first(),
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
    fun streambenchBaseCapabilityLeavesNetworkToConcreteActions() {
        val availability = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.COMPANION_UI,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.PLAYLIST,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false
        )

        assertTrue(availability.canOffer)
        assertTrue(availability.missingRequiredPermissions.isEmpty())
        assertTrue(availability.blockers.isEmpty())
    }

    @Test
    fun concreteActionCanRequireDeclaredNetworkScopeAndConnectivity() {
        val missingScopeAndOffline = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.COMPANION_UI,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.MEDIA_STREAM,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false,
            actionRequiredPermissions = setOf(BenchToolPermission.NETWORK)
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

        val grantedButOffline = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.COMPANION_UI,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.MEDIA_STREAM,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = false,
            actionRequiredPermissions = setOf(BenchToolPermission.NETWORK)
        )

        assertEquals(
            setOf(BenchToolAvailabilityBlocker.NETWORK_UNAVAILABLE),
            grantedButOffline.blockers
        )

        val online = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.COMPANION_UI,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.MEDIA_STREAM,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true,
            actionRequiredPermissions = setOf(BenchToolPermission.NETWORK)
        )

        assertTrue(online.canOffer)
    }

    @Test
    fun unsupportedSurfaceAndDisabledStateAreIndependentBlockers() {
        val availability = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.MEDIA_STREAM,
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
    fun unsupportedInputIsAnIndependentBlocker() {
        val unsupported = BuiltInBenchTool.DOCBENCH_TEXT_INSPECTOR.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.IMAGE,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            networkAvailable = true
        )

        assertFalse(unsupported.canOffer)
        assertEquals(
            setOf(BenchToolAvailabilityBlocker.UNSUPPORTED_INPUT),
            unsupported.blockers
        )

        val supported = BuiltInBenchTool.DOCBENCH_TEXT_INSPECTOR.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.TEXT,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            networkAvailable = true
        )

        assertTrue(supported.canOffer)
    }

    @Test
    fun optionalPermissionsNeverBlockTheBaseAction() {
        val availability = BuiltInBenchTool.CODEBENCH_QR_BARCODE.availability(
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.TEXT,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false
        )

        assertTrue(availability.canOffer)
        assertTrue(availability.missingRequiredPermissions.isEmpty())
        assertTrue(availability.blockers.isEmpty())
    }

    @Test
    fun concreteActionCanPromoteADeclaredOptionalPermission() {
        val blocked = BuiltInBenchTool.CODEBENCH_QR_BARCODE.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.IMAGE,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false,
            actionRequiredPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
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

        val available = BuiltInBenchTool.CODEBENCH_QR_BARCODE.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
            inputKind = BenchToolDataKind.IMAGE,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            networkAvailable = false,
            actionRequiredPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        assertTrue(available.canOffer)
    }

    @Test
    fun concreteActionCannotRequireAnUndeclaredPermission() {
        assertFailsWith<IllegalArgumentException> {
            BuiltInBenchTool.DOCBENCH_TEXT_INSPECTOR.availability(
                surface = BenchToolSurface.NATIVE_CHAT,
                invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
                inputKind = BenchToolDataKind.TEXT,
                isEnabled = true,
                grantedPermissions = emptySet(),
                networkAvailable = false,
                actionRequiredPermissions = setOf(BenchToolPermission.CAMERA)
            )
        }
    }
}
