# Graph Report - money-manager  (2026-08-22)

## Corpus Check
- 91 files · ~50,277 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 818 nodes · 2981 edges · 78 communities (55 shown, 23 thin omitted)
- Extraction: 48% EXTRACTED · 52% INFERRED · 0% AMBIGUOUS · INFERRED: 1552 edges (avg confidence: 0.5)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9556d287`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 68|Community 68]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 72|Community 72]]
- [[_COMMUNITY_Community 73|Community 73]]
- [[_COMMUNITY_Community 82|Community 82]]
- [[_COMMUNITY_Community 83|Community 83]]
- [[_COMMUNITY_Community 84|Community 84]]
- [[_COMMUNITY_Community 86|Community 86]]

## God Nodes (most connected - your core abstractions)
1. `Period` - 79 edges
2. `Category` - 79 edges
3. `Transaction` - 79 edges
4. `StagingTransaction` - 79 edges
5. `StagingCCTransaction` - 79 edges
6. `MerchantRule` - 78 edges
7. `Account` - 76 edges
8. `BudgetItem` - 76 edges
9. `Goal` - 76 edges
10. `RecurringPlan` - 75 edges

## Surprising Connections (you probably didn't know these)
- `BudgetItem` --references--> `Category`  [EXTRACTED]
  finance/services.py → README.md
- `StagingReviewView` --implements--> `Base Template`  [EXTRACTED]
  finance/views.py → templates/finance/base.html
- `_get_or_create_budget_item()` --calls--> `run()`  [INFERRED]
  finance/services.py → bootstrap.py
- `_parse_header()` --references--> `Map`  [EXTRACTED]
  finance/services.py → android/app/src/main/java/com/moneymanager/domain/usecase/ParseStatementUseCases.kt
- `StagingReviewView` --inherits--> `Screen`  [EXTRACTED]
  finance/views.py → android/app/src/main/java/com/moneymanager/ui/navigation/NavGraph.kt

## Import Cycles
- 1-file cycle: `finance/services.py -> finance/services.py`

## Communities (78 total, 23 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.28
Nodes (97): Account, BudgetItem, Category, CreateView, DeleteView, Base Template, AccountForm, BudgetItemForm (+89 more)

### Community 2 - "Community 2"
Cohesion: 0.23
Nodes (4): get_duplicate_staging_ids(), get_duplicate_staging_matches(), Return the set of staging row IDs whose (date, amount, description) triple     a, Return the ledger transaction that caused each staged duplicate warning.

### Community 3 - "Community 3"
Cohesion: 0.18
Nodes (7): date, _add_months(), Move a date forward without invalid month-end dates., record_transfer_service(), reverse_transaction_service(), TestCase, LedgerServiceTests

### Community 4 - "Community 4"
Cohesion: 0.11
Nodes (18): 1. Set up the budget, 2. Add transactions, 3. Import a bank statement, 4. Import a credit-card statement, 5. Android notification capture, 6. Review and reporting, 7. Accounts, corrections, and reconciliation, 8. Planning and automation (+10 more)

### Community 5 - "Community 5"
Cohesion: 0.09
Nodes (17): BudgetItemDao, CategoryDao, PeriodDao, StagingCCTransactionDao, StagingTransactionDao, TransactionDao, BudgetItemDao, CategoryDao (+9 more)

### Community 6 - "Community 6"
Cohesion: 0.22
Nodes (8): Android workflow, Before a pull request, Database changes, Development guide, Estilos locales, Fixtures bancarios, Local web workflow, Project conventions

### Community 7 - "Community 7"
Cohesion: 0.09
Nodes (17): BudgetItemEntity, CategoryEntity, Flow, List, LocalDate, Long, PeriodEntity, StagingCCTransactionEntity (+9 more)

### Community 8 - "Community 8"
Cohesion: 0.08
Nodes (23): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+15 more)

### Community 10 - "Community 10"
Cohesion: 0.22
Nodes (21): create_database(), create_env_file(), create_venv(), die(), django_setup(), info(), install_deps(), load_env() (+13 more)

### Community 13 - "Community 13"
Cohesion: 0.06
Nodes (34): BigDecimal, Boolean, CategoryEntity, CategoryViewModel, DupAction, List, Long, StagingTransactionEntity (+26 more)

### Community 14 - "Community 14"
Cohesion: 0.20
Nodes (13): BigDecimal, List, LocalDate, String, _parse_cc_header(), _parse_header(), InputStream, Map (+5 more)

### Community 15 - "Community 15"
Cohesion: 0.29
Nodes (6): Android setup, First-use checklist, Installation, Manual Python setup, Option A: native macOS installer, Option B: Docker Compose

### Community 16 - "Community 16"
Cohesion: 0.25
Nodes (7): Backup and recovery, Docker backup, Docker volume warning, Encrypted application bundle, Native PostgreSQL backup, Recovering a failed import, Recovery check

### Community 17 - "Community 17"
Cohesion: 0.33
Nodes (5): Install, macOS installer, Manual launch, Uninstall, What it installs

### Community 26 - "Community 26"
Cohesion: 0.31
Nodes (8): BigDecimal, List, Long, Set, StagingCCTransactionEntity, StagingTransactionEntity, DetectStagingDuplicatesUseCase, toBigDecimal()

### Community 29 - "Community 29"
Cohesion: 0.70
Nodes (4): info(), success(), warn(), uninstall.sh script

### Community 30 - "Community 30"
Cohesion: 0.17
Nodes (11): Decimal, calculate_safe_to_spend(), _clp(), contribute_to_goal(), generate_dashboard_pdf(), _parse_clp_amount(), parse_scotiabank_cc_statement(), Format a Decimal as Chilean peso string: $1.234.567 (+3 more)

### Community 31 - "Community 31"
Cohesion: 0.15
Nodes (12): first, amount, balance, date, type, second, amount, balance (+4 more)

### Community 32 - "Community 32"
Cohesion: 0.50
Nodes (3): Array, RoomMigrations, Migration

### Community 33 - "Community 33"
Cohesion: 0.07
Nodes (41): BigDecimal, Boolean, CategoryEntity, CategoryViewModel, DupAction, List, Long, StagingCCTransactionEntity (+33 more)

### Community 34 - "Community 34"
Cohesion: 0.25
Nodes (7): graphify reference: extra exports and benchmark, Step 6b - Wiki (only if --wiki flag), Step 7 - Neo4j export (only if --neo4j or --neo4j-push flag), Step 7b - SVG export (only if --svg flag), Step 7c - GraphML export (only if --graphml flag), Step 7d - MCP server (only if --mcp flag), Step 8 - Token reduction benchmark (only if total_words > 5000)

### Community 36 - "Community 36"
Cohesion: 0.20
Nodes (9): BigDecimal, BudgetItemEntity, CategoryEntity, ImportBatchEntity, PeriodEntity, StagingCCTransactionEntity, StagingTransactionEntity, toBigDecimal() (+1 more)

### Community 37 - "Community 37"
Cohesion: 0.20
Nodes (9): Agent Directives (Caveman Mode), Agent Roles & Delegation, graphify, Mandatory Graphify Workflow, money-manager workspace instructions, Project Context, Repository Structure, Safety Approval (+1 more)

### Community 38 - "Community 38"
Cohesion: 0.25
Nodes (7): Agent Directives (Caveman Mode), Agent Roles & Delegation, money-manager workspace instructions, Project Context, Repository Structure, Safety Approval, Technology Stack

### Community 39 - "Community 39"
Cohesion: 0.48
Nodes (6): die(), info(), PATH, success(), warn(), setup.sh script

### Community 40 - "Community 40"
Cohesion: 0.09
Nodes (28): BigDecimal, Boolean, BudgetItemEntity, CategoryEntity, Flow, List, LocalDate, Long (+20 more)

### Community 41 - "Community 41"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 42 - "Community 42"
Cohesion: 0.60
Nodes (3): LocalDate, String, Converters

### Community 43 - "Community 43"
Cohesion: 0.53
Nodes (3): Boolean, Context, NotificationPermissionHelper

### Community 44 - "Community 44"
Cohesion: 0.60
Nodes (4): die(), info(), PATH, run.sh script

### Community 45 - "Community 45"
Cohesion: 0.70
Nodes (4): die(), info(), success(), update.sh script

### Community 46 - "Community 46"
Cohesion: 0.20
Nodes (8): _parse_amount(), _parse_date(), parse_scotiabank_statement(), _parse_signed_balance(), Parse a debit/credit amount, which is always stored as positive., Parse balances without losing their sign., Return a reviewable category suggestion from a user-confirmed merchant rule., suggest_category()

### Community 47 - "Community 47"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 48 - "Community 48"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 49 - "Community 49"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

### Community 69 - "Community 69"
Cohesion: 0.33
Nodes (6): Daily operations, Docker deployment, Environment variables, Persistence and safety, Requirements, Start the stack

### Community 70 - "Community 70"
Cohesion: 0.07
Nodes (25): Android app, Bank-notification capture, Build and test, Data and upgrades, Money Manager for Android, Related guides, Release signing, Requirements (+17 more)

### Community 72 - "Community 72"
Cohesion: 0.22
Nodes (8): Build, Test, and Development Commands, Coding Style & Naming Conventions, Commit & Pull Request Guidelines, graphify, Project Structure & Module Organization, Repository Guidelines, Safety Approval, Testing Guidelines

### Community 73 - "Community 73"
Cohesion: 0.15
Nodes (15): _advance_recurring_date(), _assert_period_open(), _bundle_key(), close_period_service(), duplicate_period_budget_items(), export_finance_bundle(), _get_or_create_unplanned_category(), import_finance_bundle() (+7 more)

### Community 83 - "Community 83"
Cohesion: 0.50
Nodes (3): RawBankNotification, BankNotificationParser, Result

## Knowledge Gaps
- **216 isolated node(s):** `PreToolUse`, `Bundle`, `PeriodDao`, `CategoryDao`, `BudgetItemDao` (+211 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **23 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NavGraph()` connect `Community 33` to `Community 13`?**
  _High betweenness centrality (0.091) - this node is a cross-community bridge._
- **Why does `StagingReviewView` connect `Community 0` to `Community 33`, `Community 2`?**
  _High betweenness centrality (0.087) - this node is a cross-community bridge._
- **Why does `Transaction` connect `Community 0` to `Community 2`, `Community 3`, `Community 7`, `Community 73`, `Community 30`?**
  _High betweenness centrality (0.071) - this node is a cross-community bridge._
- **Are the 72 inferred relationships involving `Period` (e.g. with `Account` and `BudgetItem`) actually correct?**
  _`Period` has 72 INFERRED edges - model-reasoned connections that need verification._
- **Are the 72 inferred relationships involving `Category` (e.g. with `Account` and `BudgetItem`) actually correct?**
  _`Category` has 72 INFERRED edges - model-reasoned connections that need verification._
- **Are the 72 inferred relationships involving `Transaction` (e.g. with `Account` and `BudgetItem`) actually correct?**
  _`Transaction` has 72 INFERRED edges - model-reasoned connections that need verification._
- **Are the 72 inferred relationships involving `StagingTransaction` (e.g. with `Account` and `BudgetItem`) actually correct?**
  _`StagingTransaction` has 72 INFERRED edges - model-reasoned connections that need verification._