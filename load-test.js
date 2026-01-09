import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Ramp up to 50 users
    { duration: '1m', target: 100 },   // Ramp up to 100 users
    { duration: '2m', target: 100 },   // Stay at 100 users
    { duration: '30s', target: 200 },  // Spike to 200 users
    { duration: '1m', target: 200 },   // Stay at 200 users
    { duration: '30s', target: 0 },    // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% of requests should be below 500ms
    http_req_failed: ['rate<0.05'],                 // Error rate should be less than 5%
    errors: ['rate<0.1'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Test scenario: Mix of subject queries and timetable combination requests

  // 1. Get subjects (70% of traffic)
  if (Math.random() < 0.7) {
    testSubjectQueries();
  }
  // 2. Generate timetable combinations (30% of traffic - more CPU intensive)
  else {
    testTimetableCombinations();
  }

  sleep(Math.random() * 2 + 1); // Random sleep between 1-3 seconds
}

function testSubjectQueries() {
  const scenarios = [
    // Paginated subject list
    () => {
      const page = Math.floor(Math.random() * 20);
      const res = http.get(`${BASE_URL}/api/subjects?page=${page}&size=20`);
      check(res, {
        'subjects query status 200': (r) => r.status === 200,
        'subjects query duration < 300ms': (r) => r.timings.duration < 300,
      }) || errorRate.add(1);
    },

    // Search subjects
    () => {
      const keywords = ['컴퓨터', '수학', '영어', '물리', '경제'];
      const keyword = keywords[Math.floor(Math.random() * keywords.length)];
      const encodedKeyword = encodeURIComponent(keyword);
      const res = http.get(`${BASE_URL}/api/subjects/search?keyword=${encodedKeyword}`);
      check(res, {
        'search status 200': (r) => r.status === 200,
        'search duration < 400ms': (r) => r.timings.duration < 400,
      }) || errorRate.add(1);
    },

    // Filter subjects
    () => {
      const grades = [1, 2, 3, 4];
      const grade = grades[Math.floor(Math.random() * grades.length)];
      const res = http.get(`${BASE_URL}/api/subjects/filter?grade=${grade}&page=0&size=20`);
      check(res, {
        'filter status 200': (r) => r.status === 200,
        'filter duration < 500ms': (r) => r.timings.duration < 500,
      }) || errorRate.add(1);
    },

    // Get subject count
    () => {
      const res = http.get(`${BASE_URL}/api/subjects/count`);
      check(res, {
        'count status 200': (r) => r.status === 200,
        'count duration < 100ms': (r) => r.timings.duration < 100,
      }) || errorRate.add(1);
    },
  ];

  // Execute random scenario
  const scenario = scenarios[Math.floor(Math.random() * scenarios.length)];
  scenario();
}

function testTimetableCombinations() {
  const payload = JSON.stringify({
    userId: Math.floor(Math.random() * 1000) + 1,
    semester: '2024-1',
    targetCredits: 18,
    maxCombinations: 20,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/api/timetable-combination/generate`, payload, params);

  check(res, {
    'timetable generation status 200': (r) => r.status === 200,
    'timetable generation duration < 2000ms': (r) => r.timings.duration < 2000,
    'timetable has combinations': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.combinations !== undefined;
      } catch {
        return false;
      }
    },
  }) || errorRate.add(1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'load-test-results.json': JSON.stringify(data),
  };
}

function textSummary(data, opts) {
  const indent = opts.indent || '';
  const colors = opts.enableColors !== false;

  let summary = `\n${indent}Test Summary:\n`;
  summary += `${indent}  Scenarios executed: ${data.metrics.iterations.values.count}\n`;
  summary += `${indent}  HTTP requests: ${data.metrics.http_reqs.values.count}\n`;
  summary += `${indent}  Failed requests: ${data.metrics.http_req_failed.values.rate * 100}%\n`;
  summary += `${indent}  Average response time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
  summary += `${indent}  p95 response time: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`;
  summary += `${indent}  p99 response time: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n`;
  summary += `${indent}  Error rate: ${(data.metrics.errors?.values.rate || 0) * 100}%\n`;

  return summary;
}
