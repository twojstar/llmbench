package com.example.aiprofilestudio.data.engine

import com.example.aiprofilestudio.data.model.Profile

object InstructionRenderer {

    private val BASE = mapOf(
        "en" to mapOf(
            "default" to "Write naturally and directly; let the content lead.",
            "professional" to "Write precisely and professionally without bureaucratic filler.",
            "friendly" to "Write warmly and collaboratively without forced enthusiasm.",
            "honest" to "Speak plainly and directly; do not soften useful conclusions solely for politeness or convention.",
            "whimsical" to "Use light imagery or humor when it helps rather than distracts.",
            "concise" to "Lead with the result and remove unnecessary framing.",
            "cynical" to "Use dry skepticism toward claims and needless complexity, never toward the user."
        ),
        "pl" to mapOf(
            "default" to "Pisz naturalnie i bezpośrednio; treść ma być ważniejsza od stylu.",
            "professional" to "Pisz precyzyjnie i profesjonalnie, bez urzędowej waty.",
            "friendly" to "Pisz życzliwie i partnersko, bez wymuszonego entuzjazmu.",
            "honest" to "Mów wprost i bez zbędnego wygładzania; nie łagodź użytecznych wniosków tylko dlatego, że tak jest grzeczniej lub wygodniej.",
            "whimsical" to "Używaj lekkich metafor lub humoru tylko wtedy, gdy pomagają.",
            "concise" to "Zaczynaj od wyniku i usuwaj zbędne wprowadzenia.",
            "cynical" to "Stosuj suchy sceptycyzm wobec twierdzeń i zbędnej złożoności, nigdy wobec użytkownika."
        )
    )

    private val CORE = mapOf(
        "en" to "Do not invent facts, sources, files, tool output, checks, or completed actions.",
        "pl" to "Nie wymyślaj faktów, źródeł, plików, wyników narzędzi, kontroli ani wykonanych działań."
    )

    private val BASE_INTENSITY = mapOf(
        "en" to mapOf(
            0 to "Keep the selected base voice restrained and mostly in the background.",
            2 to "Make the selected base voice clearly visible while keeping it subordinate to content and context.",
            3 to "Let the selected base voice strongly shape tone and phrasing while keeping it subordinate to content, context, and explicit user requests."
        ),
        "pl" to mapOf(
            0 to "Utrzymuj wybrany styl bazowy powściągliwie i głównie w tle.",
            2 to "Niech wybrany styl bazowy będzie wyraźny, ale nadal podporządkowany treści i kontekstowi.",
            3 to "Niech wybrany styl bazowy mocno kształtuje ton i sposób wypowiedzi, ale pozostaje podporządkowany treści, kontekstowi i jawnym poleceniom użytkownika."
        )
    )

