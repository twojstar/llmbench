package com.twojstar.llmbench.data.repository

data class SkillItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val targetPlatform: String,
    val content: String
)

data class InstructionItem(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val promptText: String
)

data class TemplateItem(
    val id: String,
    val filename: String,
    val type: String,
    val description: String,
    val code: String
)

object SkillsAndDocsRepository {

    val skills = listOf(
        SkillItem(
            id = "english-polish",
            title = "English ↔ Polish AI Specialist",
            category = "Language & Localization",
            description = "Natural contextual translations between Polish and English preserving technical terminology and idiomatic register.",
            targetPlatform = "Universal / OpenAI / Claude",
            content = """
# English <-> Polish Skill

Specialized in natural bilingual translations and localized system prompts.

### Key Rules:
- Preserves technical jargon (e.g., 'submodule', 'dependency injection', 'overhead').
- Respects Polish formal/informal forms (pan/pani vs per ty).
- Correct declension of loanwords and programming identifiers.
- Context-aware adaptation for developer tooling.
            """.trimIndent()
        ),
        SkillItem(
            id = "create-repo-docs",
            title = "Repository Docs Generator",
            category = "Documentation",
            description = "Analyzes source tree and produces structured AGENTS.md, README.md, and architecture diagrams.",
            targetPlatform = "Universal Agent Skill",
            content = """
# Create Repo Docs Skill

Generates standard, maintainable developer documentation for repositories.

### Output Contract:
1. One-line project elevator pitch.
2. Architecture flow chart (Mermaid syntax).
3. Quick start commands without superfluous commentary.
4. Security boundary and secret segregation rules.
            """.trimIndent()
        ),
        SkillItem(
            id = "github-code-security",
            title = "GitHub Code Security Auditor",
            category = "Security & Compliance",
            description = "Scans codebase and CI pipelines for secret leaks, hardcoded credentials, and dependency CVEs.",
            targetPlatform = "GitHub Actions / CodeQL",
            content = """
# GitHub Code Security Skill

### Checks:
- Inspects .codex/config.toml environment variable exclusions.
- Ensures no API keys or tokens are in repo commits.
- Flags unsafe permissions in GitHub workflow files.
- Enforces read-only GITHUB_TOKEN permissions where possible.
            """.trimIndent()
        ),
        SkillItem(
            id = "github-actions",
            title = "CI/CD Pipeline Optimizer",
            category = "DevOps & CI/CD",
            description = "Hardens GitHub Actions workflows with caching, concurrency cancellation, and minimal privilege tokens.",
            targetPlatform = "GitHub Actions",
            content = """
# GitHub Actions Optimization Skill

### Best Practices:
- Always pin actions to full commit SHA or trusted major tags.
- Use explicit timeout-minutes on all jobs.
- Concurrency group with cancel-in-progress on pull requests.
- Cache Gradle/pip/npm artifacts cleanly.
            """.trimIndent()
        ),
        SkillItem(
            id = "llms-txt",
            title = "llms.txt Standard Generator",
            category = "AI Metadata",
            description = "Generates clean, standardized llms.txt and llms-full.txt files for AI indexers.",
            targetPlatform = "Web Standards / llms.txt",
            content = """
# llms.txt Generator Skill

Implements the llms.txt proposal for providing condensed, markdown-formatted project context to LLMs.
- Concise summary of primary APIs and documentation links.
- Strips HTML fluff and navigation headers.
            """.trimIndent()
        )
    )

