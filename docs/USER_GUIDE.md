# User guide

Money Manager is organized around budgeting periods. Transactions are recorded
against budget items, which connect a category to a specific period.

## 1. Set up the budget

1. Create a period with a name, start date, and end date. Dates cannot be
   reversed, and only one period may be active at a time.
2. Create categories in the category view. Categories group income and expenses
   for reporting.
3. Add budget items to the period, choose income or expense, and enter the
   projected amount.

The dashboard compares projected amounts with committed transactions for the
selected period.

## 2. Add transactions

You can add a transaction manually from the transaction workflow or import it
from a bank or credit-card statement. Give every transaction a correct date:
the app validates the date against the associated budget item's period.

## 3. Import a bank statement

1. Open the bank-statement import page and upload the supported statement file.
2. Review the parsed rows in staging. An import batch records the source and
   prevents an identical file from being staged repeatedly.
3. Assign each row to a category/budget item and correct parsing mistakes.
4. Review any duplicate warnings. They are a prompt to inspect the row, not a
   substitute for your judgment.
5. Commit the rows you want. Committed rows appear as transactions and update
   dashboard totals.

Duplicate matching checks the relevant period for each transaction's date, so a
similar transaction in a different period does not block normal historical
imports.

## 4. Import a credit-card statement

The credit-card workflow also stages before committing. Review purchase dates,
installment information, descriptions, amounts, and categories before finalizing
rows. Use the source statement as the authority whenever a parser result looks
wrong.

## 5. Android notification capture

If you enable notification capture in the Android app, Android grants it access
to incoming notification content. The app only parses notifications from its
supported banking-package allowlist, and it stages parsed values for review.
Verify every captured amount and date before relying on it.

## 6. Review and reporting

Use the dashboard to inspect actual versus projected totals. Exports and PDF
reports are snapshots of the records in the selected period; confirm staging
rows are committed before producing a final report.

## Good practices

- Reconcile imports against the original statement before committing.
- Avoid using a generic category when a more precise category will make future
  reporting meaningful.
- Keep `.env`, backups, exported financial data, and Android signing keys in
  private storage.
- Back up before deleting data, rebuilding the Docker database volume, or making
  a substantial upgrade. See [Backup and recovery](BACKUP_AND_RECOVERY.md).