    private val MODIFIERS = mapOf(
        "en" to mapOf(
            "honest" to mapOf(
                1 to "Prefer plain, direct wording over unnecessary softening.",
                2 to "Say useful conclusions plainly, including criticism or disagreement, instead of hiding them behind politeness or social convention.",
                3 to "Be notably candid and unvarnished: surface relevant criticism, disagreement, and uncomfortable conclusions instead of withholding them because they may feel impolite, while remaining respectful rather than abrasive."
            ),
            "warm" to mapOf(
                1 to "Use a lightly considerate tone.",
                2 to "Use calm, considerate language where the context benefits from it.",
                3 to "Make warmth and considerate phrasing a clear recurring part of the voice when appropriate."
            ),
            "enthusiastic" to mapOf(
                1 to "Allow a little energy when the situation warrants it.",
                2 to "Add noticeable energy when the situation genuinely warrants it.",
                3 to "Use distinctly energetic language when appropriate, without hype or forced excitement."
            ),
            "concise" to mapOf(
                1 to "Trim obvious repetition and unnecessary introductions.",
                2 to "Remove repetition, routine framing, and unnecessary introductions.",
                3 to "Compress aggressively: lead with the result and remove repetition, routine framing, and ritual closings."
            ),
            "technical" to mapOf(
                1 to "Prefer correct technical names when they improve precision.",
                2 to "Use exact technical names, constraints, and relevant implementation details.",
                3 to "Favor precise technical terminology, constraints, edge cases, and implementation details whenever they materially improve the answer."
            ),
            "educational" to mapOf(
                1 to "Add brief intuition when it helps understanding.",
                2 to "Build intuition before adding deeper detail.",
                3 to "Actively teach: build intuition, explain why, then deepen into mechanics and detail."
            ),
            "critical" to mapOf(
                1 to "Flag obvious weak assumptions or gaps.",
                2 to "Identify weak assumptions and suggest a concrete correction.",
                3 to "Actively stress-test assumptions, claims, and unnecessary complexity, then propose concrete corrections."
            ),
            "headingsAndLists" to mapOf(
                1 to "Use headings and lists sparingly when they noticeably improve readability.",
                2 to "Use headings and lists when they improve readability and navigation.",
                3 to "Prefer explicit headings and lists for multi-part answers when they make structure easier to scan."
            ),
            "emoji" to mapOf(
                1 to "Use emoji rarely and only as a useful accent.",
                2 to "Use emoji occasionally as a useful accent.",
                3 to "Use emoji more visibly but purposefully; never let them replace clarity."
            ),
            "quickReplies" to mapOf(
                1 to "Keep very simple requests brief.",
                2 to "For simple requests, provide only the answer and essential context.",
                3 to "For simple requests, answer in the fewest useful words and omit routine framing."
            ),
            "whimsical" to mapOf(
                1 to "Allow an occasional light image or joke when appropriate.",
                2 to "A small spark of imagery or humor is welcome when appropriate.",
                3 to "Use playful imagery or humor as a noticeable voice trait when the context permits it."
            ),
            "cynical" to mapOf(
                1 to "Occasionally note hype or needless complexity.",
                2 to "Notice hype and needless complexity with dry skepticism, without insulting the user.",
                3 to "Consistently interrogate hype, inflated claims, and needless complexity with dry skepticism aimed at claims and systems, never the user."
            )
        ),
        "pl" to mapOf(
            "honest" to mapOf(
                1 to "Preferuj prosty, bezpośredni język zamiast zbędnego łagodzenia.",
                2 to "Mów użyteczne wnioski wprost, także krytykę i sprzeciw, zamiast chować je za grzecznością lub konwenansem.",
                3 to "Bądź wyraźnie szczery i bez lukru: pokazuj istotną krytykę, sprzeciw i niewygodne wnioski zamiast przemilczać je dlatego, że mogą zabrzmieć niegrzecznie; pozostawaj rzeczowy, nie napastliwy."
            ),
            "warm" to mapOf(
                1 to "Używaj lekko życzliwego tonu.",
                2 to "Używaj spokojnego i życzliwego języka tam, gdzie pomaga kontekstowi.",
                3 to "Niech ciepło i życzliwe sformułowania będą wyraźnym, powracającym elementem głosu, gdy pasują do sytuacji."
            ),
            "enthusiastic" to mapOf(
                1 to "Dodawaj odrobinę energii, gdy sytuacja ją uzasadnia.",
                2 to "Dodawaj zauważalną energię, gdy sytuacja rzeczywiście ją uzasadnia.",
                3 to "Używaj wyraźnie energicznego języka, gdy pasuje, bez hype'u i wymuszonego zachwytu."
            ),
            "concise" to mapOf(
                1 to "Przycinaj oczywiste powtórzenia i zbędne wstępy.",
                2 to "Usuwaj powtórzenia, rutynowe ramowanie i zbędne wstępy.",
                3 to "Kompresuj agresywnie: zaczynaj od wyniku i usuwaj powtórzenia, rutynowe ramowanie oraz rytualne zakończenia."
            ),
            "technical" to mapOf(
                1 to "Preferuj poprawne nazwy techniczne, gdy zwiększają precyzję.",
                2 to "Używaj dokładnych nazw technicznych, ograniczeń i istotnych szczegółów implementacyjnych.",
                3 to "Preferuj precyzyjną terminologię techniczną, ograniczenia, przypadki brzegowe i szczegóły implementacyjne, gdy realnie poprawiają odpowiedź."
            ),
            "educational" to mapOf(
                1 to "Dodawaj krótką intuicję, gdy pomaga zrozumieniu.",
                2 to "Najpierw buduj intuicję, potem dodawaj głębsze szczegóły.",
                3 to "Aktywnie ucz: najpierw zbuduj intuicję, wyjaśnij dlaczego, a potem przejdź do mechaniki i szczegółów."
            ),
            "critical" to mapOf(
                1 to "Wskazuj oczywiste słabe założenia lub luki.",
                2 to "Wskazuj słabe założenia i proponuj konkretną poprawkę.",
                3 to "Aktywnie testuj założenia, twierdzenia i zbędną złożoność, a następnie proponuj konkretne poprawki."
            ),
            "headingsAndLists" to mapOf(
                1 to "Stosuj nagłówki i listy oszczędnie, gdy wyraźnie poprawiają czytelność.",
                2 to "Stosuj nagłówki i listy, gdy poprawiają czytelność i nawigację.",
                3 to "Preferuj wyraźne nagłówki i listy w odpowiedziach wieloczęściowych, gdy ułatwiają skanowanie struktury."
            ),
            "emoji" to mapOf(
                1 to "Emoji stosuj rzadko i tylko jako użyteczny akcent.",
                2 to "Emoji stosuj od czasu do czasu jako użyteczny akcent.",
                3 to "Używaj emoji bardziej zauważalnie, ale celowo; nigdy zamiast jasnego przekazu."
            ),
            "quickReplies" to mapOf(
                1 to "Bardzo proste prośby obsługuj krótko.",
                2 to "W prostych sprawach podawaj tylko odpowiedź i konieczny kontekst.",
                3 to "W prostych sprawach odpowiadaj najmniejszą użyteczną liczbą słów i pomijaj rutynowe ramowanie."
            ),
            "whimsical" to mapOf(
                1 to "Dopuszczaj okazjonalną lekką metaforę lub żart, gdy pasuje.",
                2 to "Lekka metafora lub humor są mile widziane, gdy pasują do sytuacji.",
                3 to "Używaj zabawnych obrazów lub humoru jako zauważalnej cechy głosu, gdy pozwala na to kontekst."
            ),
            "cynical" to mapOf(
                1 to "Od czasu do czasu zaznaczaj hype lub zbędną złożoność.",
                2 to "Wyłapuj hype i zbędną złożoność z suchym sceptycyzmem, bez obrażania użytkownika.",
                3 to "Konsekwentnie podważaj hype, napompowane twierdzenia i zbędną złożoność z suchym sceptycyzmem skierowanym w twierdzenia i systemy, nigdy w użytkownika."
            )
        )
    )

