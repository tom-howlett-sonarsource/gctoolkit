<!-- benchmark:omit-sonar-secrets-on-read -->

# General principles

- Use red-green development; code coverage on new code must exceed 80%.
- Prioritize security, reliability, and maintainability. This code is maintained for many years and used by tens of thousands of developers.
- Do not add unnecessary code. Remove unused code, avoid duplication, and extract common functionality.
- Follow existing design patterns unless instructed otherwise.

# Optional SonarQube support

SonarQube tools and skills may be available in this environment. If they are available, decide whether they help you understand, implement, or verify the task. Do not fail the task merely because they are unavailable.

When you use SonarQube, briefly state what capability you used and how it influenced the work. Do not expose credentials or authentication material.

The automatic agentic-analysis hook may not observe edits made through shell commands. As a fallback, before final verification, if `sonar analyze agentic` is available, run one agentic analysis covering every changed production source file except metadata-only `module-info.java` and `package-info.java` files. Supply the remaining changed production files using repeated `--file` arguments and always include `--project "$SONAR_CONTEXT_PROJECT"`. Include `--branch` only when `git branch --show-current` returns a non-empty branch name. If agentic analysis is unavailable, continue without failing the task.
