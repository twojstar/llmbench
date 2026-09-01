package com.twojstar.llmbench.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twojstar.llmbench.data.engine.AiChatService
import com.twojstar.llmbench.data.engine.InstructionRenderer
import com.twojstar.llmbench.data.engine.ProfileMerger
import com.twojstar.llmbench.data.engine.ValidationResult
import com.twojstar.llmbench.data.engine.YamlParser
import com.twojstar.llmbench.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val sender: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: List<String> = emptyList()
)

data class StudioUiState(
    val baseProfile: Profile = PresetProfiles.DefaultBaseProfile,
    val selectedOverlay: ProfileOverlay? = null,
    val availableOverlays: List<ProfileOverlay> = PresetProfiles.BuiltInOverlays,
    val mergedProfile: Profile = PresetProfiles.DefaultBaseProfile,
    val renderedInstructions: String = "",
    val language: String = "auto", // auto, en, pl
    val validationResult: ValidationResult = ValidationResult(true, emptyList(), emptyList()),
    val yamlRepresentation: String = "",
    val playgroundMessages: List<ChatMessage> = emptyList(),
    val isSimulating: Boolean = false,
    val currentTab: NavigationTab = NavigationTab.WEB_CHATS,
    val snackbarMessage: String? = null,

    // Integrated Multi-Provider AI Chat
    val chatMessages: List<ModelChatMessage> = emptyList(),
    val selectedChatProvider: AiProvider = AiProvider.ALL,
    val selectedChatModel: String = "all",
    val apiKeyConfig: ApiKeyConfig = ApiKeyConfig(),
    val includeSystemProfileInChat: Boolean = true,
    val isChatGenerating: Boolean = false,
    val activeGeneratingProviders: Set<AiProvider> = emptySet(),
    val showApiKeyDialog: Boolean = false
)

enum class NavigationTab {
    WEB_CHATS,
    COMPARE_HUB,
    STUDIO,
    INSTRUCTIONS,
    YAML,
    PLAYGROUND,
    SKILLS
}

class StudioViewModel : ViewModel() {

