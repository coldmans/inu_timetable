#!/usr/bin/env python3
"""Export course-centered, privacy-preserving snapshots from the production DB."""

from __future__ import annotations

import argparse
import os
import warnings
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import pandas as pd
import psycopg2


ROOT = Path(__file__).resolve().parents[1]
DAY_ORDER_SQL = "array_position(ARRAY['월','화','수','목','금','토','일'], sc.day_of_week)"
warnings.filterwarnings(
    "ignore",
    message="pandas only supports SQLAlchemy connectable",
    category=UserWarning,
)


def load_dotenv(dotenv_path: Path) -> None:
    if not dotenv_path.exists():
        return
    for raw_line in dotenv_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key, value.strip().strip('"').strip("'"))


def db_connect():
    jdbc_url = os.environ["DB_URL"]
    if jdbc_url.startswith("jdbc:"):
        jdbc_url = jdbc_url[5:]
    parsed = urlparse(jdbc_url)
    query = parse_qs(parsed.query)
    conn = psycopg2.connect(
        host=parsed.hostname,
        port=parsed.port,
        dbname=parsed.path.lstrip("/"),
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        sslmode=query.get("sslmode", ["prefer"])[0],
    )
    conn.set_session(readonly=True, autocommit=True)
    return conn


def table_columns(conn, table_name: str) -> set[str]:
    with conn.cursor() as cursor:
        cursor.execute(
            """
            select column_name
            from information_schema.columns
            where table_schema = 'public' and table_name = %s
            """,
            (table_name,),
        )
        return {row[0] for row in cursor.fetchall()}


def optional_subject_select(columns: set[str]) -> list[str]:
    return [
        "s.course_code" if "course_code" in columns else "NULL::text as course_code",
        "s.semester" if "semester" in columns else "NULL::text as semester",
        "s.active" if "active" in columns else "TRUE as active",
    ]


def optional_subject_group(columns: set[str]) -> list[str]:
    group = []
    if "course_code" in columns:
        group.append("s.course_code")
    if "semester" in columns:
        group.append("s.semester")
    if "active" in columns:
        group.append("s.active")
    return group


def subject_base_select(subject_columns: set[str]) -> str:
    optional = ",\n        ".join(optional_subject_select(subject_columns))
    return f"""
        s.id as subject_id,
        {optional},
        s.subject_name,
        s.professor,
        s.department,
        s.grade,
        s.subject_type,
        s.class_method,
        s.credits,
        s.is_night
    """


def subject_base_group(subject_columns: set[str]) -> str:
    columns = [
        "s.id",
        *optional_subject_group(subject_columns),
        "s.subject_name",
        "s.professor",
        "s.department",
        "s.grade",
        "s.subject_type",
        "s.class_method",
        "s.credits",
        "s.is_night",
    ]
    return ", ".join(columns)


def read_sql(conn, sql: str) -> pd.DataFrame:
    return pd.read_sql_query(sql, conn)


