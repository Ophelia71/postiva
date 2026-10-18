#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: bash scripts/restore-drill.sh /absolute/path/to/postiva_*.tar.gz.age" >&2
  exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="${POSTIVA_COMPOSE_FILE:-$PROJECT_DIR/docker-compose.yml}"
BACKUP_FILE="$(cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")"
IDENTITY_FILE="${POSTIVA_BACKUP_AGE_IDENTITY_FILE:-}"

[[ -f "$BACKUP_FILE" ]] || { echo "Backup file not found" >&2; exit 2; }
[[ -r "$IDENTITY_FILE" && -f "$IDENTITY_FILE" ]] || {
  echo "POSTIVA_BACKUP_AGE_IDENTITY_FILE must point to the protected age identity used for restore" >&2
  exit 2
}
[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found" >&2; exit 2; }
for command in age docker sha256sum tar find; do
  command -v "$command" >/dev/null || { echo "Required command not found: $command" >&2; exit 2; }
done

if [[ -z "${POSTIVA_IMAGE_TAG:-}" ]]; then
  RUNNING_IMAGE="$(docker inspect --format '{{.Config.Image}}' postiva-backend 2>/dev/null || true)"
  POSTIVA_IMAGE_TAG="${RUNNING_IMAGE##*:}"
fi
if [[ ! "${POSTIVA_IMAGE_TAG:-}" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "Cannot determine the currently running immutable POSTIVA_IMAGE_TAG" >&2
  exit 2
fi
export POSTIVA_IMAGE_TAG

if [[ -f "$BACKUP_FILE.sha256" ]]; then
  (cd -- "$(dirname -- "$BACKUP_FILE")" && sha256sum --check "$(basename -- "$BACKUP_FILE").sha256")
fi

umask 077
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/postiva-restore-drill.XXXXXXXX")"
STAMP="$(date -u +%Y%m%d%H%M%S)_$$"
DRILL_DB="postiva_restore_drill_$STAMP"
DRILL_DB_CREATED=0
CONTAINER_DUMP="/tmp/$DRILL_DB.dump"

cleanup() {
  if (( DRILL_DB_CREATED == 1 )); then
    docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T postgres \
      sh -c 'dropdb --username="$POSTGRES_USER" "$1"' sh "$DRILL_DB" >/dev/null 2>&1 || true
  fi
  docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T postgres \
    sh -c 'rm -f -- "$1"' sh "$CONTAINER_DUMP" >/dev/null 2>&1 || true
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

age --decrypt --identity "$IDENTITY_FILE" --output "$WORK_DIR/backup.tar.gz" "$BACKUP_FILE"
mkdir -- "$WORK_DIR/unpacked" "$WORK_DIR/media"
tar -xzf "$WORK_DIR/backup.tar.gz" -C "$WORK_DIR/unpacked"
[[ -s "$WORK_DIR/unpacked/database.dump" && -s "$WORK_DIR/unpacked/media.tar.gz" ]] || {
  echo "Backup archive is missing the database dump or media archive" >&2
  exit 1
}
tar -tzf "$WORK_DIR/unpacked/media.tar.gz" >/dev/null
tar -xzf "$WORK_DIR/unpacked/media.tar.gz" -C "$WORK_DIR/media"

docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" cp \
  "$WORK_DIR/unpacked/database.dump" "postgres:$CONTAINER_DUMP"
docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T postgres \
  sh -c 'createdb --username="$POSTGRES_USER" "$1"' sh "$DRILL_DB"
DRILL_DB_CREATED=1
docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T postgres \
  sh -c 'pg_restore --exit-on-error --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$1" "$2"' \
  sh "$DRILL_DB" "$CONTAINER_DUMP"
docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" exec -T postgres \
  sh -c 'psql --username="$POSTGRES_USER" --dbname="$1" --set=ON_ERROR_STOP=1 \
    --command="SELECT COUNT(*) AS users FROM users" \
    --command="SELECT COUNT(*) AS scheduled_posts FROM scheduled_posts" \
    --command="SELECT COUNT(*) AS uploaded_files FROM uploaded_files"' sh "$DRILL_DB"

MEDIA_FILE_COUNT="$(find "$WORK_DIR/media" -type f | wc -l | tr -d '[:space:]')"
echo "Restore drill passed: database $DRILL_DB restored and checked; $MEDIA_FILE_COUNT media files extracted."
echo "The drill database is isolated and will be dropped automatically when this script exits."
