# 09_AGENT_ORCHESTRATION

## LOGICAL ROLES
The system runs multiple virtual roles coordinated by Hermes and executed by Roo Code:
1. **Architect**: High-level reasoning, system design (Hermes).
2. **Coder**: Generation of targeted file patches (Roo Code).
3. **QA**: Test execution via ADB shell (Automated script + Roo Code).

## ROUTING RULES
- **Skill Execution**: If the user asks for a persistent workflow (e.g., "Summarize my meetings daily"), the prompt is routed to the **Hermes Adapter** to create a persistent skill.
- **Code Editing**: If the user asks for a codebase change (e.g., "Refactor this ViewModel"), the prompt is routed to the **Roo Code Adapter**.
- **General Inference**: Simple chat or local visual reasoning is routed directly to the **Edge Adapter**.

## CONTEXT BOUNDARIES
- Roo Code is restricted to reading from `files/projects/` and cannot read `files/skills/` unless explicitly permissioned.
- Hermes manages long-term SQLite memory but cannot directly mutate `files/projects/` without invoking Roo Code's patch generator.
- This prevents a runaway agent from corrupting the model catalog or skill database.
