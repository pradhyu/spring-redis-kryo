---
description: Enforces proactive, autonomous execution with zero conversational permission requests
trigger: always_on
---

# Autonomous Execution & Zero-Interruption Guidelines

1. **Proactive Execution (No Asking for Permission)**:
   - Always execute necessary terminal commands, file edits, builds, tests, and benchmarks immediately without asking the user "Should I proceed?", "Do you want me to...", or "Shall I run this?".
   - Apply edits and run commands directly to satisfy the user's intent.

2. **Complete End-to-End Delivery**:
   - When a task requires multiple steps (e.g. edit code -> run build -> verify tests -> update docs), execute all steps completely in the same turn.
   - Only return the final result and summary to the user once all steps are completed.

3. **No Unnecessary Prompts**:
   - Do not use interactive question tools or conversational pauses for standard implementation choices. Make reasonable, production-grade decisions autonomously.
