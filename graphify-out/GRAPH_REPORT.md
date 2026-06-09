# Graph Report - /Users/juan/github-labs/money-manager  (2026-06-08)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 917 nodes · 1925 edges · 68 communities (57 shown, 11 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 265 edges (avg confidence: 0.51)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9b5793dd`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
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

## God Nodes (most connected - your core abstractions)
1. `BudgetItem` - 51 edges
2. `Transaction` - 49 edges
3. `Period` - 38 edges
4. `Category` - 38 edges
5. `FinanceRepository` - 28 edges
6. `BudgetItemForm` - 27 edges
7. `TransactionForm` - 27 edges
8. `Deprecated` - 26 edges
9. `PeriodForm` - 26 edges
10. `CategoryForm` - 26 edges

## Surprising Connections (you probably didn't know these)
- `BudgetItem` --references--> `Category`  [EXTRACTED]
  finance/services.py → README.md
- `BudgetItem` --references--> `Period`  [EXTRACTED]
  finance/services.py → README.md
- `StagingReviewView` --implements--> `Base Template`  [EXTRACTED]
  finance/views.py → templates/finance/base.html
- `_get_or_create_budget_item()` --references--> `BudgetItemEntity`  [EXTRACTED]
  finance/services.py → android/app/src/main/java/com/moneymanager/data/repository/FinanceRepository.kt
- `_get_or_create_budget_item()` --references--> `String`  [EXTRACTED]
  finance/services.py → android/app/src/main/java/com/moneymanager/data/repository/FinanceRepository.kt

## Import Cycles
- 1-file cycle: `finance/services.py -> finance/services.py`

## Communities (68 total, 11 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.13
Nodes (64): BudgetItemWithStats, BankNotificationService, BudgetItem, Category, CreateView, date, Decimal, DeleteView (+56 more)

### Community 1 - "Community 1"
Cohesion: 0.09
Nodes (39): AbstractExternalDependencyFactory, CapabilityNotationParser, DefaultVersionCatalog, ImmutableAttributesFactory, Inject, MinimalExternalModuleDependency, ObjectFactory, PluginDependency (+31 more)

### Community 2 - "Community 2"
Cohesion: 0.08
Nodes (39): CapabilityNotationParser, DefaultVersionCatalog, ImmutableAttributesFactory, Inject, MinimalExternalModuleDependency, ObjectFactory, PluginDependency, Provider (+31 more)

### Community 3 - "Community 3"
Cohesion: 0.07
Nodes (29): Flow, Int, List, Long, TransactionEntity, BigDecimal, Boolean, BudgetItemEntity (+21 more)

### Community 4 - "Community 4"
Cohesion: 0.05
Nodes (39): 1. Generate a Signing Keystore (One-time), 2. Configure Build Credentials, 3. Build the APK, Money Manager Android App, Architecture, Building & Deploying, Core Feature: Auto-Logging via Notifications, Database & Storage (+31 more)

### Community 5 - "Community 5"
Cohesion: 0.08
Nodes (16): BudgetItemEntity, Flow, List, Long, Flow, List, Long, PeriodEntity (+8 more)

### Community 6 - "Community 6"
Cohesion: 0.06
Nodes (28): Long, CategoryEntity, List, Long, NavController, NumberFormat, Long, StateFlow (+20 more)

### Community 7 - "Community 7"
Cohesion: 0.13
Nodes (12): Flow, List, LocalDate, Long, String, BudgetItemDao, CategoryDao, PeriodDao (+4 more)

### Community 8 - "Community 8"
Cohesion: 0.08
Nodes (23): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+15 more)

### Community 9 - "Community 9"
Cohesion: 0.09
Nodes (20): Boolean, List, Long, NavController, String, BudgetItemWithStats, Long, NavController (+12 more)

### Community 10 - "Community 10"
Cohesion: 0.22
Nodes (21): create_database(), create_env_file(), create_venv(), die(), django_setup(), info(), install_deps(), load_env() (+13 more)

### Community 11 - "Community 11"
Cohesion: 0.15
Nodes (17): BigDecimal, List, Modifier, PeriodEntity, String, Long, StateFlow, androidx (+9 more)

### Community 12 - "Community 12"
Cohesion: 0.18
Nodes (7): Double, Flow, String, String, RawBankNotification, RawBankNotification, ParsedTransaction

### Community 13 - "Community 13"
Cohesion: 0.18
Nodes (17): BigDecimal, Boolean, CategoryEntity, DupAction, Int, List, Long, Modifier (+9 more)

### Community 14 - "Community 14"
Cohesion: 0.23
Nodes (12): BigDecimal, InputStream, List, LocalDate, String, _parse_cc_header(), _parse_header(), Map (+4 more)

### Community 15 - "Community 15"
Cohesion: 0.22
Nodes (6): CategoryEntity, Flow, Int, List, Long, CategoryDao

### Community 16 - "Community 16"
Cohesion: 0.18
Nodes (14): BudgetItemWithStats, DashboardViewModel, List, Modifier, NavController, NumberFormat, String, Color (+6 more)

### Community 17 - "Community 17"
Cohesion: 0.22
Nodes (11): NavController, android, String, Uri, ImportCCViewModel, Error, Idle, ImportCCState (+3 more)

### Community 18 - "Community 18"
Cohesion: 0.22
Nodes (11): NavController, android, String, Uri, ImportDatViewModel, Error, Idle, ImportDatState (+3 more)

### Community 19 - "Community 19"
Cohesion: 0.20
Nodes (9): Long, NavController, List, Long, StateFlow, NavController, BudgetItemOption, TransactionFormViewModel (+1 more)

### Community 20 - "Community 20"
Cohesion: 0.18
Nodes (7): java, Long, StateFlow, String, DupAction, StagingUiState, StagingViewModel

