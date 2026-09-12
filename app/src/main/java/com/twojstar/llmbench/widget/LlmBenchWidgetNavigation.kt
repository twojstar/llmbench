package com.twojstar.llmbench.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.twojstar.llmbench.MainActivity
import com.twojstar.llmbench.ui.viewmodel.NavigationTab

internal object LlmBenchWidgetNavigation {
    const val ACTION_OPEN_DESTINATION = "com.twojstar.llmbench.action.OPEN_WIDGET_DESTINATION"
    const val EXTRA_DESTINATION = "com.twojstar.llmbench.extra.WIDGET_DESTINATION"

    const val DESTINATION_WEB_AI = "web_ai"
    const val DESTINATION_COMPARE = "compare"
    const val DESTINATION_STUDIO = "studio"

    fun launchIntent(context: Context, destination: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DESTINATION
            data = Uri.parse("llmbench://widget/$destination")
            putExtra(EXTRA_DESTINATION, destination)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun destination(value: String?): NavigationTab? = when (value) {
        DESTINATION_WEB_AI -> NavigationTab.WEB_CHATS
        DESTINATION_COMPARE -> NavigationTab.COMPARE_HUB
        DESTINATION_STUDIO -> NavigationTab.STUDIO
        else -> null
    }
}
