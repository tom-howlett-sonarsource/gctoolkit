# Responsibility Ownership and Lifecycle

When a change introduces or moves a responsibility, trace the affected behavior through related implementations and callers before reviewing it. Include relevant interfaces, implementations, wrappers, and parallel paths.

Check whether:

- The behavior belongs in an existing abstraction rather than being duplicated or assigned to the wrong layer.
- Related implementations and parallel paths preserve consistent behavior.
- Resource ownership and lifecycle are explicit across acquisition, use, short-circuiting or failure, and cleanup.

Report a finding only when the change has a concrete, actionable consequence supported by code evidence. Identify the changed file and symbol, and cite the related file and symbol or caller that demonstrates the impact. Explain the consequence rather than making a generic recommendation.

Distinguish demonstrated defects from optional refactoring preferences. Do not present a design preference, hypothetical risk, or unverified inconsistency as a defect; omit it or label it clearly as optional. If the investigation finds no concrete issue, do not manufacture a finding.
