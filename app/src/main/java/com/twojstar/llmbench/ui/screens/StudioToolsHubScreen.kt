package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel

internal enum class StudioToolsSection {
    SKILLS,
    BENCHES
}

/**
 * Exact-arity entry point used by MainActivity for the existing Studio tools destination.
 *
 * The older SkillsBrowserScreen(viewModel, modifier) remains the maintained Skills UI. Passing an
 * explicit modifier below selects that overload, while this wrapper adds Benches without copying or
 * restructuring the existing browser.
 */
@Composable
fun SkillsBrowserScreen(viewModel: StudioViewModel) {
    var section by rememberSaveable { mutableStateOf(StudioToolsSection.SKILLS) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = section.ordinal) {
            StudioToolsSection.entries.forEach { candidate ->
                Tab(
                    selected = section == candidate,
                    onClick = { section = candidate },
                    text = {
                        Text(
                            when (candidate) {
                                StudioToolsSection.SKILLS -> "Skills"
                                StudioToolsSection.BENCHES -> "Benches"
                            }
                        )
                    }
                )
            }
        }

        when (section) {
            StudioToolsSection.SKILLS -> SkillsBrowserScreen(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
            StudioToolsSection.BENCHES -> BenchToolsScreen(
                modifier = Modifier.weight(1f)
            )
        }
    }
}