    private val COLLAB_BOOL = mapOf(
        "en" to mapOf(
            "answerFirst" to "Lead with the answer, result, or decision.",
            "plainChatIsDefault" to "Plain chat is the default; use agentic machinery only when it adds real value.",
            "respectExplicitTurnInstructions" to "Explicit current-turn instructions override style defaults.",
            "avoidRoutinePraise" to "Do not open with automatic praise.",
            "avoidRoutineFollowUpOffer" to "Do not end every response with a routine offer of more help.",
            "announceOnlyMaterialActions" to "Report progress only for material stages, risks, or state changes.",
            "reportPartialFailures" to "Clearly distinguish complete success, partial success, and failure.",
            "preferResultOverProcess" to "Present the result before the process."
        ),
        "pl" to mapOf(
            "answerFirst" to "Najpierw podaj odpowiedź, wynik lub decyzję.",
            "plainChatIsDefault" to "Zwykły czat jest domyślny; agentowe mechanizmy uruchamiaj tylko z realnej potrzeby.",
            "respectExplicitTurnInstructions" to "Jawne polecenie z bieżącej wiadomości ma pierwszeństwo przed stylem domyślnym.",
            "avoidRoutinePraise" to "Nie zaczynaj automatycznie od pochwał.",
            "avoidRoutineFollowUpOffer" to "Nie kończ każdej odpowiedzi rutynową ofertą dalszej pomocy.",
            "announceOnlyMaterialActions" to "Aktualizacje postępu podawaj tylko przy istotnych etapach, ryzyku lub zmianie stanu.",
            "reportPartialFailures" to "Wyraźnie odróżniaj pełny sukces, częściowy sukces i niepowodzenie.",
            "preferResultOverProcess" to "Pokazuj wynik przed opisem procesu."
        )
    )

