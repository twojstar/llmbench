package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context

class StudioPromptBridgeTest {
    @Test
    fun scriptsAreRestrictedToProviderOwnedHttpsPages() {
        assertTrue(
            studioPromptApplyScript(
                WebAiService.CHATGPT,
                "https://chatgpt.com/",
                "Profile"
            ) != null
        )
        assertNull(
            studioPromptApplyScript(
                WebAiService.CHATGPT,
                "https://chatgpt.com.evil.example/",
                "Profile"
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

        assertEquals("inserted", outcome.result)
        assertEquals(prompt, outcome.value)
        assertEquals("beforeinput,input,change", outcome.events)
    }


    @Test
    fun runtimeGuardRejectsNavigationRaceToAnotherHost() {
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "", active = true),
            prompt = "Studio profile",
            runtimeHost = "accounts.example.com"
        )

        assertEquals("off-provider", outcome.result)
        assertEquals("", outcome.value)
        assertEquals("", outcome.events)
    }

    @Test
    fun refusesToOverwriteExistingComposerText() {
        val outcome = runApply(
            targetSetup = textAreaSetup(value = "draft message", active = true),
            prompt = "Studio profile"
        )

        assertEquals("not-empty", outcome.result)
        assertEquals("draft message", outcome.value)
        assertEquals("", outcome.events)
    }

    @Test
    fun neverTreatsPasswordInputAsAStudioTarget() {
        val outcome = runApply(
            targetSetup = inputSetup(type = "password", value = "", active = true),
            prompt = "Studio profile"
        )

        assertEquals("no-editor", outcome.result)
        assertEquals("", outcome.value)
    }
    @Test
    fun trackerRemembersLastFocusedEditableElement() {
        val script = requireNotNull(
            studioPromptTargetTrackerScript(WebAiService.CHATGPT, "https://chatgpt.com/")
        )
        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            val runtime = browserMocks.replace("__RUNTIME_HOST__", "chatgpt.com")
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
                "window.__llmbenchStudioPromptTarget = { target: target };",
            prompt = "Studio profile"
        )

        assertEquals("inserted", outcome.result)
        assertEquals("Studio profile", outcome.value)
    }

    @Test
    fun insertsIntoEmptyContentEditable() {
        val outcome = runApply(
            targetSetup = contentEditableSetup(active = true),
            prompt = "Studio profile"
        )

        assertEquals("inserted", outcome.result)
        assertEquals("Studio profile", outcome.value)
        assertEquals("beforeinput,input,change", outcome.events)
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
        runtimeHost: String = "chatgpt.com"
    ): ScriptOutcome {
        val script = requireNotNull(
            studioPromptApplyScript(
                WebAiService.CHATGPT,
                "https://chatgpt.com/",
                prompt
            )
        )
        val context = Context.enter()
        return try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            context.evaluateString(scope, browserMocks + "\n" + targetSetup, "studio-setup", 1, null)
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

    private fun contentEditableSetup(active: Boolean): String = """
        var target = {
            tagName: 'DIV', textContent: '', innerText: '', isConnected: true,
            parentElement: null, isContentEditable: true,
            focus: function() {}, dispatchEvent: function(event) { events.push(event.type); return true; }
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
        var location = { protocol: 'https:', hostname: '__RUNTIME_HOST__' };
    """.trimIndent()
}
