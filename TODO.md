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
- [ ] **Use installments for forward planning.** Turn captured credit-card installment data into future-period obligations and remaining-balance views.

## P1 — Usability and visual design

- [ ] **Build responsive navigation.** Replace the permanently fixed desktop sidebar with a collapsible mobile drawer and a compact top/bottom navigation. The staging action bar must not assume `left-56` on small screens.
- [ ] **Make the current location unmistakable.** Add an active navigation state, page breadcrumbs where useful, and clear import/review progress: `Upload → Validate → Categorise → Confirm`.
- [ ] **Scope staging visually.** Show batch name, account/card, covered dates, import time, total rows, duplicates, assigned rows, skipped rows, and committed count at the top of review.
- [ ] **Make review fast for real statements.** Add search, date/type/category filters, sortable columns, pagination/virtualisation, multi-select, and “apply category to selected” actions.
- [ ] **Retain manual approval while adding suggestions.** Offer a preselected category based on previously confirmed merchant rules; mark it as a suggestion and require review before commit.
- [ ] **Improve duplicate decisions.** Show the matching ledger transaction (date, category, source, and amount) next to the staged row, rather than only “Already in ledger.”
- [ ] **Use Chilean money formatting consistently.** Add a shared formatter using `$1.234.567`; do not rely on generic `floatformat` output.
- [ ] **Use labels, not cryptic abbreviations.** Replace table headings such as `T`, `Cap.`, and `—` with readable text or tooltips that work on touch devices.
- [ ] **Do not rely on colour alone.** Pair green/amber/red states with labels and icons; ensure readable contrast in the dark theme.
- [ ] **Make row actions always available.** Hover-only edit/delete controls are inaccessible on touch and awkward with keyboards. Use visible compact actions or an accessible overflow menu.
- [ ] **Use safer destructive confirmations.** “Delete all staged” should name the batch and number of rows, then require an explicit confirmation step; offer undo for individual removals where practical.
- [ ] **Improve empty states.** Explain the next useful action and link directly to it, especially for no period, no budget, no import, and no staged rows.
- [ ] **Localise intentionally.** The product data and audience are Chilean/Spanish but parts of the UI are English. Choose a primary language and make terminology consistent.
- [ ] **Remove runtime styling dependencies for offline use.** Bundle Tailwind output and fonts locally rather than loading them from CDNs, consistent with the local-first promise.
- [ ] **Add accessibility basics.** Visible focus states, semantic buttons/labels, keyboard navigation, screen-reader labels for icons, and responsive table alternatives.
- [ ] **Add merchant rules and bulk categorisation.** Merchant rules now provide transparent suggestions; bulk review actions remain.

## P2 — Reporting and planning

- [ ] Add an at-a-glance “needs attention” section: uncategorised rows, unreviewed duplicates, categories near budget, and categories over budget.
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