def collect_tables(conn) -> dict[str, pd.DataFrame]:
    subject_columns = table_columns(conn, "subjects")
    wishlist_columns = table_columns(conn, "wishlist_items")
    user_columns = table_columns(conn, "users")

    subject_select = subject_base_select(subject_columns)
    subject_group = subject_base_group(subject_columns)

    required_count = (
        "count(*) filter (where coalesce(is_required, false)) as required_count"
        if "is_required" in wishlist_columns
        else "0::bigint as required_count"
    )
    avg_priority = (
        "avg(priority) as avg_wishlist_priority"
        if "priority" in wishlist_columns
        else "NULL::numeric as avg_wishlist_priority"
    )

    subjects = read_sql(
        conn,
        f"""
        select
            {subject_select},
            count(sc.id) as schedule_count,
            string_agg(
                concat(sc.day_of_week, ' ', sc.start_time::text, '-', sc.end_time::text),
                ', ' order by {DAY_ORDER_SQL}, sc.start_time, sc.end_time
            ) filter (where sc.id is not null) as schedule_text
        from subjects s
        left join schedules sc on sc.subject_id = s.id
        group by {subject_group}
        order by s.id
        """,
    )

    schedules = read_sql(
        conn,
        """
        select
            sc.id as schedule_id,
            sc.subject_id,
            s.subject_name,
            s.professor,
            s.department,
            s.grade,
            s.subject_type,
            sc.day_of_week,
            sc.start_time,
            sc.end_time,
            (sc.end_time - sc.start_time) as duration
        from schedules sc
        join subjects s on s.id = sc.subject_id
        order by sc.subject_id, array_position(ARRAY['월','화','수','목','금','토','일'], sc.day_of_week), sc.start_time, sc.end_time
        """,
    )

    usage = read_sql(
        conn,
        f"""
        with wishlist as (
            select
                subject_id,
                count(*) as wishlist_count,
                count(distinct user_id) as wishlist_user_count,
                {required_count},
                {avg_priority},
                min(added_at) as first_wishlist_at,
                max(added_at) as last_wishlist_at
            from wishlist_items
            group by subject_id
        ),
        timetable as (
            select
                subject_id,
                count(*) as timetable_count,
                count(distinct user_id) as timetable_user_count,
                min(added_at) as first_timetable_at,
                max(added_at) as last_timetable_at
            from user_timetables
            group by subject_id
        ),
        combined_users as (
            select subject_id, user_id from wishlist_items
            union
            select subject_id, user_id from user_timetables
        ),
        combined as (
            select
                subject_id,
                count(distinct user_id) as combined_user_count
            from combined_users
            group by subject_id
        )
        select
            {subject_select},
            coalesce(w.wishlist_count, 0) as wishlist_count,
            coalesce(w.wishlist_user_count, 0) as wishlist_user_count,
            coalesce(w.required_count, 0) as required_count,
            round(w.avg_wishlist_priority::numeric, 2) as avg_wishlist_priority,
            coalesce(t.timetable_count, 0) as timetable_count,
            coalesce(t.timetable_user_count, 0) as timetable_user_count,
            coalesce(c.combined_user_count, 0) as combined_user_count,
            coalesce(w.wishlist_count, 0) + coalesce(t.timetable_count, 0) as total_saved_actions,
            w.first_wishlist_at,
            w.last_wishlist_at,
            t.first_timetable_at,
            t.last_timetable_at
        from subjects s
        left join wishlist w on w.subject_id = s.id
        left join timetable t on t.subject_id = s.id
        left join combined c on c.subject_id = s.id
        order by total_saved_actions desc, combined_user_count desc, s.id
        """,
    )

    department_usage = (
        usage.groupby("department", dropna=False)
        .agg(
            subject_count=("subject_id", "count"),
            wishlist_count=("wishlist_count", "sum"),
            wishlist_user_count=("wishlist_user_count", "sum"),
            timetable_count=("timetable_count", "sum"),
            timetable_user_count=("timetable_user_count", "sum"),
            combined_user_count=("combined_user_count", "sum"),
            total_saved_actions=("total_saved_actions", "sum"),
        )
        .reset_index()
        .sort_values(["total_saved_actions", "subject_count"], ascending=[False, False])
    )

    subject_type_usage = (
        usage.groupby("subject_type", dropna=False)
        .agg(
            subject_count=("subject_id", "count"),
            wishlist_count=("wishlist_count", "sum"),
            wishlist_user_count=("wishlist_user_count", "sum"),
            timetable_count=("timetable_count", "sum"),
            timetable_user_count=("timetable_user_count", "sum"),
            combined_user_count=("combined_user_count", "sum"),
            total_saved_actions=("total_saved_actions", "sum"),
        )
        .reset_index()
        .sort_values(["total_saved_actions", "subject_count"], ascending=[False, False])
    )

    time_slot_usage = read_sql(
        conn,
        """
        with subject_usage as (
            select subject_id, count(*) as wishlist_count, count(distinct user_id) as wishlist_user_count, 0::bigint as timetable_count, 0::bigint as timetable_user_count
            from wishlist_items
            group by subject_id
            union all
            select subject_id, 0::bigint, 0::bigint, count(*), count(distinct user_id)
            from user_timetables
            group by subject_id
        ),
        rolled as (
            select
                subject_id,
                sum(wishlist_count) as wishlist_count,
                sum(wishlist_user_count) as wishlist_user_count,
                sum(timetable_count) as timetable_count,
                sum(timetable_user_count) as timetable_user_count
            from subject_usage
            group by subject_id
        )
        select
            sc.day_of_week,
            sc.start_time,
            sc.end_time,
            count(distinct sc.subject_id) as subject_count,
            coalesce(sum(r.wishlist_count), 0) as wishlist_count,
            coalesce(sum(r.wishlist_user_count), 0) as wishlist_user_count,
            coalesce(sum(r.timetable_count), 0) as timetable_count,
            coalesce(sum(r.timetable_user_count), 0) as timetable_user_count,
            coalesce(sum(r.wishlist_count), 0) + coalesce(sum(r.timetable_count), 0) as total_saved_actions
        from schedules sc
        left join rolled r on r.subject_id = sc.subject_id
        group by sc.day_of_week, sc.start_time, sc.end_time
        order by total_saved_actions desc, subject_count desc, array_position(ARRAY['월','화','수','목','금','토','일'], sc.day_of_week), sc.start_time
        """,
    )

    daily_usage = read_sql(
        conn,
        """
        with wishlist as (
            select added_at::date as action_date, count(*) as wishlist_count, count(distinct user_id) as wishlist_user_count
            from wishlist_items
            group by added_at::date
        ),
        timetable as (
            select added_at::date as action_date, count(*) as timetable_count, count(distinct user_id) as timetable_user_count
            from user_timetables
            group by added_at::date
        )
        select
            coalesce(w.action_date, t.action_date) as action_date,
            coalesce(w.wishlist_count, 0) as wishlist_count,
            coalesce(w.wishlist_user_count, 0) as wishlist_user_count,
            coalesce(t.timetable_count, 0) as timetable_count,
            coalesce(t.timetable_user_count, 0) as timetable_user_count,
            coalesce(w.wishlist_count, 0) + coalesce(t.timetable_count, 0) as total_saved_actions
        from wishlist w
        full outer join timetable t on t.action_date = w.action_date
        order by action_date
        """,
    )

    pair_cooccurrence = read_sql(
        conn,
        """
        with interactions as (
            select 'wishlist' as source, user_id, semester, subject_id from wishlist_items
            union all
            select 'timetable' as source, user_id, semester, subject_id from user_timetables
        ),
        pairs as (
            select
                i.source,
                least(i.subject_id, j.subject_id) as subject_id_a,
                greatest(i.subject_id, j.subject_id) as subject_id_b,
                count(distinct i.user_id) as co_user_count
            from interactions i
            join interactions j
                on j.source = i.source
               and j.user_id = i.user_id
               and coalesce(j.semester, '') = coalesce(i.semester, '')
               and i.subject_id < j.subject_id
            group by i.source, least(i.subject_id, j.subject_id), greatest(i.subject_id, j.subject_id)
            having count(distinct i.user_id) >= 3
        )
        select
            p.source,
            p.subject_id_a,
            sa.subject_name as subject_name_a,
            sa.professor as professor_a,
            p.subject_id_b,
            sb.subject_name as subject_name_b,
            sb.professor as professor_b,
            p.co_user_count
        from pairs p
        join subjects sa on sa.id = p.subject_id_a
        join subjects sb on sb.id = p.subject_id_b
        order by p.co_user_count desc, p.source, p.subject_id_a, p.subject_id_b
        limit 1000
        """,
    )

    audience_tables: dict[str, pd.DataFrame] = {}
    if {"id", "grade"} <= user_columns:
        audience_tables["audience_by_subject_grade"] = read_sql(
            conn,
            """
            with interactions as (
                select subject_id, user_id, 'wishlist' as source from wishlist_items
                union all
                select subject_id, user_id, 'timetable' as source from user_timetables
            )
            select
                i.subject_id,
                s.subject_name,
                s.professor,
                u.grade as user_grade,
                count(*) filter (where i.source = 'wishlist') as wishlist_actions,
                count(*) filter (where i.source = 'timetable') as timetable_actions,
                count(distinct i.user_id) as unique_users
            from interactions i
            join users u on u.id = i.user_id
            join subjects s on s.id = i.subject_id
            group by i.subject_id, s.subject_name, s.professor, u.grade
            having count(distinct i.user_id) >= 3
            order by unique_users desc, i.subject_id, u.grade
            """,
        )

    if {"id", "major"} <= user_columns:
        audience_tables["audience_by_subject_major"] = read_sql(
            conn,
            """
            with interactions as (
                select subject_id, user_id, 'wishlist' as source from wishlist_items
                union all
                select subject_id, user_id, 'timetable' as source from user_timetables
            )
            select
                i.subject_id,
                s.subject_name,
                s.professor,
                nullif(trim(u.major), '') as user_major,
                count(*) filter (where i.source = 'wishlist') as wishlist_actions,
                count(*) filter (where i.source = 'timetable') as timetable_actions,
                count(distinct i.user_id) as unique_users
            from interactions i
            join users u on u.id = i.user_id
            join subjects s on s.id = i.subject_id
            group by i.subject_id, s.subject_name, s.professor, nullif(trim(u.major), '')
            having count(distinct i.user_id) >= 5
            order by unique_users desc, i.subject_id, user_major
            """,
        )

    return {
        "subjects": subjects,
        "schedules": schedules,
        "subject_usage_summary": usage,
        "department_usage_summary": department_usage,
        "subject_type_usage_summary": subject_type_usage,
        "time_slot_usage_summary": time_slot_usage,
        "daily_usage_summary": daily_usage,
        "subject_pair_cooccurrence": pair_cooccurrence,
        **audience_tables,
    }


