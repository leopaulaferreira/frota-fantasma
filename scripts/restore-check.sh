#!/usr/bin/env bash
# Restores only into a new disposable container. Never touches the live database.
set -euo pipefail
cd "$(dirname "$0")/.."
backup_file="${1:?Uso: scripts/restore-check.sh backups/arquivo.dump}"
test -s "$backup_file"
if test -f "$backup_file.sha256"; then
 read -r backup_expected_hash backup_line_rest < "$backup_file.sha256"
 read -r backup_actual_hash backup_line_rest < <(sha256sum "$backup_file")
 if [[ "$backup_expected_hash" != "$backup_actual_hash" ]]; then
   printf 'Checksum do backup inválido. Restauração cancelada.\n' >&2
   exit 1
 fi
 printf 'Checksum do backup: OK\n'
fi
restore_container="frota-restore-check-$(date +%s)-$$"
cleanup(){ docker rm -f "$restore_container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run --detach --name "$restore_container" --network none --env POSTGRES_HOST_AUTH_METHOD=trust postgres:17-alpine >/dev/null
for attempt in $(seq 1 30); do
 if docker exec "$restore_container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
 sleep 1
done
docker exec "$restore_container" createdb -U postgres frota_restore
docker exec -i "$restore_container" pg_restore --exit-on-error --no-owner --no-acl -U postgres -d frota_restore < "$backup_file"
docker exec "$restore_container" psql -U postgres -d frota_restore -v ON_ERROR_STOP=1 -c "SELECT 'player' AS tabela,count(*) FROM player UNION ALL SELECT 'war',count(*) FROM war UNION ALL SELECT 'collection_run',count(*) FROM collection_run UNION ALL SELECT 'recommendation_evaluation',count(*) FROM recommendation_evaluation;"
printf 'Restauração validada em banco descartável. Banco local preservado.\n'