### Community 21 - "Community 21"
Cohesion: 0.29
Nodes (5): Flow, List, Long, StagingCCTransactionEntity, StagingCCTransactionDao

### Community 22 - "Community 22"
Cohesion: 0.29
Nodes (5): Flow, List, Long, StagingTransactionEntity, StagingTransactionDao

### Community 23 - "Community 23"
Cohesion: 0.31
Nodes (7): Double, InputStream, Int, String, _parse_clp_amount(), org, ParseResult

### Community 24 - "Community 24"
Cohesion: 0.22
Nodes (8): Boolean, StateFlow, NotificationDashboardScreen(), NotificationPermissionScreen(), NotificationUiState, NotificationViewModel, NotificationViewModel, ParsedTransaction

### Community 25 - "Community 25"
Cohesion: 0.20
Nodes (6): DupAction, java, Long, StateFlow, String, StagingViewModel

### Community 26 - "Community 26"
Cohesion: 0.31
Nodes (7): BigDecimal, List, Long, Set, StagingCCTransactionEntity, StagingTransactionEntity, toBigDecimal()

### Community 27 - "Community 27"
Cohesion: 0.31
Nodes (4): BigDecimal, String, NotificationListenerService, StatusBarNotification

### Community 28 - "Community 28"
Cohesion: 0.20
Nodes (7): NavController, CategoryEntity, TransactionEntity, CategoriesViewModel, CategoriesViewModel, TransactionsViewModel, ViewModel

### Community 29 - "Community 29"
Cohesion: 0.27
Nodes (8): Install, Launch, Money Manager — Mac Installer, Prerequisites (installed automatically if missing), info(), success(), warn(), uninstall.sh script

### Community 30 - "Community 30"
Cohesion: 0.36
Nodes (6): Double, InputStream, String, _parse_amount(), DatParser, ParseResult

### Community 31 - "Community 31"
Cohesion: 0.29
Nodes (5): Long, NavController, Long, CategoryFormViewModel, CategoryFormViewModel

### Community 32 - "Community 32"
Cohesion: 0.29
Nodes (5): Long, NavController, Long, PeriodFormViewModel, PeriodFormViewModel

### Community 33 - "Community 33"
Cohesion: 0.25
Nodes (5): Bundle, ComponentActivity, NavHostController, NavGraph(), MoneyManagerTheme()

### Community 34 - "Community 34"
Cohesion: 0.25
Nodes (7): graphify reference: extra exports and benchmark, Step 6b - Wiki (only if --wiki flag), Step 7 - Neo4j export (only if --neo4j or --neo4j-push flag), Step 7b - SVG export (only if --svg flag), Step 7c - GraphML export (only if --graphml flag), Step 7d - MCP server (only if --mcp flag), Step 8 - Token reduction benchmark (only if total_words > 5000)

### Community 35 - "Community 35"
Cohesion: 0.48
Nodes (4): PeriodEntity, StateFlow, DashboardUiState, DashboardViewModel

### Community 36 - "Community 36"
Cohesion: 0.29
Nodes (5): BudgetItemEntity, CategoryEntity, PeriodEntity, StagingCCTransactionEntity, TransactionEntity

### Community 37 - "Community 37"
Cohesion: 0.29
Nodes (6): Agent Directives (Caveman Mode), Agent Roles & Delegation, money-manager workspace instructions, Project Context, Repository Structure, Technology Stack

### Community 38 - "Community 38"
Cohesion: 0.29
Nodes (6): Agent Directives (Caveman Mode), Agent Roles & Delegation, money-manager workspace instructions, Project Context, Repository Structure, Technology Stack

### Community 39 - "Community 39"
Cohesion: 0.48
Nodes (6): die(), info(), PATH, success(), warn(), setup.sh script

### Community 40 - "Community 40"
Cohesion: 0.33
Nodes (4): NavController, PeriodEntity, PeriodsViewModel, PeriodsViewModel

### Community 41 - "Community 41"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 44 - "Community 44"
Cohesion: 0.60
Nodes (4): die(), info(), PATH, run.sh script

### Community 45 - "Community 45"
Cohesion: 0.70
Nodes (4): die(), info(), success(), update.sh script

### Community 47 - "Community 47"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 48 - "Community 48"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 49 - "Community 49"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

## Knowledge Gaps
- **223 isolated node(s):** `PreToolUse`, `Inject`, `Inject`, `Bundle`, `PeriodEntity` (+218 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `BudgetItem` connect `Community 0` to `Community 46`, `Community 9`, `Community 11`, `Community 6`?**
  _High betweenness centrality (0.284) - this node is a cross-community bridge._
- **Why does `_parse_header()` connect `Community 14` to `Community 0`?**
  _High betweenness centrality (0.220) - this node is a cross-community bridge._
- **Why does `Map` connect `Community 14` to `Community 1`, `Community 2`?**
  _High betweenness centrality (0.212) - this node is a cross-community bridge._
- **Are the 35 inferred relationships involving `BudgetItem` (e.g. with `StagingTransactionDao.kt` and `Category`) actually correct?**
  _`BudgetItem` has 35 INFERRED edges - model-reasoned connections that need verification._
- **Are the 35 inferred relationships involving `Transaction` (e.g. with `BudgetItem` and `Category`) actually correct?**
  _`Transaction` has 35 INFERRED edges - model-reasoned connections that need verification._
- **Are the 33 inferred relationships involving `Period` (e.g. with `BudgetItem` and `Category`) actually correct?**
  _`Period` has 33 INFERRED edges - model-reasoned connections that need verification._
- **Are the 33 inferred relationships involving `Category` (e.g. with `BudgetItem` and `Category`) actually correct?**
  _`Category` has 33 INFERRED edges - model-reasoned connections that need verification._