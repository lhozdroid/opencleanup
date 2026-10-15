# Agent Instructions

## Communication

- Keep answers short, direct, and focused on the outcome.
- Use plain language. Explain unfamiliar ideas simply and define jargon when needed.
- Use small examples when they make an explanation clearer.

## Scope

- Do exactly what the user requests; do not introduce scope creep.
- Do not assume requirements, behavior, or acceptance criteria.
- If the request is unclear, ask one focused question before making changes.
- Preserve existing behavior and leave unrelated files untouched.

## Planning and execution

- Make a concise plan before acting.
- Inspect relevant files and established conventions before changing anything.
- Use relevant skills when they improve correctness or consistency, and follow their instructions.
- Split independent work into bounded workstreams when parallel help is available.
- Give each workstream a clear, non-overlapping scope; coordinate shared changes and consolidate results.

## Changes and quality

- Keep changes surgical, minimal, and easy to understand.
- Prefer the simplest solution; avoid premature abstractions, unrelated refactors, and speculative features.
- Validate changes with checks appropriate to their risk.
- Report the result and any remaining limitations briefly.
- Every Java method and constructor must have a formal Javadoc comment.

## Skill governance

- Use [AutoSkills](https://www.autoskills.sh/) to detect project technologies and review its recommendations with `npx autoskills --dry-run` before installation.
- Use [Skills.sh](https://www.skills.sh/) to search for and install additional relevant skills with `npx skills find <query>` and `npx skills add <owner/repo> --skill <skill> -y --copy`.
- Keep project-approved, repository-owned skills in the tracked `.agents/skills` directory. Each skill must have a `SKILL.md` file and may include only supporting resources needed by that skill.
- Use `.agents/.staging` for temporary skill imports, review the contents, then move accepted skills into `.agents/skills` and remove the staging directory.
- Discover and use skills from `.agents/skills` before relying on user- or system-level copies with the same purpose.
- Add only skills relevant to the current project and task; review their contents before use.
