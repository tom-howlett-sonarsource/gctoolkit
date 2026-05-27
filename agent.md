# SonarQube Agentic Workflow - Usage Directive (MUST FOLLOW)

**Always use the Guide-and-Verify workflow** for code generation and modification.

## Project Configuration

- **SonarQube Project Key:** `tom-howlett-sonarsource_gctoolkit`
- **Main Branch:** `main`

## GUIDE Phase - Before Generating Code

**Before generating or editing code** you MUST:
- Call `get_guidelines` for project context and coding standards
- Call `get_current_architecture` and `get_intended_architecture`
-  Locate existing code with `search_by_signature_patterns` or `search_by_body_patterns`. Do this before you explore the files directly, they will speed up the process
-  Read implementation with `get_source_code`
- Analyze impact using:
  - `get_upstream_call_flow` / `get_downstream_call_flow` - trace method calls
  - `get_references` - find all usages
  - `get_type_hierarchy` - If there is a class hierarchy follow its patterns

When coding use red/green TDD. Sonarqube will require 80% code coverage on new code

## VERIFY Phase - After Generating Code

You must strictly follow this Analyze-Then-Commit workflow for every code modification. No code is considered complete until it has passed the following SonarQube validation loop:

1. **Read Phase:** After any modification or before commit, use the `Read` tool to load the current state of all relevant source files.
2. **Analysis Phase:** For every new or modified file, you must call `run_advanced_code_analysis` using:
   - `filePath`: The project-relative path to the file (e.g., `src/main/java/MyClass.java`)
   - `branchName`: The active development branch
   - `fileScope`: `["MAIN"]` or `["TEST"]` depending on the code type
3. **Evaluation & Remediation:**
   - **Rule Lookup:** For every issue flagged, call `show_rule` with the specific rule key (e.g., `java:S1874`)
   - **Mandatory Fixes:** You are prohibited from committing code with **CRITICAL** or **HIGH** issues. Implement fixes based on the rule's rationale and recommended guidance immediately.
4. **Verification:** After applying fixes, re-run the analysis to ensure issues are resolved and no regressions were introduced.
