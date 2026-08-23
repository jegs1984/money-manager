# =============================================================================
# Money Manager — Django Application Image
# =============================================================================
# Multi-stage build:
#   builder  — installs Python deps into an isolated layer
#   runtime  — lean final image with only what's needed to run
# =============================================================================

# ── Stage 1: dependency builder ───────────────────────────────────────────────
FROM python:3.12-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# System libs needed to compile psycopg2 (not the binary wheel)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
 && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --upgrade pip \
 && pip install --prefix=/install --no-cache-dir -r requirements.txt

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    DJANGO_SETTINGS_MODULE=config.settings

# libpq at runtime (psycopg2 needs it even as a binary wheel on some builds)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    curl \
 && rm -rf /var/lib/apt/lists/*

# Copy installed packages from builder stage
COPY --from=builder /install /usr/local

WORKDIR /app

# Copy source (static, templates, sql, manage.py, config, finance)
COPY . .

# Collect static files at build time so the image is self-contained
# The SECRET_KEY here is only used during collectstatic; it is overridden at runtime.
RUN DJANGO_SECRET_KEY=build-dummy-key \
    DB_HOST=skip \
    python manage.py collectstatic --noinput 2>/dev/null || true

EXPOSE 8000

# Default command — overridden in docker-compose for the entrypoint wrapper
CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]
