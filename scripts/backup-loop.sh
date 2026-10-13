#!/bin/sh
set -eu
umask 077
mkdir -p /backups
while true; do
 backup_file="/backups/frota-$(date -u +%Y%m%dT%H%M%SZ).dump"
 if pg_dump --host=db --format=custom --no-owner --no-acl --username="$PGUSER" "$PGDATABASE" > "$backup_file.tmp"; then
   mv "$backup_file.tmp" "$backup_file"
   (cd /backups && sha256sum "${backup_file##*/}") > "$backup_file.sha256"
   find /backups -type f -name 'frota-*.dump*' -mtime "+${BACKUP_RETENTION_DAYS:-14}" -delete
   printf 'Backup concluído em %s\n' "$(date -u +%FT%TZ)"
 else
   rm -f "$backup_file.tmp"
   printf 'Falha no backup; tentativa no próximo intervalo.\n' >&2
 fi
 sleep "${BACKUP_INTERVAL_SECONDS:-86400}"
done
