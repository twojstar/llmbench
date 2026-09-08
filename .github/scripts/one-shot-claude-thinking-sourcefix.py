from pathlib import Path

path = Path(".github/scripts/one-shot-claude-thinking.py")
lines = path.read_text().splitlines()
bad_sonnet = """test = test.replace('"claude-sonnet-5"', 'TEST_CLAUDE_MODEL)"""
good_sonnet = """test = test.replace('"claude-sonnet-5"', 'TEST_CLAUDE_MODEL')"""
bad_legacy = """test = test.replace('"claude-haiku-4-5-20251001"', 'TEST_CLAUDE_LEGACY_MODEL)"""
good_legacy = """test = test.replace('"claude-haiku-4-5-20251001"', 'TEST_CLAUDE_LEGACY_MODEL')"""
repairs = 0
for index, line in enumerate(lines):
    if line == bad_sonnet:
        lines[index] = good_sonnet
        repairs += 1
    elif line == bad_legacy:
        lines[index] = good_legacy
        repairs += 1
assert repairs == 2, f"expected two generator source repairs, found {repairs}"
path.write_text("\n".join(lines) + "\n")