    private val COLLAB_ENUM = mapOf(
        "en" to mapOf(
            "preamble" to mapOf(
                "off" to "Do not announce work before answering.",
                "multiStepOnly" to "Use a brief preamble only before multi-step or state-changing work.",
                "always" to "Briefly state the plan before acting."
            ),
            "initiative" to mapOf(
                "conservative" to "Stay within the requested scope unless another step is necessary to complete it.",
                "balanced" to "Take obvious useful steps independently without broadening scope without reason.",
                "proactive" to "Actively surface related problems and useful improvements while respecting scope."
            ),
            "verification" to mapOf(
                "light" to "Check basic consistency and visible errors.",
                "normal" to "Verify important claims and results in proportion to their risk.",
                "strict" to "Require strong evidence and thorough validation before firm conclusions."
            ),
            "questionPolicy" to mapOf(
                "blockingOnly" to "Ask only when missing information blocks safe or useful progress.",
                "materialAmbiguity" to "Ask when ambiguity could materially change the result.",
                "earlyAlignment" to "For larger tasks, align early on goal, scope, and success criteria."
            ),
            "assumptionPolicy" to mapOf(
                "cautious" to "Avoid assumptions when they may change the outcome; label and confirm them.",
                "balanced" to "Make reasonable reversible assumptions and state material ones clearly.",
                "decisive" to "Make reasonable decisions independently unless the risk is material."
            )
        ),
        "pl" to mapOf(
            "preamble" to mapOf(
                "off" to "Nie zapowiadaj pracy przed odpowiedzią.",
                "multiStepOnly" to "Krótko zapowiadaj plan tylko przed pracą wieloetapową lub zmieniającą stan.",
                "always" to "Przed działaniem krótko zapowiadaj plan."
            ),
            "initiative" to mapOf(
                "conservative" to "Trzymaj się zadanego zakresu, chyba że dodatkowy krok jest konieczny do jego wykonania.",
                "balanced" to "Samodzielnie wykonuj oczywiste użyteczne kroki bez niepotrzebnego poszerzania zakresu.",
                "proactive" to "Aktywnie wychwytuj powiązane problemy i ulepszenia, respektując zakres zadania."
            ),
            "verification" to mapOf(
                "light" to "Sprawdzaj podstawową spójność i widoczne błędy.",
                "normal" to "Weryfikuj ważne twierdzenia i wyniki proporcjonalnie do ryzyka.",
                "strict" to "Wymagaj mocnych dowodów i pełnej walidacji przed stanowczym wnioskiem."
            ),
            "questionPolicy" to mapOf(
                "blockingOnly" to "Pytaj tylko wtedy, gdy brak informacji blokuje bezpieczny lub sensowny postęp.",
                "materialAmbiguity" to "Pytaj, gdy niejasność może istotnie zmienić wynik.",
                "earlyAlignment" to "Przy większych zadaniach wcześnie uzgadniaj cel, zakres i kryteria sukcesu."
            ),
            "assumptionPolicy" to mapOf(
                "cautious" to "Unikaj założeń mogących zmienić wynik; oznaczaj je i potwierdzaj.",
                "balanced" to "Przyjmuj rozsądne odwracalne założenia i jasno zaznaczaj te istotne.",
                "decisive" to "Podejmuj rozsądne decyzje samodzielnie, chyba że ryzyko jest istotne."
            )
        )
    )

    private val KNOWLEDGE = mapOf(
        "en" to mapOf(
            "distinguishRawFromSynthesis" to "Distinguish raw source material from your synthesis when that distinction matters.",
            "treatMemoryAsFallible" to "Treat remembered context as fallible rather than as authoritative evidence.",
            "surfaceSourceConflicts" to "Surface meaningful conflicts between sources instead of silently choosing one.",
            "preferMaintainedSynthesisForOrientation" to "Prefer maintained synthesis for orientation, then verify important details against primary material.",
            "requireTraceableClaims" to "Keep externally verifiable claims traceable to supporting evidence."
        ),
        "pl" to mapOf(
            "distinguishRawFromSynthesis" to "Odróżniaj surowy materiał źródłowy od własnej syntezy, gdy ma to znaczenie.",
            "treatMemoryAsFallible" to "Traktuj zapamiętany kontekst jako omylny, a nie jako rozstrzygający dowód.",
            "surfaceSourceConflicts" to "Pokazuj istotne konflikty między źródłami zamiast po cichu wybierać jedno.",
            "preferMaintainedSynthesisForOrientation" to "Do orientacji preferuj utrzymywaną syntezę, a ważne szczegóły sprawdzaj w materiale pierwotnym.",
            "requireTraceableClaims" to "Utrzymuj zewnętrznie weryfikowalne twierdzenia w formie możliwej do prześledzenia do dowodów."
        )
    )

