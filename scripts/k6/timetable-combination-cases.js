import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEMESTER = __ENV.SEMESTER || '2026-1';
const TARGET_CREDITS = Number(__ENV.TARGET_CREDITS || 18);
const MAX_COMBINATIONS = Number(__ENV.MAX_COMBINATIONS || 20);
const SLOT_COUNT = Number(__ENV.SLOT_COUNT || 6);
const CASES = parseCases(__ENV.CASES || '6,12,18,24,30');
const VUS_PER_CASE = Number(__ENV.VUS_PER_CASE || 2);
const DURATION = __ENV.DURATION || '30s';
const THINK_TIME_MS = Number(__ENV.THINK_TIME_MS || 100);

const combinationErrors = new Rate('combination_errors');
const combinationDuration = new Trend('combination_duration', true);
const combinationTotalCount = new Trend('combination_total_count');
const scenarioPrepared = new Counter('combination_scenario_prepared');

const caseThresholds = Object.fromEntries(
  CASES.flatMap((size) => [
    [`combination_duration{case_size:${size}}`, ['p(95)<10000']],
    [`combination_total_count{case_size:${size}}`, ['avg>=0']],
  ]),
);

export const options = {
  noCookiesReset: true,
  scenarios: Object.fromEntries(
    CASES.map((size) => [
      `case_${size}`,
      {
        executor: 'constant-vus',
        exec: 'runCase',
        vus: VUS_PER_CASE,
        duration: DURATION,
        gracefulStop: '10s',
        env: {
          WISHLIST_SIZE: String(size),
        },
      },
    ]),
  ),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    combination_errors: ['rate<0.01'],
    ...caseThresholds,
  },
};

let state = null;

export function runCase() {
  const wishlistSize = Number(__ENV.WISHLIST_SIZE || 24);
  const caseSize = String(wishlistSize);

  if (!state) {
    state = prepareScenario(wishlistSize);
    scenarioPrepared.add(1, { case_size: caseSize });
  }

  const response = http.post(
    `${BASE_URL}/api/timetable-combination/generate`,
    JSON.stringify({
      userId: state.userId,
      semester: SEMESTER,
      targetCredits: TARGET_CREDITS,
      maxCombinations: MAX_COMBINATIONS,
      freeDays: [],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': state.csrfToken,
      },
      tags: {
        endpoint: 'combination-generate',
        case_size: caseSize,
      },
    },
  );

  combinationDuration.add(response.timings.duration, { case_size: caseSize });
  const ok = check(response, {
    'combination status 200': (res) => res.status === 200,
    'combination body has totalCount': (res) => {
      const body = parseJson(res);
      return body && typeof body.totalCount === 'number';
    },
  });
  combinationErrors.add(!ok, { case_size: caseSize });

  const body = parseJson(response);
  if (body && typeof body.totalCount === 'number') {
    combinationTotalCount.add(body.totalCount, { case_size: caseSize });
  }

  sleep(THINK_TIME_MS / 1000);
}

function prepareScenario(wishlistSize) {
  const scenarioName = exec.scenario.name;
  const username = `k6-combination-${wishlistSize}-${scenarioName}-vu-${__VU}`;

  const scenarioResponse = http.post(
    `${BASE_URL}/api/dev/combination-scenario`,
    JSON.stringify({
      username,
      semester: SEMESTER,
      wishlistSize,
      slotCount: SLOT_COUNT,
      reset: true,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
      },
      tags: {
        endpoint: 'dev-combination-scenario',
        case_size: String(wishlistSize),
      },
    },
  );

  check(scenarioResponse, {
    'scenario prepared': (res) => res.status === 200,
  });

  const scenarioBody = parseJson(scenarioResponse);
  if (!scenarioBody?.user?.id) {
    throw new Error(`failed to prepare scenario for ${username}: ${scenarioResponse.status} ${scenarioResponse.body}`);
  }

  const csrfResponse = http.get(`${BASE_URL}/api/auth/csrf`, {
    tags: {
      endpoint: 'auth-csrf',
      case_size: String(wishlistSize),
    },
  });
  const csrfBody = parseJson(csrfResponse);
  if (csrfResponse.status !== 200 || !csrfBody?.token) {
    throw new Error(`failed to fetch csrf token for ${username}: ${csrfResponse.status} ${csrfResponse.body}`);
  }

  return {
    userId: scenarioBody.user.id,
    username,
    csrfToken: csrfBody.token,
  };
}

function parseCases(raw) {
  return raw
    .split(',')
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isFinite(value) && value > 0);
}

function parseJson(response) {
  try {
    return JSON.parse(response.body);
  } catch {
    return null;
  }
}

export function handleSummary(data) {
  return {
    stdout: summaryText(data),
    'reports/combination-performance/latest-k6-results.json': JSON.stringify(data, null, 2),
  };
}

function summaryText(data) {
  const metric = (name, key) => data.metrics[name]?.values?.[key];
  const tagged = (name, caseSize, key) => data.metrics[`${name}{case_size:${caseSize}}`]?.values?.[key];

  const lines = [
    '',
    'Timetable combination k6 cases',
    `  Base URL: ${BASE_URL}`,
    `  Cases: ${CASES.join(', ')}`,
    `  VUs per case: ${VUS_PER_CASE}`,
    `  Duration: ${DURATION}`,
    `  Requests: ${formatNumber(metric('http_reqs', 'count'))}`,
    `  Failed requests: ${formatRate(metric('http_req_failed', 'rate'))}`,
    `  Combination errors: ${formatRate(metric('combination_errors', 'rate'))}`,
    '',
    '  case | avg | p95 | p99 | avg combinations',
    '  ---: | --: | --: | --: | --:',
  ];

  for (const caseSize of CASES) {
    lines.push([
      `  ${caseSize}`,
      formatMs(tagged('combination_duration', caseSize, 'avg')),
      formatMs(tagged('combination_duration', caseSize, 'p(95)')),
      formatMs(tagged('combination_duration', caseSize, 'p(99)')),
      formatNumber(tagged('combination_total_count', caseSize, 'avg')),
    ].join(' | '));
  }

  lines.push('');
  return lines.join('\n');
}

function formatMs(value) {
  return value === undefined ? 'n/a' : `${value.toFixed(2)}ms`;
}

function formatNumber(value) {
  return value === undefined ? 'n/a' : value.toFixed(2);
}

function formatRate(value) {
  return value === undefined ? 'n/a' : `${(value * 100).toFixed(2)}%`;
}