def write_outputs(tables: dict[str, pd.DataFrame], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, frame in tables.items():
        frame.to_csv(output_dir / f"{name}.csv", index=False, encoding="utf-8-sig")

    workbook_path = output_dir / f"{output_dir.name}.xlsx"
    with pd.ExcelWriter(workbook_path, engine="openpyxl") as writer:
        for name, frame in tables.items():
            sheet_name = name[:31]
            frame.to_excel(writer, sheet_name=sheet_name, index=False)


def write_manifest(conn, tables: dict[str, pd.DataFrame], output_dir: Path) -> None:
    with conn.cursor() as cursor:
        cursor.execute(
            """
            select table_name, column_name
            from information_schema.columns
            where table_schema = 'public'
              and table_name in ('subjects', 'schedules', 'wishlist_items', 'user_timetables', 'users')
            order by table_name, ordinal_position
            """
        )
        schema_rows = cursor.fetchall()
        cursor.execute(
            """
            select 'subjects' as table_name, count(*) from subjects
            union all select 'schedules', count(*) from schedules
            union all select 'wishlist_items', count(*) from wishlist_items
            union all select 'user_timetables', count(*) from user_timetables
            union all select 'users', count(*) from users
            """
        )
        counts = cursor.fetchall()

    top_subjects = tables["subject_usage_summary"].head(10)
    export_time = datetime.now(timezone.utc).isoformat(timespec="seconds")
    lines = [
        f"# Course Data Snapshot ({output_dir.name})",
        "",
        f"- Generated at: `{export_time}`",
        "- Source: production PostgreSQL via read-only session",
        "- Privacy guard: no usernames, passwords, raw user IDs, emails, or per-user event rows exported.",
        "- Note: subject IDs are internal course record IDs, retained only to join exported course tables.",
        "",
        "## Source Counts",
        "",
        "| table | rows |",
        "|---|---:|",
    ]
    for table_name, count in counts:
        lines.append(f"| {table_name} | {count:,} |")

    lines.extend(["", "## Exported Files", ""])
    for name, frame in tables.items():
        lines.append(f"- `{name}.csv`: {len(frame):,} rows")
    lines.append(f"- `{output_dir.name}.xlsx`: same tables bundled into one workbook")

    lines.extend(
        [
            "",
            "## Top Subjects By Saved Actions",
            "",
            "| rank | subject_id | subject | professor | wishlist | timetable | total | users |",
            "|---:|---:|---|---|---:|---:|---:|---:|",
        ]
    )
    for rank, row in enumerate(top_subjects.itertuples(index=False), start=1):
        lines.append(
            "| {rank} | {subject_id} | {subject_name} | {professor} | {wishlist_count} | {timetable_count} | {total_saved_actions} | {combined_user_count} |".format(
                rank=rank,
                subject_id=row.subject_id,
                subject_name=str(row.subject_name).replace("|", "/"),
                professor=str(row.professor).replace("|", "/"),
                wishlist_count=int(row.wishlist_count),
                timetable_count=int(row.timetable_count),
                total_saved_actions=int(row.total_saved_actions),
                combined_user_count=int(row.combined_user_count),
            )
        )

    lines.extend(
        [
            "",
            "## Schema Snapshot",
            "",
            "| table | columns |",
            "|---|---|",
        ]
    )
    safe_schema_columns = {
        "users": {"id", "created_at", "grade", "major"},
        "wishlist_items": {"id", "added_at", "priority", "semester", "subject_id", "is_required"},
        "user_timetables": {"id", "added_at", "memo", "semester", "subject_id"},
    }
    by_table: dict[str, list[str]] = {}
    for table_name, column_name in schema_rows:
        if table_name in safe_schema_columns and column_name not in safe_schema_columns[table_name]:
            continue
        by_table.setdefault(table_name, []).append(column_name)
    for table_name, columns in by_table.items():
        lines.append(f"| {table_name} | `{', '.join(columns)}` |")

    lines.extend(
        [
            "",
            "## Usage Ideas",
            "",
            "- Course recommendation: start from `subject_pair_cooccurrence.csv`.",
            "- Popular course ranking: use `subject_usage_summary.csv` with wishlist/timetable counts.",
            "- Demand by department/type/time: use the grouped summary files.",
            "- UX copy and filter defaults: use top departments, subject types, and busy time slots.",
            "",
            "## Caveats",
            "",
            "- Current production `subjects` may not yet have `course_code`, `semester`, or `active`; missing optional columns are exported as blank/default values.",
            "- Time-slot demand duplicates a subject's demand across each scheduled block; interpret it as demand touching that slot, not unique users per slot.",
            "- Audience distribution files suppress small groups (`>=3` by grade, `>=5` by major) and remain aggregate-only.",
        ]
    )

    (output_dir / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT / "reports" / f"course-data-snapshot-{datetime.now().strftime('%Y-%m-%d')}",
    )
    args = parser.parse_args()

    load_dotenv(ROOT / ".env")
    with db_connect() as conn:
        tables = collect_tables(conn)
        write_outputs(tables, args.output_dir)
        write_manifest(conn, tables, args.output_dir)

    print(f"Wrote course snapshot to {args.output_dir}")


if __name__ == "__main__":
    main()
