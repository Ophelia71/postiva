#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="${POSTIVA_COMPOSE_FILE:-$PROJECT_DIR/docker-compose.yml}"
BACKUP_ROOT="${POSTIVA_BACKUP_DIR:-/var/backups/postiva}"
AGE_RECIPIENT="${POSTIVA_BACKUP_AGE_RECIPIENT:-}"
RETENTION_DAYS="${POSTIVA_BACKUP_RETENTION_DAYS:-30}"

if [[ "$BACKUP_ROOT" != /* ]]; then
  echo "POSTIVA_BACKUP_DIR must be an absolute path" >&2
  exit 2
fi
if [[ -z "$AGE_RECIPIENT" ]]; then
  echo "POSTIVA_BACKUP_AGE_RECIPIENT is required" >&2
  exit 2
fi
if [[ ! "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || (( RETENTION_DAYS < 1 )); then
  echo "POSTIVA_BACKUP_RETENTION_DAYS must be a positive integer" >&2
  exit 2
fi
for command in age docker sha256sum tar find; do
  command -v "$command" >/dev/null || { echo "Required command not found: $command" >&2; exit 2; }
done
[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found" >&2; exit 2; }

if [[ -z "${POSTIVA_IMAGE_TAG:-}" ]]; then
  RUNNING_IMAGE="$(docker inspect --format '{{.Config.Image}}' postiva-backend 2>/dev/null || true)"
  POSTIVA_IMAGE_TAG="${RUNNING_IMAGE##*:}"
fi
if [[ ! "${POSTIVA_IMAGE_TAG:-}" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "Cannot determine the currently running immutable POSTIVA_IMAGE_TAG" >&2
  exit 2
fi
export POSTIVA_IMAGE_TAG

umask 077
mkdir -p -- "$BACKUP_ROOT"
chmod 700 -- "$BACKUP_ROOT"
STAGING_DIR="$(mktemp -d "$BACKUP_ROOT/.postiva-backup.XXXXXXXX")"
cleanup() { rm -rf -- "$STAGING_DIR"; }
trap cleanup EXIT

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
ARCHIVE_NAME="postiva_${STAMP}.tar.gz.age"

docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T postgres \
  sh -c 'pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --no-owner --no-acl' \
  > "$STAGING_DIR/database.dump"

docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T backend \
  tar -czf - -C /app/uploads . > "$STAGING_DIR/media.tar.gz"

tar -czf "$STAGING_DIR/postiva.tar.gz" -C "$STAGING_DIR" database.dump media.tar.gz
age --encrypt --recipient "$AGE_RECIPIENT" --output "$STAGING_DIR/$ARCHIVE_NAME" "$STAGING_DIR/postiva.tar.gz"
(cd -- "$STAGING_DIR" && sha256sum "$ARCHIVE_NAME" > "$ARCHIVE_NAME.sha256")

mv -- "$STAGING_DIR/$ARCHIVE_NAME" "$BACKUP_ROOT/$ARCHIVE_NAME"
mv -- "$STAGING_DIR/$ARCHIVE_NAME.sha256" "$BACKUP_ROOT/$ARCHIVE_NAME.sha256"
find "$BACKUP_ROOT" -maxdepth 1 -type f -name 'postiva_*.tar.gz.age' -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_ROOT" -maxdepth 1 -type f -name 'postiva_*.tar.gz.age.sha256' -mtime "+$RETENTION_DAYS" -delete

echo "Encrypted database and media backup created: $BACKUP_ROOT/$ARCHIVE_NAME"
