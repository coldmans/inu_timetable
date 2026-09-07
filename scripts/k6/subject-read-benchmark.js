import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const benchmarkName = __ENV.BENCHMARK_NAME || 'subject-read-benchmark';
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const resultFile = __ENV.RESULT_FILE || 'subject-read-benchmark-results.json';
const profile = __ENV.PROFILE || 'portfolio';
const peakVus = Number(__ENV.PEAK_VUS || 200);
const warmupEnabled = (__ENV.WARMUP || 'true') !== 'false';
const authorizationHeaders = __ENV.ID_TOKEN
  ? { Authorization: `Bearer ${__ENV.ID_TOKEN}` }
  : {};

const errors = new Rate('subject_read_errors');
const countDuration = new Trend('subject_read_count_duration');
const departmentsDuration = new Trend('subject_read_departments_duration');
const gradesDuration = new Trend('subject_read_grades_duration');
const searchDuration = new Trend('subject_read_search_duration');
const professorSearchDuration = new Trend('subject_read_professor_search_duration');
const filterDuration = new Trend('subject_read_filter_duration');

const profiles = {
  smoke: [
    { duration: '5s', target: Math.min(10, peakVus) },
    { duration: '10s', target: Math.min(10, peakVus) },
    { duration: '5s', target: 0 },
  ],
  portfolio: [
    { duration: '20s', target: Math.max(1, Math.floor(peakVus * 0.15)) },
    { duration: '1m', target: Math.max(1, Math.floor(peakVus * 0.5)) },
    { duration: '1m', target: Math.max(1, Math.floor(peakVus * 0.5)) },
    { duration: '20s', target: peakVus },
    { duration: '40s', target: peakVus },
    { duration: '20s', target: 0 },
  ],
};

export const options = {
  stages: profiles[profile] || profiles.portfolio,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    subject_read_errors: ['rate<0.01'],
  },
};

const subjectKeywords = ['컴퓨터', '수학', '영어', '물리', '경제'];
const professorKeywords = ['김', '이', '박', '최', '정'];
const filterQueries = [
  'grade=1&page=0&size=20',
  'grade=2&page=0&size=20',
  'grade=3&page=0&size=20',
  'grade=4&page=0&size=20',
  `subjectName=${encodeURIComponent('컴퓨터')}&page=0&size=20`,
  `department=${encodeURIComponent('컴퓨터공학부')}&page=0&size=20`,
];

export function setup() {
  if (!warmupEnabled) {
    return;
  }

  for (let index = 0; index < 3; index += 1) {
    runCount();
    runDepartments();
    runGrades();
    runSubjectSearch(subjectKeywords[index % subjectKeywords.length]);
    runProfessorSearch(professorKeywords[index % professorKeywords.length]);
    runFilter(filterQueries[index % filterQueries.length]);
  }
}

export default function () {
  const roll = Math.random();

  if (roll < 0.20) {
    runCount();
  } else if (roll < 0.35) {
    runDepartments();
  } else if (roll < 0.50) {
    runGrades();
  } else if (roll < 0.70) {
    runSubjectSearch(randomItem(subjectKeywords));
  } else if (roll < 0.85) {
    runProfessorSearch(randomItem(professorKeywords));
  } else {
    runFilter(randomItem(filterQueries));
  }

  sleep(Math.random() * 0.5 + 0.2);
}

function runCount() {
  const response = http.get(
    `${baseUrl}/api/subjects/count`,
    requestParams('subject-count'),
  );
  countDuration.add(response.timings.duration);
  record('count status 200', response);
}

function runDepartments() {
  const response = http.get(
    `${baseUrl}/api/subjects/departments`,
    requestParams('subject-departments'),
  );
  departmentsDuration.add(response.timings.duration);
  record('departments status 200', response);
}

function runGrades() {
  const response = http.get(
    `${baseUrl}/api/subjects/grades`,
    requestParams('subject-grades'),
  );
  gradesDuration.add(response.timings.duration);
  record('grades status 200', response);
}

function runSubjectSearch(keyword) {
  const response = http.get(
    `${baseUrl}/api/subjects/search?keyword=${encodeURIComponent(keyword)}`,
    requestParams('subject-search'),
  );
  searchDuration.add(response.timings.duration);
  record('subject search status 200', response);
}

function runProfessorSearch(keyword) {
  const response = http.get(
    `${baseUrl}/api/subjects/search/professor?keyword=${encodeURIComponent(keyword)}`,
    requestParams('subject-professor-search'),
  );
  professorSearchDuration.add(response.timings.duration);
  record('professor search status 200', response);
}

function runFilter(query) {
  const response = http.get(
    `${baseUrl}/api/subjects/filter?${query}`,
    requestParams('subject-filter'),
  );
  filterDuration.add(response.timings.duration);
  record('filter status 200', response);
}

function record(name, response) {
  const ok = check(response, { [name]: (result) => result.status === 200 });
  errors.add(!ok);
}

function randomItem(values) {
  return values[Math.floor(Math.random() * values.length)];
}

function requestParams(endpoint) {
  return {
    headers: authorizationHeaders,
    tags: { endpoint },
  };
}

export function handleSummary(data) {
  const artifact = {
    benchmark: {
      name: benchmarkName,
      baseUrl,
      profile,
      peakVus,
      warmupEnabled,
      completedAt: new Date().toISOString(),
    },
    result: data,
  };

  return {
    stdout: summaryText(artifact),
    [resultFile]: JSON.stringify(artifact, null, 2),
  };
}

function summaryText(artifact) {
  const metric = (name, key) => artifact.result.metrics[name]?.values?.[key];
  const duration = (name, key) => format(metric(name, key), 'ms');

  return [
    '',
    `Subject read benchmark: ${artifact.benchmark.name}`,
    `  Base URL: ${artifact.benchmark.baseUrl}`,
    `  Profile: ${artifact.benchmark.profile} (peak ${artifact.benchmark.peakVus} VUs)`,
    `  Requests: ${metric('http_reqs', 'count') ?? 0}`,
    `  Failed requests: ${formatRate(metric('http_req_failed', 'rate'))}`,
    `  Overall avg: ${duration('http_req_duration', 'avg')}`,
    `  Overall p95: ${duration('http_req_duration', 'p(95)')}`,
    `  Overall p99: ${duration('http_req_duration', 'p(99)')}`,
    `  Count p95: ${duration('subject_read_count_duration', 'p(95)')}`,
    `  Departments p95: ${duration('subject_read_departments_duration', 'p(95)')}`,
    `  Grades p95: ${duration('subject_read_grades_duration', 'p(95)')}`,
    `  Search p95: ${duration('subject_read_search_duration', 'p(95)')}`,
    `  Professor search p95: ${duration('subject_read_professor_search_duration', 'p(95)')}`,
    `  Filter p95: ${duration('subject_read_filter_duration', 'p(95)')}`,
    '',
  ].join('\n');
}

function format(value, suffix) {
  return value === undefined ? 'n/a' : `${value.toFixed(2)}${suffix}`;
}

function formatRate(value) {
  return value === undefined ? 'n/a' : `${(value * 100).toFixed(2)}%`;
}
