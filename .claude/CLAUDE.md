# graphify

## Safety Approval

Before running any potentially dangerous command or action, explain the exact impact and ask the user for explicit approval. This includes destructive filesystem or database changes, privileged/system-wide changes, external side effects, and commands that may overwrite, delete, expose, or irreversibly alter data. Do not proceed until approval is given.

- **graphify** (`.claude/skills/graphify/SKILL.md`) is the knowledge-graph entry point. When the user types `/graphify`, invoke the Skill tool with `skill: "graphify"` before doing anything else.
- For every codebase question, first run `graphify query "<question>"` when `graphify-out/graph.json` exists. Use `graphify explain "<concept>"` for focused context and `graphify path "<A>" "<B>"` for relationships.
- Do not use broad raw-source search for discovery when the graph is available. Read source only to modify/debug identified code or when Graphify lacks needed detail.
- Use `graphify-out/wiki/index.md` for broad navigation when available; use `graphify-out/GRAPH_REPORT.md` only for architecture-wide context or after focused queries are insufficient.
- Dirty Graphify artifacts are expected and do not justify skipping Graphify. After code changes, run `graphify update .`; documentation-only changes do not require an update.

## Communication Style

- **No summaries, no thinking out loud, no preamble.** Do not explain what you are about to do or narrate your reasoning.