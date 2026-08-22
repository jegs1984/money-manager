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

## 7. Accounts, corrections, and reconciliation

Use **Accounts** to record checking, savings, cash, and credit-card accounts.
Transfers are deliberately separate from budget transactions, so moving money
between your own accounts does not inflate income or spending. Reconcile an
account against its statement balance to record the difference between the
statement and the ledger's calculated balance.

Do not delete a transaction merely to correct it. Use **reverse** in the
transaction list to create an equal and opposite ledger entry while preserving
the original record. Close a period only after all of its staging rows are
reviewed; closed periods reject new ledger activity.

## 8. Planning and automation

Merchant rules suggest a category when the description contains a saved,
user-defined pattern. Suggestions remain editable and are never committed
without review. Recurring plans create due entries once using a stable source
fingerprint. Goals track progress toward a target without changing budget
totals.

## 9. Encrypted exchange and CSV

The **Encrypted backup** page creates a versioned `.mmbundle` file encrypted
with AES-GCM and a passphrase of your choice. Keep the passphrase separately:
it cannot be recovered. Importing the same bundle again does not duplicate its
transactions. The transaction list also provides CSV export for spreadsheet
analysis or user-owned backups. Use its text, category, and date filters to
audit a merchant, budget category, or statement period before exporting.

## Good practices

- Reconcile imports against the original statement before committing.
- Avoid using a generic category when a more precise category will make future
  reporting meaningful.
- Keep `.env`, backups, exported financial data, and Android signing keys in
  private storage.
- Back up before deleting data, rebuilding the Docker database volume, or making
  a substantial upgrade. See [Backup and recovery](BACKUP_AND_RECOVERY.md).
# Cuotas de tarjeta

Al confirmar una compra de tarjeta en cuotas, Money Manager conserva la compra original y crea una obligación futura. Revísala en **Cuotas pendientes** para ver la siguiente fecha, número de cuotas restantes y saldo por pagar. La obligación es una ayuda de planificación: no agrega movimientos al libro sin revisión.

# Moneda

Todos los importes se muestran como pesos chilenos sin decimales, por ejemplo `$1.234.567`. Los valores exactos se conservan internamente para los cálculos y las importaciones.

# Navegación

En computadores se muestra la barra lateral. En teléfonos, usa el botón de menú para abrirla o la barra inferior para ir a Inicio, Importar y Revisar. La revisión muestra siempre el paso actual de la importación.

# Alcance de una importación

La cabecera de revisión identifica el lote que estás viendo: archivo, cuenta o tarjeta, fechas cubiertas, hora de carga y contadores de filas. Revisa esos datos antes de confirmar para no mezclar movimientos de dos cartolas.

# Revisión rápida

Filtra la cartola por texto, tipo o categoría. Puedes seleccionar varias filas y aplicarles la categoría elegida; cada fila sigue visible y editable antes de confirmar el lote.

Para eliminar todo un lote, la aplicación muestra su nombre y el número de filas y pide escribir `ELIMINAR`. Es una acción permanente: usa las decisiones por fila cuando quieras conservar el resto del lote.