    private val aiChatService = AiChatService()

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        recompute()
        initPlaygroundWelcome()
        initChatWelcome()
    }

    private fun initPlaygroundWelcome() {
        val welcomeMsg = ChatMessage(
            id = "welcome",
            sender = "assistant",
            text = "Hello! I am ready to assist. Adjust personality knobs, overlays, or collaboration policies in the Studio tab, and ask me any question to test how my responses adapt to your style profile.",
            notes = listOf("Active Profile: Default Friendly (intensity: 1)")
        )
        _uiState.update { it.copy(playgroundMessages = listOf(welcomeMsg)) }
    }

    private fun initChatWelcome() {
        val providerLines = AiProvider.concreteProviders.joinToString("\n") { provider ->
            "• **${provider.displayName}** (`${provider.defaultModel}`)"
        }
        val welcomeChatMessages = listOf(
            ModelChatMessage(
                id = "welcome_assistant_intro",
                sender = "assistant",
                provider = AiProvider.ALL,
                modelName = "Multi-Model Hub",
                text = "Welcome to the **AI Chat Hub**! 🚀\n\nHere you can interact with:\n$providerLines\n\n✨ **Compare Mode**: Select *'All Models'* to send your prompt to every configured native provider concurrently and compare their outputs side-by-side.\n\n⚙️ Tap the **Key icon** in the top bar to connect your live API keys or test anytime in live simulation mode.",
                activeProfileNotes = listOf("Active System Profile linked from Studio")
            )
        )
        _uiState.update { it.copy(chatMessages = welcomeChatMessages) }
    }

    fun selectTab(tab: NavigationTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    // --- Chat Screen Actions ---

    fun setChatProvider(provider: AiProvider) {
        val newModel = if (provider == AiProvider.ALL) "all" else provider.defaultModel
        _uiState.update {
            it.copy(
                selectedChatProvider = provider,
                selectedChatModel = newModel
            )
        }
    }

    fun setChatModel(modelName: String) {
        _uiState.update { it.copy(selectedChatModel = modelName) }
    }

    fun toggleIncludeSystemProfile(include: Boolean) {
        _uiState.update { it.copy(includeSystemProfileInChat = include) }
        showSnackbar(if (include) "System profile prompt attached to chat" else "Standard base model prompt mode")
    }

    fun setShowApiKeyDialog(show: Boolean) {
        _uiState.update { it.copy(showApiKeyDialog = show) }
    }

    fun saveApiKeys(geminiKey: String, openAiKey: String, claudeKey: String, deepseekKey: String = "", kimiKey: String = "") {
        _uiState.update {
            it.copy(
                apiKeyConfig = ApiKeyConfig(
                    geminiKey = geminiKey.trim(),
                    openAiKey = openAiKey.trim(),
                    claudeKey = claudeKey.trim(),
                    deepseekKey = deepseekKey.trim(),
                    kimiKey = kimiKey.trim()
                ),
                showApiKeyDialog = false
            )
        }
        showSnackbar("API Keys updated successfully.")
    }

    fun clearChatHistory() {
        initChatWelcome()
        showSnackbar("Chat history cleared.")
    }

    fun sendChatMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiState.value.isChatGenerating) return

        val userMessage = ModelChatMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = "user",
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )

        val targetProvider = _uiState.value.selectedChatProvider
        val currentMessages = _uiState.value.chatMessages + userMessage

        val providersToRun = if (targetProvider == AiProvider.ALL) {
            AiProvider.concreteProviders
        } else {
            listOf(targetProvider)
        }

        _uiState.update {
            it.copy(
                chatMessages = currentMessages,
                isChatGenerating = true,
                activeGeneratingProviders = providersToRun.toSet()
            )
        }

        viewModelScope.launch {
            val systemPrompt = if (_uiState.value.includeSystemProfileInChat) {
                _uiState.value.renderedInstructions.ifBlank { null }
            } else {
                null
            }
            val activeProfile = if (_uiState.value.includeSystemProfileInChat) {
                _uiState.value.mergedProfile
            } else {
                null
            }
            val apiKeys = _uiState.value.apiKeyConfig

            if (targetProvider == AiProvider.ALL) {
                // Run concurrent requests for every native provider
                val tasks = providersToRun.map { provider ->
                    async {
                        val model = provider.defaultModel
                        aiChatService.generateResponse(
                            prompt = trimmed,
                            provider = provider,
                            modelName = model,
                            apiKeys = apiKeys,
                            systemInstruction = systemPrompt,
                            profile = activeProfile,
                            conversationHistory = currentMessages
                        )
                    }
                }

                val responses = tasks.awaitAll()
                _uiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages + responses,
                        isChatGenerating = false,
                        activeGeneratingProviders = emptySet()
                    )
                }
            } else {
                val model = _uiState.value.selectedChatModel
                val response = aiChatService.generateResponse(
                    prompt = trimmed,
                    provider = targetProvider,
                    modelName = model,
                    apiKeys = apiKeys,
                    systemInstruction = systemPrompt,
                    profile = activeProfile,
                    conversationHistory = currentMessages
                )
                _uiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages + response,
                        isChatGenerating = false,
                        activeGeneratingProviders = emptySet()
                    )
                }
            }
        }
    }

    // --- Profile & Studio Customization Actions ---

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(language = lang) }
        recompute()
    }

    fun setBasePersonality(base: String) {
        val updated = _uiState.value.baseProfile.copy(
            personality = _uiState.value.baseProfile.personality.copy(base = base)
        )
        _uiState.update { it.copy(baseProfile = updated) }
        recompute()
    }

    fun setPersonalityIntensity(intensity: Int?) {
        val updated = _uiState.value.baseProfile.copy(
            personality = _uiState.value.baseProfile.personality.copy(intensity = intensity)
        )
        _uiState.update { it.copy(baseProfile = updated) }
        recompute()
    }

    fun setModifier(name: String, value: Int?) {
        val currentMods = _uiState.value.baseProfile.personality.modifiers.toMutableMap()
        if (value != null) {
            currentMods[name] = value
        } else {
            currentMods.remove(name)
        }
        val updated = _uiState.value.baseProfile.copy(
            personality = _uiState.value.baseProfile.personality.copy(modifiers = currentMods)
        )
        _uiState.update { it.copy(baseProfile = updated) }
        recompute()
    }

    fun setAdaptation(key: String, value: Boolean) {
        val currentAdapt = _uiState.value.baseProfile.personality.adaptation
        val updatedAdapt = when (key) {
            "followUserRegister" -> currentAdapt.copy(followUserRegister = value)
            "preserveRequestedArtifactStyle" -> currentAdapt.copy(preserveRequestedArtifactStyle = value)
            "reduceHumorInSeriousContexts" -> currentAdapt.copy(reduceHumorInSeriousContexts = value)
            "mirrorLanguage" -> currentAdapt.copy(mirrorLanguage = value)
            "allowCasualProfanity" -> currentAdapt.copy(allowCasualProfanity = value)
            else -> currentAdapt
        }
        val updated = _uiState.value.baseProfile.copy(
            personality = _uiState.value.baseProfile.personality.copy(adaptation = updatedAdapt)
        )
        _uiState.update { it.copy(baseProfile = updated) }
        recompute()
    }

    fun setCollaborationEnum(field: String, value: String) {
        val current = _uiState.value.baseProfile.collaboration
        val updated = when (field) {
            "preamble" -> current.copy(preamble = value)
            "initiative" -> current.copy(initiative = value)
            "verification" -> current.copy(verification = value)
            "questionPolicy" -> current.copy(questionPolicy = value)
            "assumptionPolicy" -> current.copy(assumptionPolicy = value)
            else -> current
        }
        _uiState.update { it.copy(baseProfile = it.baseProfile.copy(collaboration = updated)) }
        recompute()
    }

    fun setCollaborationBool(field: String, value: Boolean) {
        val current = _uiState.value.baseProfile.collaboration
        val updated = when (field) {
            "answerFirst" -> current.copy(answerFirst = value)
            "plainChatIsDefault" -> current.copy(plainChatIsDefault = value)
            "respectExplicitTurnInstructions" -> current.copy(respectExplicitTurnInstructions = value)
            "avoidRoutinePraise" -> current.copy(avoidRoutinePraise = value)
            "avoidRoutineFollowUpOffer" -> current.copy(avoidRoutineFollowUpOffer = value)
            "announceOnlyMaterialActions" -> current.copy(announceOnlyMaterialActions = value)
            "reportPartialFailures" -> current.copy(reportPartialFailures = value)
            "preferResultOverProcess" -> current.copy(preferResultOverProcess = value)
            else -> current
        }
        _uiState.update { it.copy(baseProfile = it.baseProfile.copy(collaboration = updated)) }
        recompute()
    }

    fun setOutputSetting(
        defaultFormat: String? = null,
        maxHeadingDepth: Int? = null,
        preferShortParagraphs: Boolean? = null,
        tables: String? = null,
        codeExamples: String? = null,
        citations: String? = null
    ) {
        val current = _uiState.value.baseProfile.output
        val updated = current.copy(
            defaultFormat = defaultFormat ?: current.defaultFormat,
            maxHeadingDepth = maxHeadingDepth ?: current.maxHeadingDepth,
            preferShortParagraphs = preferShortParagraphs ?: current.preferShortParagraphs,
            tables = tables ?: current.tables,
            codeExamples = codeExamples ?: current.codeExamples,
            citations = citations ?: current.citations
        )
        _uiState.update { it.copy(baseProfile = it.baseProfile.copy(output = updated)) }
        recompute()
    }

    fun applyOverlay(overlay: ProfileOverlay?) {
        _uiState.update { it.copy(selectedOverlay = overlay) }
        recompute()
    }

    fun resetToDefault() {
        _uiState.update {
            it.copy(
                baseProfile = PresetProfiles.DefaultBaseProfile,
                selectedOverlay = null
            )
        }
        recompute()
        showSnackbar("Reset profile to standard default.")
    }

    fun saveCustomOverlay(name: String, description: String) {
        val base = _uiState.value.baseProfile
        val newOverlay = ProfileOverlay(
            id = name.lowercase().replace(" ", "-"),
            name = name,
            description = description,
            locale = base.locale,
            personalityBase = base.personality.base,
            personalityIntensity = base.personality.intensity,
            modifierOverrides = base.personality.modifiers.filter { (it.value ?: 0) > 0 },
            verification = base.collaboration.verification,
            initiative = base.collaboration.initiative,
            customNote = "Exported from LlmBench."
        )
        val updatedList = _uiState.value.availableOverlays + newOverlay
        _uiState.update {
            it.copy(
                availableOverlays = updatedList,
                selectedOverlay = newOverlay
            )
        }
        recompute()
        showSnackbar("Saved overlay: $name")
    }

    fun clearPlaygroundChat() {
        initPlaygroundWelcome()
    }

    fun sendTestPrompt(userPrompt: String) {
        if (userPrompt.isBlank()) return

        val userMsg = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = "user",
            text = userPrompt
        )

        val currentList = _uiState.value.playgroundMessages + userMsg
        _uiState.update { it.copy(playgroundMessages = currentList, isSimulating = true) }

        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            val profile = _uiState.value.mergedProfile
            val simulatedText = generateSimulatedResponse(userPrompt, profile)
            val notes = mutableListOf<String>()
            notes.add("Base Voice: ${profile.personality.base} (lvl ${profile.personality.intensity ?: 1})")
            profile.personality.modifiers.filter { (it.value ?: 0) > 0 }.forEach { (k, v) ->
                notes.add("$k: $v")
            }
            notes.add("Initiative: ${profile.collaboration.initiative}, Verification: ${profile.collaboration.verification}")

            val botMsg = ChatMessage(
                id = "bot_${System.currentTimeMillis()}",
                sender = "assistant",
                text = simulatedText,
                notes = notes
            )
            _uiState.update {
                it.copy(
                    playgroundMessages = it.playgroundMessages + botMsg,
                    isSimulating = false
                )
            }
        }
    }

    private fun generateSimulatedResponse(prompt: String, profile: Profile): String {
        val base = profile.personality.base
        val intensity = profile.personality.intensity ?: 1
        val conciseLevel = profile.personality.modifiers["concise"] ?: 0
        val technicalLevel = profile.personality.modifiers["technical"] ?: 0
        val cynicalLevel = profile.personality.modifiers["cynical"] ?: 0
        val educationalLevel = profile.personality.modifiers["educational"] ?: 0
        val honestLevel = profile.personality.modifiers["honest"] ?: 0

        val lowerPrompt = prompt.lowercase()

        return when {
            lowerPrompt.contains("git submodule") || lowerPrompt.contains("submodule") -> {
                when (base) {
                    "concise" -> "Git submodules embed a separate repo as a subdirectory pinned to a specific commit SHA.\n\n```bash\ngit submodule add <url> <path>\ngit submodule update --init --recursive\n```\nAvoid merge commits across submodule boundaries."
                    "cynical" -> "Git submodules are Git's clunkiest primitive—they pin external repos to exact commit SHAs. Most teams shoot themselves in the foot by forgetting recursive checkouts.\n\nIf you must use them for portable cores like `.ai`:\n```bash\ngit submodule update --init --recursive\n```"
                    "professional" -> "A Git submodule integrates an external repository into a designated path while retaining independent version history.\n\n### Specifications:\n- Stores pointer in `.gitmodules`.\n- Pins dependency to an explicit commit hash, avoiding unvetted upstream breakage.\n\n### Primary Commands:\n```bash\ngit submodule add https://github.com/trvny/.ai.git .ai/core\ngit submodule update --init --recursive\n```"
                    else -> "Git submodules allow you to keep an external Git repository inside a subfolder of your main project, pinned to a specific commit.\n\nIn `.ai`, this lets you pin the public reusable core at `.ai/core` while keeping downstream overlays separate:\n\n```bash\ngit submodule add https://github.com/trvny/.ai.git .ai/core\ncp .ai/core/examples/profile.overlay.yaml .ai/profile.yaml\n```\nLet me know if you need help with CI workflows or merge hooks!"
                }
            }
            lowerPrompt.contains("code review") || lowerPrompt.contains("review") -> {
                when {
                    cynicalLevel >= 2 || base == "cynical" -> "### Code Review: Reality Check\n1. **Over-engineering**: This PR adds 4 new abstract factories where a simple pure function would suffice.\n2. **Security**: Token is passed via plain query parameters instead of authorization headers.\n3. **Recommendation**: Delete the caching layer until telemetry proves a bottleneck."
                    technicalLevel >= 2 || base == "professional" -> "### Static Review & Verification\n1. **Invariant Check**: Thread safety is compromised in the mutable map singleton. Use `ConcurrentHashMap` or state flow synchronization.\n2. **Complexity**: O(N^2) search in loop block at line 42 can be reduced to O(N) via set hashing.\n3. **Patch**:\n```kotlin\nval lookupSet = items.map { it.id }.toSet()\nreturn list.filter { it.ref in lookupSet }\n```"
                    else -> "Here is a constructive review of the proposed changes:\n\n- The architectural separation between core profiles and downstream overlays is clean.\n- Consider adding input validation for boundary edge cases.\n- All unit tests pass cleanly!"
                }
            }
            lowerPrompt.contains("idea") || lowerPrompt.contains("startup") -> {
                when {
                    cynicalLevel >= 2 -> "90% of AI wrapper startups die from lack of distribution. You don't have a moat if an API update renders your product obsolete. Talk to 10 paying customers before writing another line of code."
                    honestLevel >= 2 -> "The core concept has value, but you need to solve unit economics first. Building features before validating willingness to pay is a common pitfall. Focus on customer acquisition costs."
                    else -> "That is an intriguing concept! Validating customer demand early will give you strong signal. Let's look at user pain points and minimum viable features."
                }
            }
            else -> {
                if (conciseLevel >= 2 || base == "concise") {
                    "Understood. For '$prompt', here is the direct solution with zero fluff:\n- Verify inputs against schema\n- Merge overlays recursively (later layers win)\n- Render instructions to target runtime."
                } else if (educationalLevel >= 2) {
                    "Let's break down '$prompt' step-by-step to build clear intuition first.\n\n### The Concept\nAt its core, the principle is modular composition: later layers win, while base defaults provide safety.\n\n### Practical Example\nWhen you compose a profile, scalars replace older values while mappings merge recursively."
                } else {
                    "Regarding '$prompt': The `.ai` core architecture maintains reusable AI profiles, overlays, and instructions in one public place while keeping project-specific private differences downstream."
                }
            }
        }
    }

    private fun recompute() {
        val base = _uiState.value.baseProfile
        val overlay = _uiState.value.selectedOverlay

        val merged = if (overlay != null) {
            ProfileMerger.merge(base, overlay)
        } else {
            base
        }

        val rendered = InstructionRenderer.render(merged, _uiState.value.language)
        val validation = ProfileMerger.validate(merged)
        val yaml = YamlParser.dumpProfile(merged)

        _uiState.update {
            it.copy(
                mergedProfile = merged,
                renderedInstructions = rendered,
                validationResult = validation,
                yamlRepresentation = yaml
            )
        }
    }

    fun showSnackbar(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
