#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
backup_directory="${BACKUP_DIR:-backups}"
mkdir -p "$backup_directory"
chmod 700 "$backup_directory"
backup_file="$backup_directory/frota-$(date -u +%Y%m%dT%H%M%SZ).dump"
backup_temp=$(mktemp "$backup_directory/.backup-XXXXXX")
trap 'rm -f "$backup_temp"' EXIT
docker compose exec -T db sh -c 'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" "$POSTGRES_DB"' > "$backup_temp"
test -s "$backup_temp"
mv "$backup_temp" "$backup_file"
(cd "$backup_directory" && sha256sum "$(basename "$backup_file")") > "$backup_file.sha256"
printf 'Backup criado: %s\n' "$backup_file"
