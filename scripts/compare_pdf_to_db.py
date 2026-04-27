#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional
from urllib.parse import parse_qs, urlparse

import psycopg2


HEADER_PATTERNS = [
    "수강신청편람",
    "학년",
    "학수번호",
    "교과목명",
    "교수명",
    "요일 및 교시",
    "구분",
    "수업방법",
    "비 고",
    "학문의기초",
]

ROW_PATTERN = re.compile(
    r"^\s*(\d)\s+(전기|전핵|전심|기교|일선|심교|핵교)\s+(\d{10})\s+(.*)$"
)

MANUAL_DEPARTMENT_MAP = {
    "국문": "국어국문학과",
    "영문": "영어영문학과",
    "독문": "독어독문학과",
    "불문": "불어불문학과",
    "일문": "일어일문학과",
    "중국": "중국학과",
    "일본지역문화": "일본지역문화학과",
    "행정": "행정학과",
    "정외": "정치외교학과",
    "경제": "경제학과",
    "무역": "무역학부",
    "소비자": "소비자학과",
    "법학부": "법학부",
    "국어교육": "국어교육과",
    "영어교육": "영어교육과",
    "일어교육": "일어교육과",
    "수학교육": "수학교육과",
    "체육교육": "체육교육과",
    "유아교육": "유아교육과",
    "역사교육": "역사교육과",
    "윤리교육": "윤리교육과",
    "사회복지": "사회복지학과",
    "수학": "수학과",
    "물리": "물리학과",
    "화학": "화학과",
    "패션": "패션산업학과",
    "패션산업": "패션산업학과",
    "해양": "해양학과",
    "해양학": "해양학과",
    "기계": "기계공학과",
    "전기": "전기공학과",
    "전자": "전자공학과",
    "산공": "산업경영공학과",
    "산경": "산업경영공학과",
    "신소재": "신소재공학과",
    "안전": "안전공학과",
    "에너지화학": "에너지화학공학과",
    "컴공": "컴퓨터공학부",
    "컴퓨터": "컴퓨터공학부",
    "임베": "임베디드시스템공학과",
    "임베디드": "임베디드시스템공학과",
    "정보통신": "정보통신공학과",
    "정통": "정보통신공학과",
    "경영": "경영학부",
    "세무": "세무회계학과",
    "세무회계": "세무회계학과",
    "조형예술": "조형예술학부",
    "디자인": "디자인학부",
    "공연예술": "공연예술학과",
    "스포츠과학": "스포츠과학부",
    "운동건강": "운동건강학부",
    "도시행정": "도시행정학과",
    "도시공학": "도시공학과",
    "도시건축": "도시건축학부",
    "도시환경": "도시환경공학부",
    "동북아": "동북아국제통상학부",
    "문헌정보": "문헌정보학과",
    "미디어커뮤니케이션": "미디어커뮤니케이션학과",
    "창의인재개발": "창의인재개발학과",
    "바이오-로봇": "바이오-로봇시스템공학과",
    "건설환경": "건설환경공학과",
    "환경": "환경공학과",
    "건축공학": "건축공학부",
    "생명과학": "생명과학부",
    "분자의생명": "분자의생명과학과",
    "생명공학": "생명공학부",
    "나노바이오공학": "나노바이오공학전공",
    "Global Trade & Service": "Global Trade & Service전공",
    "스마트물류": "스마트물류공학전공",
    "IBE": "IBE전공",
    "데이터과학": "데이터과학과",
    "자유전공학부": "자유전공학부",
}


@dataclass(frozen=True)
class ParsedEntry:
    subject_name: str
    professor: str
    department: str
    grade: int
    subject_type: str
    course_code: str
    time_string: str


def load_dotenv(dotenv_path: Path) -> None:
    if not dotenv_path.exists():
        return

    for line in dotenv_path.read_text(encoding="utf-8").splitlines():
        if "=" not in line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key, value)


