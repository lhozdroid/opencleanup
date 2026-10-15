---
name: git-commit
description: Create Git commits with a concise keyword-prefixed subject and a detailed bullet-list body when the user asks to commit changes.
---

# Git commit

Use this skill when the user explicitly invokes `$git` or asks to commit the current changes.

## Workflow

1. Inspect the repository with `git status --short`, `git diff`, and `git diff --cached`. Inspect relevant untracked files before staging them.
2. Determine the intended commit scope. Commit all changes in scope, but do not silently combine clearly unrelated work. If the scope is ambiguous, stop and ask the user.
3. Stage only the intended files and review the staged diff. Do not edit application files just to prepare the commit.
4. Create the commit message in this exact shape:

   ```text
   <keyword>: <What is the change>

   - Change 1 in specific detail
   - Change 2 in specific detail
   - Continue with as many bullets as necessary
   ```

   Choose a short, lowercase keyword that describes the change, such as `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, or `config`. Make the subject concise, specific, and present-tense; do not end it with a period. Each body line must start with `- ` and describe a meaningful change, including relevant behavior, files, configuration, tests, or user-visible effects. List every meaningful change in enough detail to explain the complete diff.
5. Run `git diff --cached --check`, then create the commit using the prepared subject and body. Preserve normal commit hooks; do not use `--no-verify` unless the user explicitly requests it.
6. Confirm the commit succeeded and report its short hash and complete message. Do not push, amend, reset, clean, or rewrite history unless the user separately requests that action.

If there are no changes to commit, report that instead of creating an empty commit. If a hook or Git command fails, report the failure and leave the repository state intact as far as possible.
