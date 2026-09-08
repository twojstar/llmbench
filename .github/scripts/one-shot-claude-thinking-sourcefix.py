from pathlib import Path

path = Path(".github/scripts/one-shot-claude-thinking.py")
lines = path.read_text().splitlines()
repairs = 0
for index, line in enumerate(lines):
    if line.startswith("test = test.replace") and "claude-sonnet-5" in line and "TEST_CLAUDE_MODEL" in line:
        lines[index] = '''test = test.replace('"claude-sonnet-5"', 'TEST_CLAUDE_MODEL')'''
        repairs += 1
    elif line.startswith("test = test.replace") and "claude-haiku-4-5-20251001" in line and "TEST_CLAUDE_LEGACY_MODEL" in line:
        lines[index] = '''test = test.replace('"claude-haiku-4-5-20251001"', 'TEST_CLAUDE_LEGACY_MODEL')'''
        repairs += 1
assert repairs == 2, f"expected two generator source repairs, found {repairs}"
path.write_text("\n".join(lines) + "\n")
