---
title: "Resource lifecycle impact assessment"
type: check
when: "A PR changes file-backed streams or their resource ownership and closing contracts"
actions: "Post a resource lifecycle assessment with code references, including when all checks pass"
---

Trace the changed contract through interfaces, implementations,
wrappers, and production callers.

Report a compact table showing:
- Where each resource is acquired.
- Who owns and closes it.
- What happens on normal completion, short-circuiting, and failure.
- The file and symbol supporting each conclusion.

Mark each assessed path satisfied, violated, or unverified.
Explain concrete defects separately from optional improvements.
State explicitly whether conclusions came from code inspection or executed tests. 