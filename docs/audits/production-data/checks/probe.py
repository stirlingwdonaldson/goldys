#!/usr/bin/env python3
"""Operator-safe SELECT-only audit runner. No credentials leave the DB container.

Usage: python3 probe.py checks/schema.sql > evidence/schema.csv
Requires local Docker access. Uses a read-only transaction and bounded execution.
The connection role is the container's configured role, not a least-privilege role.
Only run reviewed SQL; this wrapper is not a security boundary for arbitrary SQL.
"""
import pathlib
import subprocess
import sys

sql = pathlib.Path(sys.argv[1]).read_text()
command = [
    "docker", "exec", "-i", "goldys-prod-postgres-1", "sh", "-c",
    'PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=8000 '
    '-c lock_timeout=1000 -c idle_in_transaction_session_timeout=15000" '
    'psql -X -q --csv -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
]
result = subprocess.run(command, input="BEGIN READ ONLY;\n" + sql + "\nROLLBACK;\n", text=True)
sys.exit(result.returncode)
