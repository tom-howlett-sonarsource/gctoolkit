# SonarQube Agentic Workflow - Usage Directive (MUST FOLLOW)

**Always use the Guide-and-Verify workflow** for code generation and modification.

## Project Configuration

- **SonarQube Project Key:** `tom-howlett-sonarsource_gctoolkit`
- **Main Branch:** `main`

## GUIDE Phase - Before Generating Code

You MUST use the sonar-context-augmentation skill. Use it outside the sandbox
The code is well structured ensure any changes do not degrade it, respect the class hierarchy and follow the intended design. Consider future maintenance cost on any change you make. 
When coding use red/green TDD. Sonarqube will require 80% code coverage on new code

## VERIFY Phase - After Generating Code

a post tool hook will call sonar analyse, fix any new issues found

