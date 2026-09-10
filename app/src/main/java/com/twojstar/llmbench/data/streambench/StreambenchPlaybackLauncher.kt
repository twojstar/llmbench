package com.twojstar.llmbench.data.streambench

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface

/** Execute the explicit-user playback policy and hand eligible requests to the guarded Media3 service. */
internal fun launchStreambenchPlayback(
    context: Context,
    entry: StreambenchPlaylistEntry,
    isEnabled: Boolean
): String {
    val result = StreambenchPlaybackRequestAction.execute(
        entry = entry,
        surface = BenchToolSurface.COMPANION_UI,
        isEnabled = isEnabled,
        grantedPermissions = setOf(BenchToolPermission.NETWORK),
        networkAvailable = context.hasValidatedInternet()
    )

    return when (result) {
        is StreambenchPlaybackRequestActionResult.EligibleForGuardedLoader -> runCatching {
            StreambenchPlaybackService.play(context, result.request)
            "Playback started. Media controls stay available outside this screen."
        }.getOrElse {
            "Could not start Streambench playback."
        }
        is StreambenchPlaybackRequestActionResult.Blocked -> {
            if (BenchToolAvailabilityBlocker.NETWORK_UNAVAILABLE in result.availability.blockers) {
                "A validated internet connection is required for playback."
            } else {
                "Playback is blocked by the current Bench policy."
            }
        }
        is StreambenchPlaybackRequestActionResult.Rejected ->
            "This stream URL is not allowed for playback."
    }
}

private fun Context.hasValidatedInternet(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
