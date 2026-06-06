# Money Manager — Mac Installer

Self-contained installer for macOS. Sets up all dependencies, the database, and a
double-clickable `.app` to launch the server and open the browser in one step.

---

## Prerequisites (installed automatically if missing)

| Tool | How |
|---|---|
| Homebrew | Downloaded from brew.sh if absent |
| Python 3.11+ | `brew install python` |
| PostgreSQL 16 | `brew install postgresql@16` |
| LibreOffice | `brew install --cask libreoffice` |

---

## Install

```bash
cd installer
bash setup.sh
```

That's it. The script will:

1. Install Homebrew (if needed)
2. Install Python 3, PostgreSQL, LibreOffice via Homebrew
3. Create a Python virtualenv inside the project
4. Install Python dependencies
5. Create the `money_manager` Postgres database and user
6. Run `init_db.sql` + `categories.sql` to seed the schema
7. Write a `.env` file with generated secret key
8. Create `Money Manager.app` on your Desktop

---

## Launch

Double-click **Money Manager** on your Desktop.

It will:
- Start PostgreSQL (if not running)
- Start the Django dev server on `http://127.0.0.1:8765`
- Open your browser automatically

To stop the server press **Ctrl-C** in the terminal window that opens,
or quit the app from the menu bar.

---

## Uninstall

```bash
bash installer/uninstall.sh
```

Removes the virtualenv, `.env`, and the Desktop app.
Does **not** drop the database or uninstall Homebrew packages.
