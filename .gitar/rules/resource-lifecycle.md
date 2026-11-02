---
name: resource-lifecycle
description: Trace resource ownership changes through production callers and publish an evidence-linked lifecycle assessment on the PR.
---

# Resource lifecycle assessment

Assess whether the PR preserves correct resource ownership from
acquisition through consumption and cleanup. Inspect the current
revision and relevant unchanged code. Do not modify code.

## Investigation

1. Identify changed resource acquisition, ownership, closing contracts,
   and error handling. Include interfaces, implementations, wrappers,
   and parallel paths relevant to the change.

2. Trace returned resources through production callers to terminal
   consumption. Continue past intermediate wrappers and stream
   transformations. Tests alone do not establish production safety.

3. For each materially distinct path, establish:
   - Where resources are acquired.
   - Which object or caller owns them.
   - What actually triggers cleanup.
   - Whether cleanup occurs after normal completion, short-circuiting,
     setup failure, and failure during consumption.
   - Whether cleanup failures preserve the original exception and still
     release other owned resources.

4. For Java streams, distinguish terminal consumption from closing.
   Do not assume EOF or a terminal operation closes the stream.
   Check inner and outer stream ownership separately when flatMap or
   other wrappers are involved.

5. Separate defects introduced or exposed by this PR from unchanged,
   pre-existing issues. Report behavior changes, such as newly
   propagated exceptions, separately from resource leaks.

Use available tools to inspect callers and implementations. If a path
cannot be traced, identify the missing evidence rather than assuming
it is safe. Do not claim tests ran unless execution results are available.

## Verdicts

Use exactly these verdicts:

- SATISFIED: inspected evidence establishes cleanup for the applicable
  lifecycle paths.
- VIOLATED: inspected evidence demonstrates a concrete cleanup defect.
- UNVERIFIED: relevant ownership, callers, or failure paths remain
  unresolved.
- NOT APPLICABLE: the scenario cannot occur on the assessed path,
  with a brief explanation.

Do not mark a path SATISFIED merely because cleanup would work if an
unverified caller closed the resource.

Overall assessment:
- VIOLATED if any relevant path is violated.
- Otherwise UNVERIFIED if any relevant path remains unverified.
- Otherwise SATISFIED.

Keep this assessment distinct from Gitar's overall code-review verdict.

## Publish the assessment

Post a separate top-level PR comment titled:

### Resource lifecycle assessment

Do not place the full report only inside the dashboard's Verification
summary. If an earlier standalone assessment from this skill can be
updated, update it; otherwise post a new comment.

Start with:
- The assessed commit SHA.
- The overall assessment.
- The evidence basis: code inspection, executed tests, or both.

Then produce an actual GitHub-flavoured Markdown table with exactly
these columns:

| Production path | Resource owner | Cleanup and lifecycle coverage | Verdict | Evidence |
| --- | --- | --- | --- | --- |

Replace the empty table with evidence-backed rows:
- One row per materially distinct production path.
- Keep each cell short; put detailed explanations below the table.
- Use one physical line per row.
- Include clickable links to inspected code at the assessed revision.
- Escape literal pipe characters inside cells.
- Put blank lines before and after the table.
- Do not wrap the table in a code fence, HTML, or a details element.
- Do not substitute numbered prose beginning with "Table:".
- Do not invent rows or omit relevant paths to meet a row count.

Below the table, include only sections that have content:

**Concrete defects**
Explain the trigger, consequence, and affected symbols.

**Unresolved evidence**
List what remains unverified and which callers or paths need inspection.

**Other observations**
Separate behavior changes, pre-existing issues, and optional improvements.

End with a brief statement of test execution and its limits.
If tests were not run, say: "Tests were not executed; conclusions are
based on code inspection."

Before publishing, check that every SATISFIED verdict is supported,
the overall assessment follows the verdict rules, and the table
contains a header, separator, and consistently structured data rows.

If posting a separate comment is unavailable, return the complete
Markdown report in the response and state that it was not published.