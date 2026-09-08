package com.twojstar.llmbench.data.skills

const val MAX_RUNTIME_SKILL_INSTRUCTION_CHARS = 32 * 1024
const val MAX_RUNTIME_SKILL_TOTAL_CHARS = 64 * 1024

fun localSkillRuntimeBudgetError(skills: List<AgentSkillManifest>): String? {
    var total = 0
    skills.sortedBy(AgentSkillManifest::name).forEach { skill ->
        val size = skill.instructions.length
        if (size > MAX_RUNTIME_SKILL_INSTRUCTION_CHARS) {
            return "'${skill.name}' has too many instruction characters to enable safely."
        }
        total += size
        if (total > MAX_RUNTIME_SKILL_TOTAL_CHARS) {
            return "Enabled local skills exceed the safe combined instruction limit."
        }
    }
    return null
}

fun composeLocalSkillSystemInstruction(
    baseInstruction: String?,
    skills: List<AgentSkillManifest>
): String? {
    val base = baseInstruction?.takeIf(String::isNotBlank)
    if (skills.isEmpty()) return base
    check(localSkillRuntimeBudgetError(skills) == null) {
        "Enabled local skills exceed the runtime instruction budget."
    }

    val skillSection = buildString {
        appendLine("## User-enabled local skills")
        appendLine("Only the Markdown instructions from skills explicitly enabled by the user are attached below.")
        appendLine("LlmBench does not execute scripts or declared tools from these skills.")
        skills.sortedBy(AgentSkillManifest::name).forEach { skill ->
            appendLine()
            appendLine("### Skill: ${skill.name}")
            append(skill.instructions)
        }
    }

    return listOfNotNull(base, skillSection).joinToString("\n\n")
}
