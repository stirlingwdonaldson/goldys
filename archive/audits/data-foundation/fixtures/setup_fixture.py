#!/usr/bin/env python3
"""Provision a throwaway Postgres container with the real schema + synthetic fixtures.

Creates ``goldys-audit-fixture`` (postgres:16), applies every Flyway migration in
version order, then loads ``fixture.sql`` (synthetic data only). After this, the
read-only probe runner can target the container with:

  python3 scripts/run_probes.py --container goldys-audit-fixture

Clean up with:  python3 fixtures/setup_fixture.py --teardown
"""
import argparse
import pathlib
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[3]
MIGRATIONS = REPO / "backend/src/main/resources/db/migration"
CONTAINER = "goldys-audit-fixture"


def sh(*args, check=True, input=None):
    return subprocess.run(list(args), capture_output=True, text=True,
                          input=input, check=check)


def psql(sql: str, db="goldys"):
    return sh("docker", "exec", "-i", CONTAINER, "psql", "-X", "-q",
              "-v", "ON_ERROR_STOP=1", "-U", "goldys", "-d", db,
              input=sql, check=True)


def migrations_in_order():
    files = []
    for p in MIGRATIONS.glob("*.sql"):
        name = p.name
        if name.startswith("V") and "__" in name:
            version = int(name[1:].split("__")[0])
            files.append((version, p))
    return [p for _, p in sorted(files)]


def wait_ready(timeout=90):
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = sh("docker", "exec", CONTAINER, "psql", "-X", "-q",
               "-U", "goldys", "-d", "goldys", "-c", "SELECT 1",
               check=False)
        if r.returncode == 0 and "1" in r.stdout:
            return
        time.sleep(1)
    sys.exit("fixture container did not become ready")


def setup():
    sh("docker", "rm", "-f", CONTAINER, check=False)
    r = sh("docker", "run", "-d", "--name", CONTAINER,
           "-e", "POSTGRES_USER=goldys",
           "-e", "POSTGRES_DB=goldys",
           "-e", "POSTGRES_PASSWORD=goldys-fixture",
           "-p", "55432:5432", "postgres:16", check=False)
    if r.returncode != 0:
        sys.exit(f"docker run failed: {r.stderr}")
    wait_ready()

    files = migrations_in_order()
    print(f"Applying {len(files)} migrations to {CONTAINER} ...")
    for p in files:
        sql = p.read_text()
        r = sh("docker", "exec", "-i", CONTAINER, "psql", "-X", "-q",
               "-v", "ON_ERROR_STOP=1", "-U", "goldys", "-d", "goldys",
               input=sql, check=False)
        if r.returncode != 0:
            sys.exit(f"migration {p.name} failed:\n{r.stderr}")

    # Mirror Flyway's history table so the schema probe matches production shape.
    history = ("CREATE TABLE flyway_schema_history ("
               "installed_rank integer NOT NULL, version varchar(50), "
               "description varchar(200) NOT NULL, type varchar(20) NOT NULL, "
               "script varchar(1000) NOT NULL, checksum integer, "
               "installed_by varchar(100) NOT NULL, "
               "installed_on timestamp NOT NULL DEFAULT now(), "
               "execution_time integer NOT NULL, success boolean NOT NULL);")
    sh("docker", "exec", "-i", CONTAINER, "psql", "-X", "-q",
       "-v", "ON_ERROR_STOP=1", "-U", "goldys", "-d", "goldys",
       input=history, check=True)
    rows = []
    for rank, p in enumerate(files, start=1):
        version = p.name[1:].split("__")[0]
        rows.append(f"({rank},'{version}','{p.stem.split('__')[1]}',"
                    f"'SQL','{p.name}',NULL,'fixture',now(),0,true)")
    sh("docker", "exec", "-i", CONTAINER, "psql", "-X", "-q",
       "-v", "ON_ERROR_STOP=1", "-U", "goldys", "-d", "goldys",
       input="INSERT INTO flyway_schema_history VALUES "
             + ",\n".join(rows) + ";", check=True)

    print("Loading synthetic fixtures ...")
    r = sh("docker", "exec", "-i", CONTAINER, "psql", "-X", "-q",
           "-v", "ON_ERROR_STOP=1", "-U", "goldys", "-d", "goldys",
           input=(HERE / "fixture.sql").read_text(), check=False)
    if r.returncode != 0:
        sys.exit(f"fixture.sql failed:\n{r.stderr}")

    print(f"Fixture ready. Run probes with: "
          f"python3 scripts/run_probes.py --container {CONTAINER}")


def teardown():
    sh("docker", "rm", "-f", CONTAINER, check=False)
    print(f"Removed {CONTAINER}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--teardown", action="store_true")
    args = ap.parse_args()
    teardown() if args.teardown else setup()
