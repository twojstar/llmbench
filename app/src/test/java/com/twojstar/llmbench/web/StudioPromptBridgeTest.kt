package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context

class StudioPromptBridgeTest {
    private companion object {
        const val CHATGPT_URL = "https://chatgpt.com/"
        const val SAMPLE_PROFILE = "Profile"
        const val STUDIO_PROFILE = "Studio profile"
        const val INSERTED_RESULT = "inserted"
        const val NO_EDITOR_RESULT = "no-editor"
        const val OFF_PROVIDER_RESULT = "off-provider"
        const val INPUT_EVENTS = "beforeinput,input,change"
    }

    @Test
    fun scriptsAreRestrictedToProviderOwnedHttpsPages() {
        assertTrue(
            studioPromptApplyScript(
                WebAiService.CHATGPT,
                CHATGPT_URL,
                SAMPLE_PROFILE
            ) != null
        )
        assertNull(
            studioPromptApplyScript(
                WebAiService.CHATGPT,
                "https://chatgpt.com.evil.example/",
                SAMPLE_PROFILE
            )
        )
        assertNull(
            studioPromptTargetTrackerScript(WebAiService.CHATGPT, "http://chatgpt.com/")
        )
    }
    @Test
    fun insertsIntoEmptyTextareaAndDispatchesExpectedEvents() {
        val prompt = "Be precise.\nKeep quotes like \"this\" intact."
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "", active = true),
            prompt = prompt
        )

        assertEquals(INSERTED_RESULT, outcome.result)
        assertEquals(prompt, outcome.value)
        assertEquals(INPUT_EVENTS, outcome.events)
    }


    @Test
    fun runtimeGuardRejectsNavigationRaceToAnotherHost() {
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "", active = true),
            prompt = STUDIO_PROFILE,
            runtimeHost = "accounts.example.com"
        )

        assertEquals(OFF_PROVIDER_RESULT, outcome.result)
        assertEquals("", outcome.value)
        assertEquals("", outcome.events)
    }

    @Test
    fun runtimeGuardRejectsSameProviderNavigationToDifferentDocument() {
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "", active = true),
            prompt = STUDIO_PROFILE,
            runtimeHref = "https://chatgpt.com/settings"
        )

        assertEquals(OFF_PROVIDER_RESULT, outcome.result)
        assertEquals("", outcome.value)
        assertEquals("", outcome.events)
    }

    @Test
    fun refusesToOverwriteExistingComposerText() {
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "draft message", active = true),
            prompt = STUDIO_PROFILE
        )

        assertEquals("not-empty", outcome.result)
        assertEquals("draft message", outcome.value)
        assertEquals("", outcome.events)
    }

    @Test
    fun rejectsGenericInputsWithoutVerifiedComposerMarkers() {
        listOf("text", "search", "email").forEach { type ->
            val outcome = runApply(
                targetSetup = inputSetup(type = type, value = "", active = true),
                prompt = STUDIO_PROFILE
            )
            assertEquals(NO_EDITOR_RESULT, outcome.result)
            assertEquals("", outcome.value)
        }
    }

    @Test
    fun rejectsHiddenDisabledAndReadOnlyTextareas() {
        val hidden = runApply(
            targetSetup = textAreaSetup(value = "", active = true) +
                "\ntarget.getClientRects = function() { return []; };",
            prompt = STUDIO_PROFILE
        )
        val disabled = runApply(
            targetSetup = textAreaSetup(value = "", active = true) + "\ntarget.disabled = true;",
            prompt = STUDIO_PROFILE
        )
        val readOnly = runApply(
            targetSetup = textAreaSetup(value = "", active = true) + "\ntarget.readOnly = true;",
            prompt = STUDIO_PROFILE
        )

        assertEquals(NO_EDITOR_RESULT, hidden.result)
        assertEquals(NO_EDITOR_RESULT, disabled.result)
        assertEquals(NO_EDITOR_RESULT, readOnly.result)
    }

    @Test
    fun neverTreatsPasswordInputAsAStudioTarget() {
        val outcome = runApply(
            targetSetup = inputSetup(type = "password", value = "", active = true),
            prompt = STUDIO_PROFILE
        )

        assertEquals(NO_EDITOR_RESULT, outcome.result)
        assertEquals("", outcome.value)
    }
    @Test
    fun trackerRemembersLastFocusedEditableElement() {
        val script = requireNotNull(
            studioPromptTargetTrackerScript(WebAiService.CHATGPT, CHATGPT_URL)
        )
        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            val runtime = browserMocks
                .replace("__RUNTIME_HOST__", "chatgpt.com")
                .replace("__RUNTIME_HREF__", CHATGPT_URL)
            context.evaluateString(scope, runtime, "tracker-setup", 1, null)
            context.evaluateString(scope, script, "tracker-install", 1, null)
            context.evaluateString(scope, textAreaSetup("", active = false), "tracker-target", 1, null)
            val remembered = context.evaluateString(
                scope,
                "document.listeners.focusin({target: target}); window.__llmbenchStudioPromptTarget.target === target;",
                "tracker-focus",
                1,
                null
            )
            assertTrue(Context.toBoolean(remembered))
        } finally {
            Context.exit()
        }
    }

    @Test
    fun usesLastTrackedEditorAfterNativeToolbarTakesFocus() {
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "", active = false) + "\n" +
                "window.__llmbenchStudioPromptTarget = { target: target, focusedAt: Date.now() };",
            prompt = STUDIO_PROFILE
        )

        assertEquals(INSERTED_RESULT, outcome.result)
        assertEquals(STUDIO_PROFILE, outcome.value)
    }

    @Test
    fun rememberedEditorAgeRefreshesWhenFocusLeavesComposer() {
        val script = requireNotNull(
            studioPromptTargetTrackerScript(WebAiService.CHATGPT, CHATGPT_URL)
        )
        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            val runtime = browserMocks
                .replace("__RUNTIME_HOST__", "chatgpt.com")
                .replace("__RUNTIME_HREF__", CHATGPT_URL)
            context.evaluateString(scope, runtime + "\n" + textAreaSetup("", active = true), "focus-setup", 1, null)
            context.evaluateString(scope, script, "focus-tracker", 1, null)
            context.evaluateString(
                scope,
                "window.__llmbenchStudioPromptTarget.focusedAt = 1; document.listeners.focusout({target: target});",
                "focus-out",
                1,
                null
            )
            val refreshed = context.evaluateString(
                scope,
                "window.__llmbenchStudioPromptTarget.focusedAt > 1;",
                "focus-time",
                1,
                null
            )
            assertTrue(Context.toBoolean(refreshed))
        } finally {
            Context.exit()
        }
    }

    @Test
    fun contentEditableWithAttachmentFallsBackWithoutDeletingContent() {
        val outcome = runApply(
            targetSetup = contentEditableSetup(active = true, hasAttachment = true),
            prompt = STUDIO_PROFILE
        )

        assertEquals("not-empty", outcome.result)
        assertEquals("", outcome.value)
        assertEquals("", outcome.events)
    }

    @Test
    fun insertsIntoEmptyContentEditable() {
        val outcome = runApply(
            targetSetup = contentEditableSetup(active = true),
            prompt = STUDIO_PROFILE
        )

        assertEquals(INSERTED_RESULT, outcome.result)
        assertEquals(STUDIO_PROFILE, outcome.value)
        assertEquals(INPUT_EVENTS, outcome.events)
    }

    @Test
    fun parsesWebViewCallbackTokensConservatively() {
        assertEquals(StudioPromptApplyResult.INSERTED, parseStudioPromptApplyResult("\"inserted\""))
        assertEquals(StudioPromptApplyResult.NOT_EMPTY, parseStudioPromptApplyResult("\"not-empty\""))
        assertEquals(StudioPromptApplyResult.FAILED, parseStudioPromptApplyResult("null"))
        assertEquals(StudioPromptApplyResult.FAILED, parseStudioPromptApplyResult(null))
    }

    private data class ScriptOutcome(
        val result: String,
        val value: String,
        val events: String
    )

    private fun runApply(
        targetSetup: String,
        prompt: String,
        runtimeHost: String = "chatgpt.com",
        runtimeHref: String = CHATGPT_URL
    ): ScriptOutcome {
        val script = requireNotNull(
            studioPromptApplyScript(
                WebAiService.CHATGPT,
                CHATGPT_URL,
                prompt
            )
        )
        val context = Context.enter()
        return try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            val runtime = browserMocks
                .replace("__RUNTIME_HOST__", runtimeHost)
                .replace("__RUNTIME_HREF__", runtimeHref)
            context.evaluateString(scope, runtime + "\n" + targetSetup, "studio-setup", 1, null)
            val result = Context.toString(
                context.evaluateString(scope, script, "studio-apply", 1, null)
            )
            val value = Context.toString(
                context.evaluateString(scope, "String(target.value || target.textContent || '')", "studio-value", 1, null)
            )
            val events = Context.toString(
                context.evaluateString(scope, "events.join(',')", "studio-events", 1, null)
            )
            ScriptOutcome(result, value, events)
        } finally {
            Context.exit()
        }
    }

    private fun textAreaSetup(value: String, active: Boolean): String = """
        var target = {
            tagName: 'TEXTAREA', value: ${jsQuote(value)}, isConnected: true,
            parentElement: null, isContentEditable: false,
            focus: function() { this.focused = true; },
            dispatchEvent: function(event) { events.push(event.type); return true; }
        };
        document.activeElement = ${if (active) "target" else "document.body"};
    """.trimIndent()

    private fun inputSetup(type: String, value: String, active: Boolean): String = """
        var target = {
            tagName: 'INPUT', type: ${jsQuote(type)}, value: ${jsQuote(value)}, isConnected: true,
            parentElement: null, isContentEditable: false,
            focus: function() {}, dispatchEvent: function(event) { events.push(event.type); return true; }
        };
        document.activeElement = ${if (active) "target" else "document.body"};
    """.trimIndent()

    private fun contentEditableSetup(active: Boolean, hasAttachment: Boolean = false): String = """
        var target = {
            tagName: 'DIV', textContent: '', innerText: '', isConnected: true,
            parentElement: null, isContentEditable: true,
            focus: function() {}, dispatchEvent: function(event) { events.push(event.type); return true; },
            querySelector: function() { return ${if (hasAttachment) "{ tagName: 'IMG' }" else "null"}; }
        };
        document.activeElement = ${if (active) "target" else "document.body"};
    """.trimIndent()

    private fun jsQuote(value: String): String = org.json.JSONObject.quote(value)

    private val browserMocks = """
        var events = [];
        function Event(type, options) { this.type = type; this.options = options || {}; }
        function InputEvent(type, options) {
            this.type = type;
            this.data = options && options.data;
            this.options = options || {};
        }
        var document = {
            documentElement: { tagName: 'HTML' },
            body: { tagName: 'BODY', parentElement: null, isContentEditable: false },
            activeElement: null,
            listeners: {},
            addEventListener: function(type, listener) { this.listeners[type] = listener; }
        };
        var window = {};
        var location = { protocol: 'https:', hostname: '__RUNTIME_HOST__', href: '__RUNTIME_HREF__' };
    """.trimIndent()
}
