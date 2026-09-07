package com.twojstar.llmbench.ui.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twojstar.llmbench.data.document.DocumentDiagnostics
import com.twojstar.llmbench.data.document.LineEnding
import com.twojstar.llmbench.data.document.MarkdownDocumentFileAccess
import com.twojstar.llmbench.data.document.MarkdownWorkspaceRecoverySnapshot
import com.twojstar.llmbench.data.document.MarkdownWorkspaceRecoveryStore
import com.twojstar.llmbench.data.document.TextDocument
import com.twojstar.llmbench.data.document.TextDocumentCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val DEFAULT_MARKDOWN_NAME = "untitled.md"
private const val RECOVERY_DEBOUNCE_MS = 650L
internal const val MAX_EDITABLE_MARKDOWN_CHARS = 1_000_000

data class MarkdownWorkspaceUiState(
    val text: String = "",
    val hadUtf8Bom: Boolean = false,
    val displayName: String = DEFAULT_MARKDOWN_NAME,
    val isDirty: Boolean = false,
    val revision: Long = 0L,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val isRecoveryLoading: Boolean = false
) {
    val isBusy: Boolean
        get() = isRecoveryLoading || isExporting || isImporting

    val isLargeDocumentReadOnly: Boolean
        get() = text.length > MAX_EDITABLE_MARKDOWN_CHARS

    val isEditorLocked: Boolean
        get() = isRecoveryLoading || isImporting || isLargeDocumentReadOnly
}

data class MarkdownExportSnapshot(
    val operationId: Long,
    val revision: Long,
    val document: TextDocument,
    val displayName: String
)

class MarkdownWorkspaceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MarkdownWorkspaceUiState())
    val uiState: StateFlow<MarkdownWorkspaceUiState> = _uiState.asStateFlow()

    private var recoveryStore: MarkdownWorkspaceRecoveryStore? = null
    private var recoveryLoadJob: Job? = null
    private var recoveryJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var nextExportOperationId = 1L
    private var activeExportOperationId: Long? = null

    private val recoveryLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    flushRecoveryNow()
                } catch (_: IOException) {
                    // Local recovery is best-effort; stopping the activity must remain safe.
                }
            }
        }
    }

    internal fun attachRecoveryStore(store: MarkdownWorkspaceRecoveryStore) {
        val shouldAttach = synchronized(this) {
            if (recoveryStore != null) {
                false
            } else {
                recoveryStore = store
                _uiState.value = _uiState.value.copy(isRecoveryLoading = true)
                true
            }
        }
        if (!shouldAttach) return

        recoveryLoadJob = viewModelScope.launch {
            val result = runCatching { store.load() }
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            val snapshot = result.getOrNull()
            synchronized(this@MarkdownWorkspaceViewModel) {
                val state = _uiState.value
                _uiState.value = if (snapshot != null && state.isPristineForRecovery()) {
                    recoveredState(snapshot)
                } else {
                    state.copy(isRecoveryLoading = false)
                }
            }
        }
    }

    internal fun attachLifecycle(owner: LifecycleOwner) {
        synchronized(this) {
            if (lifecycleOwner === owner) return
            lifecycleOwner?.lifecycle?.removeObserver(recoveryLifecycleObserver)
            lifecycleOwner = owner
            owner.lifecycle.addObserver(recoveryLifecycleObserver)
        }
    }

    internal suspend fun flushRecoveryNow() = withContext(NonCancellable) {
        val store = recoveryStore ?: return@withContext
        recoveryLoadJob?.join()
        if (_uiState.value.isRecoveryLoading) return@withContext
        recoveryJob?.cancelAndJoin()
        store.save(recoverySnapshot())
    }

    fun newDocument() {
        val changed = synchronized(this) {
            val state = _uiState.value
            if (state.isBusy) return@synchronized false
            _uiState.value = MarkdownWorkspaceUiState(revision = state.revision + 1)
            true
        }
        if (changed) scheduleRecovery(delayMs = 0L)
    }

    fun beginImport(): Boolean = synchronized(this) {
        val state = _uiState.value
        if (state.isBusy) return@synchronized false
        _uiState.value = state.copy(isImporting = true)
        true
    }

    fun cancelImport() {
        _uiState.update { state ->
            if (state.isImporting) state.copy(isImporting = false) else state
        }
    }

    fun completeImport(displayName: String, document: TextDocument): Boolean {
        val changed = synchronized(this) {
            val state = _uiState.value
            if (!state.isImporting) return@synchronized false
            _uiState.value = MarkdownWorkspaceUiState(
                text = document.text,
                hadUtf8Bom = document.hadUtf8Bom,
                displayName = MarkdownDocumentFileAccess.normalizeDisplayName(displayName),
                isDirty = false,
                revision = state.revision + 1
            )
            true
        }
        if (changed) scheduleRecovery(delayMs = 0L)
        return changed
    }

    fun updateText(text: String): Boolean {
        var changed = false
        _uiState.update { state ->
            if (
                state.isEditorLocked ||
                state.text == text ||
                text.length > MAX_EDITABLE_MARKDOWN_CHARS
            ) {
                state
            } else {
                changed = true
                state.copy(text = text, isDirty = true, revision = state.revision + 1)
            }
        }
        if (changed) scheduleRecovery()
        return changed
    }

    fun currentDocument(): TextDocument = documentFrom(_uiState.value)

    fun normalizeLineEndings(target: LineEnding): Boolean {
        val before = _uiState.value
        if (before.isEditorLocked) return false
        val repaired = DocumentDiagnostics.repair(documentFrom(before), normalizeTo = target)
        if (repaired.document.text == before.text) return false
        if (repaired.document.text.length > MAX_EDITABLE_MARKDOWN_CHARS) return false
        var changed = false
        _uiState.update { state ->
            if (state.revision != before.revision || state.isEditorLocked) {
                state
            } else {
                changed = true
                state.copy(
                    text = repaired.document.text,
                    isDirty = true,
                    revision = state.revision + 1
                )
            }
        }
        if (changed) scheduleRecovery()
        return changed
    }

    fun beginExport(): MarkdownExportSnapshot? = synchronized(this) {
        val state = _uiState.value
        if (state.isBusy) return@synchronized null
        val operationId = nextExportOperationId++
        activeExportOperationId = operationId
        val snapshot = MarkdownExportSnapshot(
            operationId = operationId,
            revision = state.revision,
            document = documentFrom(state),
            displayName = state.displayName
        )
        _uiState.value = state.copy(isExporting = true)
        snapshot
    }

    fun completeExport(snapshot: MarkdownExportSnapshot, displayName: String): Boolean {
        val stillCurrent = synchronized(this) {
            val state = _uiState.value
            if (!state.isExporting || activeExportOperationId != snapshot.operationId) return@synchronized false
            activeExportOperationId = null
            val current = state.revision == snapshot.revision
            _uiState.value = if (current) {
                state.copy(
                    displayName = MarkdownDocumentFileAccess.normalizeDisplayName(displayName),
                    isDirty = false,
                    isExporting = false
                )
            } else {
                state.copy(isExporting = false)
            }
            current
        }
        if (stillCurrent) scheduleRecovery(delayMs = 0L)
        return stillCurrent
    }

    fun failExport(snapshot: MarkdownExportSnapshot) {
        synchronized(this) {
            if (activeExportOperationId != snapshot.operationId) return
            activeExportOperationId = null
            _uiState.update { state ->
                if (state.isExporting) state.copy(isExporting = false) else state
            }
        }
    }

    fun recoverySnapshot(): MarkdownWorkspaceRecoverySnapshot {
        val state = _uiState.value
        return MarkdownWorkspaceRecoverySnapshot(
            text = state.text,
            hadUtf8Bom = state.hadUtf8Bom,
            displayName = state.displayName,
            isDirty = state.isDirty
        )
    }

    fun restoreRecovery(snapshot: MarkdownWorkspaceRecoverySnapshot): Boolean = synchronized(this) {
        if (!_uiState.value.isPristineForRecovery(includeLoading = false)) return@synchronized false
        _uiState.value = recoveredState(snapshot)
        true
    }

    override fun onCleared() {
        synchronized(this) {
            lifecycleOwner?.lifecycle?.removeObserver(recoveryLifecycleObserver)
            lifecycleOwner = null
        }
        super.onCleared()
    }

    private fun scheduleRecovery(delayMs: Long = RECOVERY_DEBOUNCE_MS) {
        val store = recoveryStore ?: return
        if (_uiState.value.isRecoveryLoading) return
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val snapshot = recoverySnapshot()
            val result = runCatching { store.save(snapshot) }
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
        }
    }

    private fun MarkdownWorkspaceUiState.isPristineForRecovery(includeLoading: Boolean = true): Boolean =
        text.isEmpty() &&
            !hadUtf8Bom &&
            displayName == DEFAULT_MARKDOWN_NAME &&
            !isDirty &&
            revision == 0L &&
            !isExporting &&
            !isImporting &&
            (includeLoading || !isRecoveryLoading)

    private fun recoveredState(snapshot: MarkdownWorkspaceRecoverySnapshot): MarkdownWorkspaceUiState =
        MarkdownWorkspaceUiState(
            text = snapshot.text,
            hadUtf8Bom = snapshot.hadUtf8Bom,
            displayName = MarkdownDocumentFileAccess.normalizeDisplayName(
                snapshot.displayName,
                DEFAULT_MARKDOWN_NAME
            ),
            isDirty = snapshot.isDirty,
            revision = 1L,
            isRecoveryLoading = false
        )

    private fun documentFrom(state: MarkdownWorkspaceUiState): TextDocument = TextDocument(
        text = state.text,
        hadUtf8Bom = state.hadUtf8Bom,
        lineEndings = TextDocumentCodec.detectLineEndings(state.text)
    )
}
