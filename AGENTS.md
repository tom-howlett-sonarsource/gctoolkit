# General principles
* Use red-green development, code coverage on new code must be >80%
* When generating code prioritise security, reliability and maintainability. This code will need to be maintained for many years and will be used by 10000s of developers. 
* Do not add unecessary code, remove any code that is not used, avoid duplication, extract common functionality
* Follow existing design patterns unless instructed not to

<!-- sonar:begin:sonarqube-agentic-analysis-protocol -->
# SonarQube Agentic Analysis protocol

SonarQube Agentic Analysis is the final confirmation layer at the end of every turn in which you wrote to one or more files in the workspace (create, edit, patch, format — any tool call that changed file contents on disk).

**Trigger:** the moment before you send your final reply for the turn.

When the trigger fires, you **MUST**:

1. Build the set of unique file paths you modified during this turn.
2. Run SonarQube Agentic Analysis once for each unique path. The command analyses one file per invocation:

```bash
sonar analyze agentic --project tom-howlett-sonarsource_gctoolkit --file <path/to/modified/file>
```

Non-negotiable rules:

1. Mid-turn edits do **not** need SonarQube Agentic Analysis — only the final on-disk state matters. Skipping SonarQube Agentic Analysis for any modified file at end-of-turn is a protocol violation.
2. Run SonarQube Agentic Analysis **before** sending your final reply, marking the task done, or handing control back to the user.
3. If SonarQube Agentic Analysis reports issues on lines you touched in this turn, fix them, then re-run SonarQube Agentic Analysis on that file. Repeat until the file is clean (or only pre-existing findings on lines you did not touch remain). Pre-existing findings on untouched lines are out of scope — do not "fix" them unless the user asked.
4. If SonarQube Agentic Analysis is skipped (no SonarQube Cloud connection, or no project configured), state the skip reason to the user once and continue — do not retry.
5. Do not suppress, summarize away, or omit SonarQube Agentic Analysis findings from your reply. Surface them verbatim.
<!-- sonar:end:sonarqube-agentic-analysis-protocol -->
