from __future__ import annotations

import argparse
import base64
import json
import subprocess
from pathlib import Path
from typing import Any


MAX_POINTS = 400
MYSQL_FIELDS = (
    ("model_training_jobs", "metrics_json"),
    ("model_versions", "metrics_json"),
    ("model_evaluations", "metrics_json"),
    ("model_feedback", "metrics_snapshot_json"),
)


def sampled(values: list[Any], max_points: int) -> list[Any]:
    if len(values) <= max_points:
        return values
    result: list[Any] = []
    previous = -1
    for index in range(max_points):
        source_index = round(index * (len(values) - 1) / (max_points - 1))
        if source_index != previous:
            result.append(values[source_index])
            previous = source_index
    return result


def compact_metrics(metrics: Any, max_points: int) -> tuple[Any, bool]:
    if not isinstance(metrics, dict):
        return metrics, False
    changed = False
    compacted = dict(metrics)
    for key in ("rocCurve", "prCurve"):
        value = compacted.get(key)
        if isinstance(value, list) and len(value) > max_points:
            compacted[key] = sampled(value, max_points)
            changed = True
    return compacted, changed


def compact_metadata_file(path: Path, max_points: int, apply: bool) -> bool:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"skip metadata {path}: {exc}")
        return False
    metrics, changed = compact_metrics(payload.get("metrics"), max_points)
    if not changed:
        return False
    payload["metrics"] = metrics
    if apply:
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(path)
    print(f"{'updated' if apply else 'would update'} metadata {path}")
    return True


def compact_json_text(text: str, max_points: int) -> tuple[str, bool]:
    try:
        payload = json.loads(text)
    except Exception:
        return text, False
    compacted, changed = compact_metrics(payload, max_points)
    if not changed:
        return text, False
    return json.dumps(compacted, ensure_ascii=False, separators=(",", ":")), True


def mysql_query(table: str, column: str) -> str:
    return (
        f"SELECT id, REPLACE(TO_BASE64({column}), '\\n', '') "
        f"FROM {table} "
        f"WHERE {column} IS NOT NULL "
        f"AND ({column} LIKE '%rocCurve%' OR {column} LIKE '%prCurve%')"
    )


def run_mysql(sql: str, capture: bool = False) -> str:
    command = [
        "docker",
        "compose",
        "exec",
        "-T",
        "mysql",
        "sh",
        "-lc",
        'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" '
        "--batch --raw --skip-column-names --max_allowed_packet=512M",
    ]
    if not sql.rstrip().endswith(";"):
        sql = sql.rstrip() + ";\n"
    completed = subprocess.run(
        command,
        input=sql,
        text=True,
        capture_output=True,
    )
    if completed.returncode != 0:
        if completed.stderr:
            print(completed.stderr.strip())
        raise subprocess.CalledProcessError(completed.returncode, command, completed.stdout, completed.stderr)
    return completed.stdout if capture else ""


def compact_mysql(max_points: int, apply: bool) -> int:
    updates: list[str] = []
    changed_rows = 0
    for table, column in MYSQL_FIELDS:
        rows = run_mysql(mysql_query(table, column), capture=True)
        for line in rows.splitlines():
            if not line.strip():
                continue
            row_id, encoded = line.split("\t", 1)
            raw = base64.b64decode(encoded).decode("utf-8")
            compacted, changed = compact_json_text(raw, max_points)
            if not changed:
                continue
            changed_rows += 1
            encoded_compacted = base64.b64encode(compacted.encode("utf-8")).decode("ascii")
            updates.append(
                f"UPDATE {table} SET {column}=CONVERT(FROM_BASE64('{encoded_compacted}') USING utf8mb4) "
                f"WHERE id={int(row_id)};"
            )
            print(f"{'updated' if apply else 'would update'} mysql {table}.{column} id={row_id}")
    if apply and updates:
        run_mysql("START TRANSACTION;\n" + "\n".join(updates) + "\nCOMMIT;\n")
    return changed_rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Compact large MedRisk training metric curves.")
    parser.add_argument("--root", default=".", help="MedRisk project root")
    parser.add_argument("--max-points", type=int, default=MAX_POINTS)
    parser.add_argument("--apply", action="store_true", help="write changes; default is dry-run")
    parser.add_argument("--mysql", action="store_true", help="also compact MySQL metric JSON columns via docker compose")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    metadata_changed = 0
    for path in sorted((root / "models" / "training").glob("**/metadata.json")):
        if compact_metadata_file(path, args.max_points, args.apply):
            metadata_changed += 1
    mysql_changed = compact_mysql(args.max_points, args.apply) if args.mysql else 0
    print(json.dumps({
        "apply": args.apply,
        "metadataChanged": metadata_changed,
        "mysqlChanged": mysql_changed,
        "maxPoints": args.max_points,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
