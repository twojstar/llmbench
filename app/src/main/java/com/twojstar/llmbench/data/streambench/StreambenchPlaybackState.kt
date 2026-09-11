package com.twojstar.llmbench.data.streambench

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class StreambenchPlaybackSnapshot(
    val active: Boolean = false,
    val title: String = "",
    val group: String = "",
    val isPlaying: Boolean = false
)

/** Lightweight in-process mirror of the active Media3 session for Compose chrome. */
internal object StreambenchPlaybackState {
    private val mutableState = MutableStateFlow(StreambenchPlaybackSnapshot())
    val state: StateFlow<StreambenchPlaybackSnapshot> = mutableState.asStateFlow()

    fun setMedia(title: String, group: String) {
        mutableState.value = StreambenchPlaybackSnapshot(
            active = true,
            title = title,
            group = group,
            isPlaying = false
        )
    }

    fun setPlaying(isPlaying: Boolean) {
        mutableState.update { current ->
            if (current.active) current.copy(isPlaying = isPlaying) else current
        }
    }

    fun clear() {
        mutableState.value = StreambenchPlaybackSnapshot()
    }
}