def db_connect():
    jdbc_url = os.environ["DB_URL"]
    if jdbc_url.startswith("jdbc:"):
        jdbc_url = jdbc_url[5:]

    parsed = urlparse(jdbc_url)
    query = parse_qs(parsed.query)

    return psycopg2.connect(
        host=parsed.hostname,
        port=parsed.port,
        dbname=parsed.path.lstrip("/"),
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        sslmode=query.get("sslmode", ["prefer"])[0],
    )


def extract_pdf_text(pdf_path: Path) -> list[str]:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".txt") as temp_output:
        temp_path = Path(temp_output.name)

    try:
        subprocess.run(
            ["pdftotext", "-layout", str(pdf_path), str(temp_path)],
            check=True,
            capture_output=True,
            text=True,
        )
        return temp_path.read_text(encoding="utf-8").splitlines()
    finally:
        temp_path.unlink(missing_ok=True)


def generate_abbreviation(department: str) -> str:
    abbreviation = (
        department.replace("학과", "").replace("학부", "").replace("전공", "")
    )
    if "어" in abbreviation and "문" in abbreviation:
        index = abbreviation.find("문")
        if index > 1:
            abbreviation = abbreviation[:2] + "문"
    return abbreviation


def build_department_map(db_departments: Iterable[str]) -> dict[str, str]:
    mapping = dict(MANUAL_DEPARTMENT_MAP)
    candidates: dict[str, set[str]] = defaultdict(set)

    for department in db_departments:
        if not department or department == "미분류":
            continue
        candidates[generate_abbreviation(department)].add(department)

    for abbreviation, values in candidates.items():
        if len(values) == 1:
            mapping.setdefault(abbreviation, next(iter(values)))

    return mapping


def normalize_text(value: Optional[str]) -> str:
    return re.sub(r"\s+", "", (value or "").strip())


def full_key(
    subject_name: str, professor: str, department: str, grade: int, subject_type: str
) -> str:
    return (
        f"{normalize_text(subject_name)}|{normalize_text(professor)}|"
        f"{normalize_text(department)}|{grade}|{subject_type}"
    )


def relaxed_key(subject_name: str, professor: str, grade: int, subject_type: str) -> str:
    return f"{normalize_text(subject_name)}|{normalize_text(professor)}|{grade}|{subject_type}"


def parse_schedule_key(day: str, start_time: float, end_time: float) -> tuple[str, float, float]:
    return day, float(start_time), float(end_time)


def convert_to_time(period: str) -> float:
    if period.startswith("야"):
        return {"1": 10.0, "2": 11.0, "3": 12.0}.get(period[1:], 10.0)

    numeric = re.sub(r"[^0-9]", "", period)
    if not numeric:
        return 0.0

    time = float(numeric)
    if "B" in period:
        return time + 0.5
    return time


def convert_to_time_end(period: str, start_period: str) -> float:
    if period.startswith("야"):
        return {"1": 11.0, "2": 12.0, "3": 13.0}.get(period[1:], 11.0)

    if start_period.startswith("야"):
        numeric = re.sub(r"[^0-9]", "", period)
        if not numeric:
            return 11.0
        night = int(numeric)
        return (9 + night) + (0.5 if "A" in period else 1.0)

    numeric = re.sub(r"[^0-9]", "", period)
    if not numeric:
        return 0.0

    time = float(numeric)
    if "A" in period:
        return time + 0.5
    return time + 1.0