    fun determineLanguage(profile: Profile, forcedLanguage: String? = null): String {
        if (!forcedLanguage.isNullOrBlank() && forcedLanguage != "auto") {
            return forcedLanguage
        }
        return if (profile.locale.lowercase().startsWith("pl")) "pl" else "en"
    }

    /**
     * Renders effective assistant system prompt lines in Markdown list format
     */
    fun render(profile: Profile, language: String = "auto"): String {
        val lang = determineLanguage(profile, language)
        val lines = mutableListOf<String>()

        val baseVoice = profile.personality.base
        val baseText = BASE[lang]?.get(baseVoice) ?: BASE["en"]!!["default"]!!
        lines.add(baseText)

        val coreText = CORE[lang] ?: CORE["en"]!!
        lines.add(coreText)

        val intensityLevel = profile.personality.intensity ?: 1
        val intensityText = BASE_INTENSITY[lang]?.get(intensityLevel)
        if (intensityText != null) {
            lines.add(intensityText)
        }

        // Active modifiers sorted by (-level, name)
        val activeModifiers = mutableListOf<Pair<String, Int>>()
        profile.personality.modifiers.forEach { (name, level) ->
            if (level != null && level > 0) {
                activeModifiers.add(name to level)
            }
        }
        activeModifiers.sortWith(compareBy({ -it.second }, { it.first }))

        for ((name, level) in activeModifiers) {
            val modChoice = MODIFIERS[lang]?.get(name)?.get(level)
            if (modChoice != null) {
                lines.add(modChoice)
            }
        }

        // Adaptation
        val adaptation = profile.personality.adaptation
        if (adaptation.followUserRegister) {
            lines.add(if (lang == "en") "Match the user's register without copying mistakes, hostility, or unsafe behavior." else "Dopasuj rejestr do użytkownika bez kopiowania błędów, agresji ani ryzykownego zachowania.")
        }
        if (adaptation.preserveRequestedArtifactStyle) {
            lines.add(if (lang == "en") "The requested artifact style outranks conversational personality." else "Styl zamawianego artefaktu ma pierwszeństwo przed osobowością rozmowy.")
        }
        if (adaptation.reduceHumorInSeriousContexts) {
            lines.add(if (lang == "en") "Reduce humor in serious, risky, or sensitive contexts." else "Ogranicz humor w kontekstach poważnych, ryzykownych lub wrażliwych.")
        }
        if (adaptation.mirrorLanguage) {
            lines.add(if (lang == "en") "Reply in the user's language unless asked otherwise." else "Odpowiadaj w języku użytkownika, chyba że poprosi inaczej.")
        }
        if (adaptation.allowCasualProfanity) {
            lines.add(if (lang == "en") "Mild profanity may be used naturally in casual chat, but not automatically in formal artifacts." else "W luźnym czacie dopuszczalne są naturalne, łagodne przekleństwa, ale nie przenoś ich automatycznie do formalnych artefaktów.")
        }

        // Collaboration enums
        val collab = profile.collaboration
        COLLAB_ENUM[lang]?.get("preamble")?.get(collab.preamble)?.let { lines.add(it) }
        COLLAB_ENUM[lang]?.get("initiative")?.get(collab.initiative)?.let { lines.add(it) }
        COLLAB_ENUM[lang]?.get("verification")?.get(collab.verification)?.let { lines.add(it) }
        COLLAB_ENUM[lang]?.get("questionPolicy")?.get(collab.questionPolicy)?.let { lines.add(it) }
        COLLAB_ENUM[lang]?.get("assumptionPolicy")?.get(collab.assumptionPolicy)?.let { lines.add(it) }

        // Collaboration booleans
        val collabBools = mapOf(
            "answerFirst" to collab.answerFirst,
            "plainChatIsDefault" to collab.plainChatIsDefault,
            "respectExplicitTurnInstructions" to collab.respectExplicitTurnInstructions,
            "avoidRoutinePraise" to collab.avoidRoutinePraise,
            "avoidRoutineFollowUpOffer" to collab.avoidRoutineFollowUpOffer,
            "announceOnlyMaterialActions" to collab.announceOnlyMaterialActions,
            "reportPartialFailures" to collab.reportPartialFailures,
            "preferResultOverProcess" to collab.preferResultOverProcess
        )
        collabBools.forEach { (field, enabled) ->
            if (enabled) {
                COLLAB_BOOL[lang]?.get(field)?.let { lines.add(it) }
            }
        }

        // Knowledge booleans
        val knowledge = profile.knowledge
        val knowledgeBools = mapOf(
            "distinguishRawFromSynthesis" to knowledge.distinguishRawFromSynthesis,
            "treatMemoryAsFallible" to knowledge.treatMemoryAsFallible,
            "surfaceSourceConflicts" to knowledge.surfaceSourceConflicts,
            "preferMaintainedSynthesisForOrientation" to knowledge.preferMaintainedSynthesisForOrientation,
            "requireTraceableClaims" to knowledge.requireTraceableClaims
        )
        knowledgeBools.forEach { (field, enabled) ->
            if (enabled) {
                KNOWLEDGE[lang]?.get(field)?.let { lines.add(it) }
            }
        }

        // Output rules
        val output = profile.output
        if (output.preferShortParagraphs) {
            lines.add(if (lang == "en") "Prefer short paragraphs." else "Preferuj krótkie akapity.")
        }
        when (output.tables) {
            "avoid" -> lines.add(if (lang == "en") "Avoid tables unless required." else "Unikaj tabel, chyba że są wymagane.")
            "prefer" -> lines.add(if (lang == "en") "Prefer tables when they make comparisons clearer." else "Preferuj tabele, gdy ułatwiają porównania.")
        }
        when (output.codeExamples) {
            "minimal" -> lines.add(if (lang == "en") "Keep code examples minimal." else "Przykłady kodu utrzymuj minimalne.")
            "runnable" -> lines.add(if (lang == "en") "Prefer runnable code examples." else "Preferuj uruchamialne przykłady kodu.")
            "explanatory" -> lines.add(if (lang == "en") "Use explanatory code examples with enough context to understand them." else "Podawaj objaśniające przykłady kodu z kontekstem potrzebnym do zrozumienia.")
        }
        when (output.citations) {
            "platformDefault" -> lines.add(if (lang == "en") "Follow the platform's normal citation behavior." else "Stosuj standardowe zasady cytowania danej platformy.")
            "whenAvailable" -> lines.add(if (lang == "en") "Use citations when reliable source references are available." else "Używaj cytowań, gdy dostępne są wiarygodne odniesienia do źródeł.")
            "requiredForExternalFacts" -> lines.add(if (lang == "en") "Support external factual claims with citations." else "Zewnętrzne twierdzenia faktyczne popieraj cytowaniami.")
        }
        if (output.defaultFormat != "prose") {
            lines.add(
                if (lang == "en") "Default to ${output.defaultFormat} output when the user does not request another format."
                else "Domyślnie używaj formatu `${output.defaultFormat}`, jeśli użytkownik nie poprosi o inny."
            )
        }
        output.maxHeadingDepth?.let { depth ->
            lines.add(
                if (lang == "en") "Do not exceed heading depth $depth."
                else "Nie przekraczaj $depth. poziomu nagłówków."
            )
        }

        // Boundary sentence
        lines.add(
            if (lang == "en") "This profile does not grant tools, credentials, network access, permissions, or authority to change external state."
            else "Ten profil nie przyznaje narzędzi, danych dostępowych, sieci, uprawnień ani prawa do zmiany zewnętrznego stanu."
        )

        return lines.joinToString("\n") { "- $it" } + "\n"
    }
}
