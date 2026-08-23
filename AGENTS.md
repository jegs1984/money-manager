# Repository Guidelines

## Safety Approval

Before running any potentially dangerous command or action, explain the exact impact and ask the user for explicit approval. This includes destructive filesystem or database changes, privileged/system-wide changes, external side effects, and commands that may overwrite, delete, expose, or irreversibly alter data. Do not proceed until approval is given.

## Project Structure & Module Organization

The Django web app lives at the repository root. `config/` contains settings and URL/WGSI configuration; `finance/` contains models, forms, views, services, URLs, and migrations. Keep request handlers thin and put parsing, reconciliation, and export logic in `finance/services.py`. Server-rendered pages are in `templates/finance/`.

The native client is in `android/`. Kotlin code is under `android/app/src/main/java/com/moneymanager/`, organized into `data/`, `domain/`, `ui/`, `notifications/`, and `di/`; tests belong in `android/app/src/test/`. Database initialization/reference scripts live in `sql/`, while Docker support is in `docker/` and `docker-compose.yml`.

## Build, Test, and Development Commands

- `python bootstrap.py` creates the virtual environment, installs Python dependencies, prepares PostgreSQL, and applies migrations.
- `bash start.sh` starts the native macOS development stack at `http://127.0.0.1:8765`.
- `source venv/bin/activate && python manage.py runserver 127.0.0.1:8765` runs Django manually.
- `python manage.py test` runs Django tests; add tests as web behavior changes.
- `docker compose up --build` builds and starts the containerized web app and database.
- `cd android && ./gradlew test` runs Android JVM unit tests; `./gradlew assembleDebug` produces a debug APK.

## Coding Style & Naming Conventions

Follow the surrounding code: Python uses four-space indentation, `snake_case` functions/variables, and `PascalCase` Django models/forms. Use descriptive migration names and do not edit an applied migration. Kotlin uses four-space indentation, `PascalCase` classes/composables, and `camelCase` functions/properties. Keep Compose screens and their ViewModels paired by feature (for example, `CategoriesScreen.kt` and `CategoriesViewModel.kt`). No formatter or linter is currently enforced; avoid unrelated reformatting.

## Testing Guidelines

Use Django's built-in test runner for server changes and JUnit/MockK tests for Android domain logic. Name Android tests `*Test.kt` and test observable behavior, especially statement parsing and duplicate detection. Run the relevant test command before opening a PR; no coverage threshold is configured.

## Commit & Pull Request Guidelines

Recent history favors short imperative summaries, commonly `feat: ...`, `Fixes`, or `Refactor`; prefer a clear conventional-style subject such as `feat: add statement duplicate warning`. Keep commits focused. PRs should explain the user-visible change, identify schema or configuration changes, link relevant issues, list validation performed, and include screenshots for Django or Compose UI changes. Never commit `.env`, release keystores, database dumps, or generated Gradle/Python cache files.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, invoke the `skill` tool with `skill: "graphify"` before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Communication Style

- **No summaries, no thinking out loud, no preamble.** Do not explain what you are about to do or narrate your reasoning.