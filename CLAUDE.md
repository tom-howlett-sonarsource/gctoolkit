<!-- benchmark:omit-sonar-secrets-on-read -->
<!-- benchmark:omit-sonar-agentic-hook -->

# General principles

- Use red-green development; code coverage on new code must exceed 80%.
- Prioritize security, reliability, and maintainability. This code is maintained for many years and used by tens of thousands of developers.
- Do not add unnecessary code. Remove unused code, avoid duplication, and extract common functionality.
- Follow existing design patterns unless instructed otherwise.

# Optional SonarQube support

SonarQube tools and skills may be available in this environment. If they are available, decide whether they help you understand, navigate, implement, or verify the task. Use Context Augmentation and semantic navigation throughout the task whenever they are more efficient or accurate than file or text search. There is no fixed limit on context, navigation, or MCP calls.

When you use SonarQube, briefly state what capability you used and how it influenced the work. Do not expose credentials or authentication material.

The automatic per-edit agentic-analysis hook is deliberately disabled for this campaign. Before final verification, if `sonar analyze agentic` is available, run exactly one deep agentic analysis covering every changed production source file except metadata-only `module-info.java` and `package-info.java` files. Supply the remaining changed production files using repeated `--file` arguments and use `--depth DEEP`. Include `--branch` only when `git branch --show-current` returns a non-empty branch name. If agentic analysis is unavailable, continue without failing the task.

<!-- sonar:begin:sonarqube-agentic-analysis-protocol -->
# SonarQube Agentic Analysis protocol

SonarQube Agentic Analysis is the final confirmation layer at the end of every turn in which you wrote to one or more files in the workspace (create, edit, patch, format — any tool call that changed file contents on disk).

**Trigger:** the moment before you send your final reply for the turn.

When the trigger fires, you **MUST** run SonarQube Agentic Analysis on the full set of files you modified this turn — **one command, not a per-file loop**.

**Preferred:** pass every path you modified this turn in a single invocation (repeat `--file` for each path). Use paths **relative to the project root** (e.g. `src/foo.ts`). For end-of-turn analysis, **always** pass `--depth DEEP` — even when you changed only one file — so cross-file findings are included.

```bash
sonar analyze agentic --project tom-howlett-sonarsource_gctoolkit --depth DEEP --file <path/to/file1> --file <path/to/file2>
```

**Fallback:** when you cannot reliably list every modified path (e.g. you did not track them, or git state is the only source of truth), use the git change set with DEEP analysis:

```bash
sonar analyze agentic --project tom-howlett-sonarsource_gctoolkit --depth DEEP
```

Per-edit hooks run faster STANDARD analysis. End-of-turn analysis must always use `--depth DEEP` (including a single `--file`). Use `--depth STANDARD` only for optional faster passes — never for the mandatory end-of-turn run.

Non-negotiable rules:

1. Mid-turn edits do **not** need SonarQube Agentic Analysis — only the final on-disk state matters. Skipping SonarQube Agentic Analysis at end-of-turn is a protocol violation.
2. Run SonarQube Agentic Analysis **before** sending your final reply, marking the task done, or handing control back to the user.
3. If SonarQube Agentic Analysis reports issues on lines you touched in this turn, fix them, then re-run SonarQube Agentic Analysis on the same scope (change set or explicit file list). Repeat until clean (or only pre-existing findings on lines you did not touch remain). Pre-existing findings on untouched lines are out of scope — do not "fix" them unless the user asked.
4. If SonarQube Agentic Analysis is skipped (no SonarQube Cloud connection, or no project configured), state the skip reason to the user once and continue — do not retry.
5. Do not suppress, summarize away, or omit SonarQube Agentic Analysis findings from your reply. Surface them verbatim.
<!-- sonar:end:sonarqube-agentic-analysis-protocol -->