def parse_time_string(time_string: str) -> list[tuple[str, float, float]]:
    schedules: list[tuple[str, float, float]] = []
    if not time_string or not time_string.strip():
        return schedules

    clean_time_string = re.sub(r"\([^)]*\)", "", time_string).strip()
    day_pattern = re.compile(r"([월화수목금토일])\s+([^월화수목금토일]+)")

    for day_match in day_pattern.finditer(clean_time_string):
        day_of_week = day_match.group(1)
        time_slots = day_match.group(2).strip()

        min_start_time = float("inf")
        max_end_time = float("-inf")
        has_range = False

        range_pattern = re.compile(
            r"((?:야)?[1-9][0-9]?[AB]?)-((?:야)?[1-9][0-9]?[AB]?)"
        )
        for range_match in range_pattern.finditer(time_slots):
            start_period = range_match.group(1)
            end_period = range_match.group(2)
            start = convert_to_time(start_period)
            end = convert_to_time_end(end_period, start_period)
            min_start_time = min(min_start_time, start)
            max_end_time = max(max_end_time, end)
            has_range = True

        if not has_range:
            time_pattern = re.compile(r"(야[1-3]|[1-9][0-9]?[AB]?)")
            times = [convert_to_time(match.group(1)) for match in time_pattern.finditer(time_slots)]
            if times:
                min_start_time = times[0]
                max_end_time = times[-1] + 1.0

        if min_start_time != float("inf"):
            if min_start_time > max_end_time:
                max_end_time += 8.0
            schedules.append((day_of_week, min_start_time, max_end_time))

    return sorted(schedules)


def looks_like_fragment(line: str) -> bool:
    if not line:
        return False
    if any(pattern in line for pattern in HEADER_PATTERNS):
        return False
    if re.search(r"\d{4}학년도|\d{10}|\([^)]*\)|,", line):
        return False
    return True


def parse_departments(raw_department: str, department_map: dict[str, str]) -> list[str]:
    departments: list[str] = []
    for token in re.split(r"[,，]+", raw_department.strip()):
        cleaned = token.strip().replace("(야)", "")
        if not cleaned:
            continue
        departments.append(department_map.get(cleaned, cleaned))
    return departments


def parse_pdf_rows(
    lines: list[str], department_map: dict[str, str]
) -> tuple[list[ParsedEntry], list[dict[str, object]], int]:
    pending_fragments: list[str] = []
    expanded_entries: list[ParsedEntry] = []
    skipped_rows: list[dict[str, object]] = []
    raw_row_count = 0
    index = 0

    while index < len(lines):
        line = lines[index].rstrip("\n")
        stripped = line.strip()
        match = ROW_PATTERN.match(line)

        if match:
            raw_row_count += 1
            grade = int(match.group(1))
            subject_type = match.group(2)
            course_code = match.group(3)
            rest = match.group(4).strip()
            parts = [part.strip() for part in re.split(r"\s{2,}", rest) if part.strip()]

            subject_name = credits = professor = time_string = raw_department = None

            if len(parts) == 5:
                subject_name, credits, professor, time_string, raw_department = parts
            elif len(parts) >= 6:
                subject_name, credits, professor, time_string, raw_department = parts[:5]
            elif len(parts) == 4 and pending_fragments:
                subject_name = "".join(
                    fragment for fragment in pending_fragments if fragment != "실제"
                )
                credits, professor, time_string, raw_department = parts

                lookahead = index + 1
                while lookahead < len(lines):
                    next_line = lines[lookahead].strip()
                    if not next_line:
                        lookahead += 1
                        continue
                    if ROW_PATTERN.match(lines[lookahead]) or any(
                        pattern in next_line for pattern in HEADER_PATTERNS
                    ):
                        break
                    if re.fullmatch(r"[A-Za-z가-힣&\-]+", next_line):
                        subject_name += next_line
                        break
                    break

            if not all([subject_name, credits, professor, time_string, raw_department]):
                skipped_rows.append(
                    {
                        "line": index + 1,
                        "courseCode": course_code,
                        "parts": parts,
                        "pendingFragments": pending_fragments.copy(),
                    }
                )
                pending_fragments = []
                index += 1
                continue

            if not credits.isdigit():
                skipped_rows.append(
                    {
                        "line": index + 1,
                        "courseCode": course_code,
                        "parts": parts,
                        "pendingFragments": pending_fragments.copy(),
                    }
                )
                pending_fragments = []
                index += 1
                continue

            for department in parse_departments(raw_department, department_map):
                expanded_entries.append(
                    ParsedEntry(
                        subject_name=subject_name,
                        professor=professor,
                        department=department,
                        grade=grade,
                        subject_type=subject_type,
                        course_code=course_code,
                        time_string=time_string,
                    )
                )

            pending_fragments = []
        else:
            if looks_like_fragment(stripped):
                pending_fragments.append(stripped)
                pending_fragments = pending_fragments[-2:]

        index += 1

    return expanded_entries, skipped_rows, raw_row_count


