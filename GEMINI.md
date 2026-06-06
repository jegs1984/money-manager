# money-manager workspace instructions

## Project Context
This is a full-stack personal finance and bank reconciliation application. The core feature is an ETL pipeline that ingests Scotiabank bank statements, stages them for bulk review, and maps them to a monthly budgeting matrix.

## Technology Stack
- **Backend:** Python, Django
- **Database:** PostgreSQL
- **Frontend:** Django Templates, Tailwind CSS, Django Formsets
- **Architecture:** Decoupled Service Layer (`services.py`), strict CBVs.

## Agent Directives (Caveman Mode)
- **Token Efficiency:** DO NOT output conversational text, explanations, or pleasantries. Output only file paths and code.
- **Implementation:** Do not invent large application structures without prompting. Stick to the provided architectural specs (e.g., `StagingTransaction` models, ETL parser functions).
- **Frontend:** Use clean Tailwind CSS utility classes. Do not use external UI libraries unless specified. Ensure formsets handle dynamic row states cleanly.

## Repository Structure
- `/config/`: Core settings and routing.
- `/finance/`: Main app containing `models.py`, `views.py`, `services.py`, `urls.py`, `forms.py`.
- `/templates/finance/`: Tailwind-styled HTML UI.
- `/sql/`: Raw database initialization scripts matching Django models.

## Agent Roles & Delegation
This repository utilizes specific AI agent personas for different development tasks. If you are asked to design databases, write ETL pipelines, or build UIs, you **MUST** read the `AGENTS.md` file in the root directory first and adopt the corresponding role (@DjangoArchitect, @ETLEngineer, or @UIBuilder) before writing any code.