    val instructions = listOf(
        InstructionItem(
            id = "universal-core",
            title = "Universal Core Guidance",
            category = "Foundation",
            summary = "Direct, factual, never-inventing baseline for any assistant.",
            promptText = """
Respond naturally and directly. Address the user's main need first.
Match detail to the task. Keep simple answers short; develop complex topics far enough to support understanding or a decision. Do not hide material risks, conditions, or exceptions just to stay concise.
Separate facts, assumptions, interpretations, and recommendations when the distinction matters. State uncertainty where it belongs. Never invent sources, quotes, files, tool output, checks, or completed actions.
Style must remain subordinate to correctness, safety, permissions, user intent, and the requested output format.
            """.trimIndent()
        ),
        InstructionItem(
            id = "friendly-style",
            title = "Friendly Style",
            category = "Voice",
            summary = "Warm, collaborative everyday tone without hype.",
            promptText = """
Use warm, clear everyday language without forced enthusiasm. Treat the user as a capable collaborator. Explain difficult ideas without talking down to them. Match the user's register while keeping the response readable and accurate.
            """.trimIndent()
        ),
        InstructionItem(
            id = "professional-style",
            title = "Professional Style",
            category = "Voice",
            summary = "Precise terminology, tradeoff-focused, zero bureaucratic filler.",
            promptText = """
Lead with the conclusion or most important answer. Use precise terminology and explicit criteria. State tradeoffs and risks when they affect the decision. Avoid bureaucratic filler, marketing superlatives, and theatrical certainty.
            """.trimIndent()
        ),
        InstructionItem(
            id = "concise-style",
            title = "Concise & Direct",
            category = "Voice",
            summary = "Start with result, strip boilerplate and ritual closings.",
            promptText = """
Start with the result. Remove repeated framing, obvious restatements, and ritual closing lines. Keep necessary caveats and evidence even when the answer is short.
            """.trimIndent()
        ),
        InstructionItem(
            id = "critical-style",
            title = "Critical & Skeptical",
            category = "Voice",
            summary = "Stress-test assumptions, spot weak points, direct skepticism at claims.",
            promptText = """
Inspect claims, assumptions, and unnecessary complexity. Point out the weak part and suggest a concrete correction. Direct skepticism at the claim or system, not at the user.
            """.trimIndent()
        ),
        InstructionItem(
            id = "working-behavior",
            title = "Working Behavior Policy",
            category = "Collaboration",
            summary = "Guidelines for asking questions, assuming safely, and progress reports.",
            promptText = """
Ask only when missing information materially blocks useful progress. Prefer reasonable reversible assumptions when safe. Verify important claims and state changes in proportion to their risk. For multi-step work, communicate only material milestones rather than narrating every operation.
            """.trimIndent()
        )
    )

    val templates = listOf(
        TemplateItem(
            id = "outcome-task",
            filename = "outcome-task.md",
            type = "Markdown",
            description = "Structured template for goal-driven autonomous agent tasks.",
            code = """
# Outcome-Oriented Task Specification

## 1. Primary Objective
[State the exact observable end-state]

## 2. Constraints & Boundaries
- Strictly avoid: [Forbidden actions]
- Reversible assumptions allowed: [Yes/No]

## 3. Success Criteria & Verification
- [ ] Automated verification step passes
- [ ] User review checklist satisfied
            """.trimIndent()
        ),
        TemplateItem(
            id = "openai-agent",
            filename = "openai-agent.py",
            type = "Python",
            description = "Minimal runnable agent implementation loading .ai profile instructions.",
            code = """
import os
from pathlib import Path
from openai import OpenAI

client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))

def load_system_prompt() -> str:
    instructions_path = Path("generated/instructions.md")
    if instructions_path.exists():
        return instructions_path.read_text(encoding="utf-8")
    return "Write naturally and directly."

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[
        {"role": "system", "content": load_system_prompt()},
        {"role": "user", "content": "Explain git submodules concisely"}
    ]
)
print(response.choices[0].message.content)
            """.trimIndent()
        ),
        TemplateItem(
            id = "wrangler-jsonc",
            filename = "wrangler.jsonc",
            type = "JSONC",
            description = "Cloudflare Workers AI deployment configuration.",
            code = """
{
  "name": "ai-core-worker",
  "main": "src/index.ts",
  "compatibility_date": "2026-08-01",
  "vars": {
    "PROFILE_ID": "default"
  }
}
            """.trimIndent()
        ),
        TemplateItem(
            id = "dev-vars",
            filename = ".dev.vars.example",
            type = "Env",
            description = "Segregated secret environment variables template.",
            code = """
# Security: Keep secrets out of the public .ai core repo
OPENAI_API_KEY=sk-...
ANTHROPIC_API_KEY=sk-ant-...
GEMINI_API_KEY=...
            """.trimIndent()
        )
    )
}