def fetch_db_snapshot(connection):
    with connection.cursor() as cursor:
        cursor.execute(
            "select distinct department from subjects where department is not null order by department"
        )
        departments = [row[0] for row in cursor.fetchall()]

        cursor.execute(
            "select subject_name, professor, department, grade, subject_type from subjects"
        )
        rows = cursor.fetchall()

        cursor.execute(
            """
            select s.subject_name, s.professor, s.department, s.grade, s.subject_type,
                   sch.day_of_week, sch.start_time, sch.end_time
            from subjects s
            left join schedules sch on sch.subject_id = s.id
            """
        )
        schedule_rows = cursor.fetchall()

    return departments, rows, schedule_rows


def build_report(
    entries: list[ParsedEntry],
    skipped_rows: list[dict[str, object]],
    raw_row_count: int,
    db_rows,
    schedule_rows,
) -> dict[str, object]:
    db_full_keys = {
        full_key(subject_name, professor, department, grade, subject_type)
        for subject_name, professor, department, grade, subject_type in db_rows
    }
    db_relaxed_map: dict[str, list[str]] = defaultdict(list)
    for subject_name, professor, department, grade, subject_type in db_rows:
        db_relaxed_map[
            relaxed_key(subject_name, professor, grade, subject_type)
        ].append(department)

    db_schedule_map: dict[str, list[tuple[str, float, float]]] = defaultdict(list)
    for (
        subject_name,
        professor,
        department,
        grade,
        subject_type,
        day_of_week,
        start_time,
        end_time,
    ) in schedule_rows:
        if day_of_week is None or start_time is None or end_time is None:
            continue
        db_schedule_map[
            full_key(subject_name, professor, department, grade, subject_type)
        ].append(parse_schedule_key(day_of_week, start_time, end_time))

    for subject_key_name in list(db_schedule_map.keys()):
        db_schedule_map[subject_key_name] = sorted(db_schedule_map[subject_key_name])

    matched_full = 0
    matched_relaxed = 0
    time_match_count = 0
    time_mismatches: list[dict[str, object]] = []
    department_only_mismatches: list[dict[str, object]] = []
    hard_mismatches: list[dict[str, object]] = []

    for entry in entries:
        entry_full_key = full_key(
            entry.subject_name,
            entry.professor,
            entry.department,
            entry.grade,
            entry.subject_type,
        )
        entry_relaxed_key = relaxed_key(
            entry.subject_name,
            entry.professor,
            entry.grade,
            entry.subject_type,
        )

        if entry_full_key in db_full_keys:
            matched_full += 1
            matched_relaxed += 1
            parsed_schedules = parse_time_string(entry.time_string)
            db_schedules = db_schedule_map.get(entry_full_key, [])
            if parsed_schedules == db_schedules:
                time_match_count += 1
            elif len(time_mismatches) < 15:
                time_mismatches.append(
                    {
                        "courseCode": entry.course_code,
                        "subjectName": entry.subject_name,
                        "professor": entry.professor,
                        "department": entry.department,
                        "timeString": entry.time_string,
                        "parsedSchedule": parsed_schedules,
                        "dbSchedule": db_schedules,
                    }
                )
            continue

        if entry_relaxed_key in db_relaxed_map:
            matched_relaxed += 1
            if len(department_only_mismatches) < 15:
                department_only_mismatches.append(
                    {
                        "courseCode": entry.course_code,
                        "subjectName": entry.subject_name,
                        "professor": entry.professor,
                        "parsedDepartment": entry.department,
                        "dbDepartments": db_relaxed_map[entry_relaxed_key][:10],
                    }
                )
            continue

        if len(hard_mismatches) < 15:
            hard_mismatches.append(
                {
                    "courseCode": entry.course_code,
                    "subjectName": entry.subject_name,
                    "professor": entry.professor,
                    "department": entry.department,
                    "grade": entry.grade,
                    "subjectType": entry.subject_type,
                }
            )

    expanded_count = len(entries)
    full_accuracy = round((matched_full / expanded_count) * 100, 1) if expanded_count else 0.0
    relaxed_accuracy = (
        round((matched_relaxed / expanded_count) * 100, 1) if expanded_count else 0.0
    )

    return {
        "summary": {
            "dbSubjectCount": len(db_rows),
            "rawPdfRowCount": raw_row_count,
            "parsedRawRowCount": raw_row_count - len(skipped_rows),
            "skippedRawRowCount": len(skipped_rows),
            "expandedParsedEntryCount": expanded_count,
            "fullMatchCount": matched_full,
            "departmentOnlyMismatchCount": matched_relaxed - matched_full,
            "hardMismatchCount": expanded_count - matched_relaxed,
            "fullAccuracyPercent": full_accuracy,
            "relaxedAccuracyPercent": relaxed_accuracy,
            "timeMatchCount": time_match_count,
            "timeMismatchCount": matched_full - time_match_count,
        },
        "notes": [
            "이 비교는 단일 PDF 파일과 현재 DB를 비교한 결과입니다. DB 전체 과목 수와 1:1로 맞추는 용도는 아닙니다.",
            "fullAccuracy는 과목명, 교수명, 학과, 학년, 이수구분이 모두 일치한 경우입니다.",
            "relaxedAccuracy는 학과를 제외하고 과목명, 교수명, 학년, 이수구분만 맞춘 경우입니다.",
            "timeMismatchCount는 full match된 항목 안에서 PDF 시간 문자열을 코드 방식으로 해석한 결과와 DB schedules가 다른 경우입니다.",
        ],
        "sampleSkippedRows": skipped_rows[:10],
        "sampleTimeMismatches": time_mismatches,
        "sampleDepartmentOnlyMismatches": department_only_mismatches,
        "sampleHardMismatches": hard_mismatches,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="LLM 없이 PDF를 규칙 기반으로 파싱한 뒤 DB와 비교합니다."
    )
    parser.add_argument("pdf_path", type=Path, help="비교할 PDF 경로")
    parser.add_argument(
        "--dotenv",
        type=Path,
        default=Path(".env"),
        help="DB 연결 정보를 읽을 .env 경로",
    )
    args = parser.parse_args()

    load_dotenv(args.dotenv)

    required_env = ["DB_URL", "DB_USERNAME", "DB_PASSWORD"]
    missing = [name for name in required_env if name not in os.environ]
    if missing:
        print(json.dumps({"error": f"Missing env: {', '.join(missing)}"}, ensure_ascii=False))
        return 1

    try:
        connection = db_connect()
    except Exception as error:
        print(json.dumps({"error": f"DB connection failed: {error}"}, ensure_ascii=False))
        return 1

    try:
        db_departments, db_rows, schedule_rows = fetch_db_snapshot(connection)
        department_map = build_department_map(db_departments)
        lines = extract_pdf_text(args.pdf_path)
        entries, skipped_rows, raw_row_count = parse_pdf_rows(lines, department_map)
        report = build_report(entries, skipped_rows, raw_row_count, db_rows, schedule_rows)
        report["pdfPath"] = str(args.pdf_path.resolve())
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    finally:
        connection.close()


if __name__ == "__main__":
    sys.exit(main())
