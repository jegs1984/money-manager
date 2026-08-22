# Money Manager — Product TODO

## Product guardrails

Keep these principles intact in every change:

- **Local first:** no required cloud account or third-party financial-data service.
- **Review before commit:** imported or detected activity is never silently added to the ledger.
- **User control:** duplicate warnings and categorisation suggestions assist the user; they do not make decisions for them.
- **Financial correctness over convenience:** preserve exact amounts, provenance, and an audit trail.

## P0 — Make data safe and the apps buildable

- [x] **Fix budget direction integrity.** Budget lines are distinct by `(period, category, type)` in Django and Android.
- [x] **Prevent overlapping periods.** PostgreSQL range validation and application validation ensure one period per transaction date.
- [x] **Make committed imports traceable and idempotent.** Ledger rows retain an immutable staging source and source fingerprint.
- [x] **Make Django migrations authoritative.** `finance/migrations/` is the schema history; `sql/` is reference-only.
- [x] **Add database integrity rules.** Period validity/activeness and transaction-period integrity are enforced in Django and PostgreSQL.
- [x] **Remove manual primary-key allocation from `Category.save()`.** Category identifiers are database-generated and the existing migration repairs the PostgreSQL sequence.
- [x] **Fix the `Unplanned/Extra` category group.** The helper creates it in `Gastos`.
- [x] **Consolidate Android into one architecture.** A single Room repository, Compose UI, and navigation graph now expose the dashboard and both staging review screens; unused legacy presentation paths were removed.
- [x] **Repair Android notification ingestion.** `BankNotificationParser` is the single pure parser used by the notification listener, staging writer, and JVM tests; notifications create review-only staging rows.
- [x] **Remove Room destructive migrations.** Explicit, tested Room migrations preserve the upgrade path; destructive fallback is prohibited and versioned schemas are exported for review.
- [x] **Restrict local deployments by default.** Docker binds to loopback and production settings require login/secure cookies.
- [x] **Correct `.env.example`.** It uses the Django secret-key and database variable names consumed by settings.

## P1 — Make reconciliation dependable

- [x] **Introduce `ImportBatch`.** Source metadata, hashes, status, and staging associations are stored.
- [x] **Review one batch at a time.** The review views default to the newest batch and accept explicit batch scope.
- [x] **Detect duplicates against each row's target period.** Web and Android find the period by the staged row date.
- [x] **Add idempotent import protection.** Content hashes warn by default, re-import is explicit, and commits retain unique source fingerprints.
- [x] **Preserve signed balances.** Debit parsing separates signed balances from positive debit/credit amounts.
- [x] **Harden XLS handling.** Dependencies are declared, temporary files are cleaned, and parser failures are actionable.
- [x] **Complete or remove unfinished period actions.** Budget duplication has preview/confirmation and rollover is implemented as a service.
- [x] **Add a correction workflow.** Reversals preserve the original transaction and provide an audit trail.
- [x] **Model accounts and transfers.** Accounts and off-budget transfers prevent transfers from inflating income or expense totals.
- [x] **Close and reconcile periods.** Account reconciliation, immutable reversals, and closed-period guards are available.
- [x] **Use installments for forward planning.** Confirmed credit-card installment purchases create immutable future obligations with next due date, remaining installments, and outstanding balance views.

## P1 — Usability and visual design

- [x] **Build responsive navigation.** The desktop sidebar becomes an accessible mobile drawer with compact bottom navigation; content and review controls no longer depend on a fixed sidebar offset.
- [x] **Make the current location unmistakable.** Primary navigation exposes active and `aria-current` states, and staging review shows `Cargar → Validar → Categorizar → Confirmar` progress.
- [x] **Scope staging visually.** Each review now identifies its file and account/card, covered dates and import time, plus total, duplicate, assigned, skipped, and committed rows.
- [x] **Make review fast for real statements.** Staging review provides local search, type/category filters, date/amount/description sorting, row multi-select, and bulk category application without relaxing confirmation.
- [ ] **Retain manual approval while adding suggestions.** Offer a preselected category based on previously confirmed merchant rules; mark it as a suggestion and require review before commit.
- [x] **Improve duplicate decisions.** Duplicate warnings now identify the matching ledger date, category, amount, and imported-source provenance next to the staged row.
- [x] **Use Chilean money formatting consistently.** The shared `clp` template formatter renders whole pesos as `$1.234.567` across templates instead of generic `floatformat` currency output.
- [x] **Use labels, not cryptic abbreviations.** Review and dashboard tables use explicit Spanish headings such as Tipo and Uso del presupuesto, with visible action text.
- [ ] **Do not rely on colour alone.** Pair green/amber/red states with labels and icons; ensure readable contrast in the dark theme.
- [x] **Make row actions always available.** Dashboard and group-table edit/delete controls are always visible, while review controls are persistent buttons with accessible names.
- [x] **Use safer destructive confirmations.** Bulk deletion names the current batch and exact row count and requires typing `ELIMINAR`; individual staged rows remain explicitly removable from the review surface.
- [ ] **Improve empty states.** Explain the next useful action and link directly to it, especially for no period, no budget, no import, and no staged rows.
- [ ] **Localise intentionally.** The product data and audience are Chilean/Spanish but parts of the UI are English. Choose a primary language and make terminology consistent.
- [x] **Remove runtime styling dependencies for offline use.** Tailwind is compiled into a local static stylesheet and the UI uses system fonts; no runtime styling or font CDN is required.
- [ ] **Add accessibility basics.** Visible focus states, semantic buttons/labels, keyboard navigation, screen-reader labels for icons, and responsive table alternatives.
- [ ] **Add merchant rules and bulk categorisation.** Merchant rules now provide transparent suggestions; bulk review actions remain.

## P2 — Reporting and planning

- [x] Add an at-a-glance “needs attention” section: uncategorised rows, unreviewed duplicates, categories near budget, and categories over budget.
- [ ] Make group dashboards expandable and link each group total to its transactions.
- [ ] Add date-range and category filters to transaction history, plus CSV export for user-owned backups. CSV export is available; filters remain.
- [ ] Add a planned-versus-actual trend view across periods without changing the current period-based budgeting model.
- [x] Add an offline, versioned, encrypted export/import bundle if the web and Android apps need to exchange data. Do not make cloud sync a requirement.
- [ ] Add recurring income/expense plans, upcoming-bill reminders, and a daily cash-flow forecast. Recurring plans are available; reminders and forecast remain.
- [x] Add goals and sinking funds for non-monthly spending such as insurance, travel, and emergency savings.

## P2 — Quality and delivery

- [ ] Add Django tests for imports, malformed rows, signed balances, duplicate decisions, batch commits, dashboard totals, constraints, and PDF generation.
- [ ] Add Android tests for statement parsers, notification parsing, Room migrations, duplicate logic, and staging commits.
- [ ] Keep sanitised bank-statement fixtures with expected results; never commit real financial data.
- [ ] Add CI checks for Django migrations, Django tests, Android compilation, Android tests, and schema parity documentation.
- [ ] Document a backup/restore procedure and a recovery process for failed imports.

## Suggested delivery order

1. P0 migration, integrity, Android consolidation, and local-security fixes.
2. Import batches and scoped reconciliation.
3. Responsive review UI and fast categorisation tools.
4. Tests, CI, and backup/export reliability.
5. Planning/reporting refinements and optional offline web-to-mobile transfer.
