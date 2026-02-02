import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const timetableAddSuccess = new Counter('timetable_add_success');
const timetableRemoveSuccess = new Counter('timetable_remove_success');
const wishlistAddSuccess = new Counter('wishlist_add_success');
const wishlistRemoveSuccess = new Counter('wishlist_remove_success');
const combinationGenerateSuccess = new Counter('combination_generate_success');
const apiResponseTime = new Trend('api_response_time');

// Test configuration - 5,000 concurrent users (M4 Pro + 48GB optimized)
export const options = {
  scenarios: {
    stress_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 500 },    // Warm-up: 500 users
        { duration: '1m', target: 2000 },    // Ramp to 2,000 users
        { duration: '1m', target: 3500 },    // Ramp to 3,500 users
        { duration: '1m', target: 5000 },    // Ramp to 5,000 users (peak)
        { duration: '2m', target: 5000 },    // Stay at 5,000 users
        { duration: '30s', target: 2000 },   // Ramp down
        { duration: '30s', target: 0 },      // Ramp down to 0
      ],
      gracefulRampDown: '20s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],  // 95% < 2s, 99% < 5s
    http_req_failed: ['rate<0.1'],                    // Error rate < 10%
    errors: ['rate<0.15'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEMESTER = '2024-2';

// Simulated subject IDs (adjust based on your actual data)
const SUBJECT_IDS = Array.from({ length: 100 }, (_, i) => i + 1);

// Search keywords
const KEYWORDS = ['컴퓨터', '수학', '영어', '물리', '화학', '경제', '경영', '프로그래밍', '알고리즘', '데이터'];
const GRADES = [1, 2, 3, 4];

export default function () {
  // Each virtual user gets a unique ID based on VU number
  const userId = __VU;

  // Simulate realistic user behavior with different scenarios
  const scenario = Math.random();

  if (scenario < 0.25) {
    // 25%: Browse and search subjects
    browseAndSearchSubjects();
  } else if (scenario < 0.50) {
    // 25%: Add/Remove from wishlist (shopping cart behavior)
    wishlistOperations(userId);
  } else if (scenario < 0.75) {
    // 25%: Add/Remove from timetable
    timetableOperations(userId);
  } else {
    // 25%: Full user journey (search -> add to wishlist -> generate combination -> add to timetable)
    fullUserJourney(userId);
  }

  // Random think time between 0.5-2 seconds
  sleep(Math.random() * 1.5 + 0.5);
}

// Scenario 1: Browse and search subjects
function browseAndSearchSubjects() {
  group('Browse Subjects', function () {
    // Get subject count
    let res = http.get(`${BASE_URL}/api/subjects/count`);
    check(res, { 'count status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    // Browse paginated list
    const page = Math.floor(Math.random() * 50);
    res = http.get(`${BASE_URL}/api/subjects?page=${page}&size=20`);
    check(res, { 'subjects list status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    // Search by keyword
    const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    res = http.get(`${BASE_URL}/api/subjects/search?keyword=${encodeURIComponent(keyword)}`);
    check(res, { 'search status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    // Filter by grade
    const grade = GRADES[Math.floor(Math.random() * GRADES.length)];
    res = http.get(`${BASE_URL}/api/subjects/filter?grade=${grade}&page=0&size=20`);
    check(res, { 'filter status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);
  });
}

// Scenario 2: Wishlist operations (add and remove subjects)
function wishlistOperations(userId) {
  group('Wishlist Operations', function () {
    const subjectId = SUBJECT_IDS[Math.floor(Math.random() * SUBJECT_IDS.length)];
    const params = { headers: { 'Content-Type': 'application/json' } };

    // Add to wishlist
    let addPayload = JSON.stringify({
      userId: userId,
      subjectId: subjectId,
      semester: SEMESTER,
      priority: Math.floor(Math.random() * 5) + 1,
      isRequired: Math.random() > 0.5,
    });

    let res = http.post(`${BASE_URL}/api/wishlist/add`, addPayload, params);
    const addSuccess = check(res, {
      'wishlist add status 200': (r) => r.status === 200,
    });
    if (addSuccess) wishlistAddSuccess.add(1);
    else errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    sleep(0.3);

    // Get wishlist
    res = http.get(`${BASE_URL}/api/wishlist/user/${userId}?semester=${SEMESTER}`);
    check(res, { 'get wishlist status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    sleep(0.3);

    // Remove from wishlist (50% chance)
    if (Math.random() > 0.5) {
      res = http.del(`${BASE_URL}/api/wishlist/remove?userId=${userId}&subjectId=${subjectId}`);
      const removeSuccess = check(res, {
        'wishlist remove status 200': (r) => r.status === 200,
      });
      if (removeSuccess) wishlistRemoveSuccess.add(1);
      else errorRate.add(1);
      apiResponseTime.add(res.timings.duration);
    }
  });
}

// Scenario 3: Timetable operations (add and remove subjects)
function timetableOperations(userId) {
  group('Timetable Operations', function () {
    const subjectId = SUBJECT_IDS[Math.floor(Math.random() * SUBJECT_IDS.length)];
    const params = { headers: { 'Content-Type': 'application/json' } };

    // Add subject to timetable
    let addPayload = JSON.stringify({
      userId: userId,
      subjectId: subjectId,
      semester: SEMESTER,
      memo: `Test memo from user ${userId}`,
    });

    let res = http.post(`${BASE_URL}/api/timetable/add`, addPayload, params);
    const addSuccess = check(res, {
      'timetable add status 200 or 400': (r) => r.status === 200 || r.status === 400, // 400 for conflicts
    });
    if (res.status === 200) timetableAddSuccess.add(1);
    else if (res.status !== 400) errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    sleep(0.3);

    // Get user's timetable
    res = http.get(`${BASE_URL}/api/timetable/user/${userId}?semester=${SEMESTER}`);
    check(res, { 'get timetable status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    sleep(0.3);

    // Remove subject from timetable (60% chance)
    if (Math.random() > 0.4) {
      res = http.del(`${BASE_URL}/api/timetable/remove?userId=${userId}&subjectId=${subjectId}`);
      const removeSuccess = check(res, {
        'timetable remove status 200 or 400': (r) => r.status === 200 || r.status === 400,
      });
      if (res.status === 200) timetableRemoveSuccess.add(1);
      else if (res.status !== 400) errorRate.add(1);
      apiResponseTime.add(res.timings.duration);
    }

    // Clear entire timetable (5% chance - simulates "reset")
    if (Math.random() < 0.05) {
      res = http.del(`${BASE_URL}/api/timetable/clear?userId=${userId}&semester=${SEMESTER}`);
      check(res, { 'timetable clear status 200': (r) => r.status === 200 }) || errorRate.add(1);
      apiResponseTime.add(res.timings.duration);
    }
  });
}

// Scenario 4: Full user journey - realistic course registration flow
function fullUserJourney(userId) {
  group('Full User Journey', function () {
    const params = { headers: { 'Content-Type': 'application/json' } };

    // Step 1: Search for subjects
    const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    let res = http.get(`${BASE_URL}/api/subjects/search?keyword=${encodeURIComponent(keyword)}`);
    check(res, { 'search subjects status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    sleep(0.5);

    // Step 2: Add multiple subjects to wishlist
    const selectedSubjects = [];
    for (let i = 0; i < 3; i++) {
      const subjectId = SUBJECT_IDS[Math.floor(Math.random() * SUBJECT_IDS.length)];
      selectedSubjects.push(subjectId);

      const addPayload = JSON.stringify({
        userId: userId,
        subjectId: subjectId,
        semester: SEMESTER,
        priority: i + 1,
        isRequired: i === 0,
      });

      res = http.post(`${BASE_URL}/api/wishlist/add`, addPayload, params);
      if (res.status === 200) wishlistAddSuccess.add(1);
      apiResponseTime.add(res.timings.duration);

      sleep(0.2);
    }

    // Step 3: Generate timetable combinations
    const combinationPayload = JSON.stringify({
      userId: userId,
      semester: SEMESTER,
      targetCredits: Math.floor(Math.random() * 6 + 15), // 15-20 credits
      maxCombinations: 10,
    });

    res = http.post(`${BASE_URL}/api/timetable-combination/generate`, combinationPayload, params);
    const combSuccess = check(res, {
      'combination generate status 200': (r) => r.status === 200,
      'combination response time < 3s': (r) => r.timings.duration < 3000,
    });
    if (combSuccess) combinationGenerateSuccess.add(1);
    else errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    sleep(0.5);

    // Step 4: Add subjects to actual timetable
    for (const subjectId of selectedSubjects) {
      const addPayload = JSON.stringify({
        userId: userId,
        subjectId: subjectId,
        semester: SEMESTER,
        memo: 'Selected from combination',
      });

      res = http.post(`${BASE_URL}/api/timetable/add`, addPayload, params);
      if (res.status === 200) timetableAddSuccess.add(1);
      apiResponseTime.add(res.timings.duration);

      sleep(0.2);
    }

    // Step 5: View final timetable
    res = http.get(`${BASE_URL}/api/timetable/user/${userId}?semester=${SEMESTER}`);
    check(res, { 'final timetable view status 200': (r) => r.status === 200 }) || errorRate.add(1);
    apiResponseTime.add(res.timings.duration);

    // Step 6: Sometimes clear and start over (simulates indecisive user)
    if (Math.random() < 0.1) {
      sleep(0.3);
      res = http.del(`${BASE_URL}/api/timetable/clear?userId=${userId}&semester=${SEMESTER}`);
      check(res, { 'clear timetable status 200': (r) => r.status === 200 }) || errorRate.add(1);
      apiResponseTime.add(res.timings.duration);
    }
  });
}

export function handleSummary(data) {
  const summary = generateTextSummary(data);

  return {
    'stdout': summary,
    'k6-test-results-20k.json': JSON.stringify(data, null, 2),
  };
}

function generateTextSummary(data) {
  const metrics = data.metrics;

  let summary = `
================================================================================
                    20,000 Users Load Test Summary
================================================================================

Test Duration: ${formatDuration(data.state.testRunDurationMs)}
Peak VUs: ${metrics.vus_max?.values?.max || 'N/A'}

HTTP Metrics:
  - Total Requests: ${metrics.http_reqs?.values?.count || 0}
  - Request Rate: ${(metrics.http_reqs?.values?.rate || 0).toFixed(2)} req/s
  - Failed Requests: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%

Response Times:
  - Average: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)} ms
  - Median (p50): ${(metrics.http_req_duration?.values?.['p(50)'] || 0).toFixed(2)} ms
  - p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)} ms
  - p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)} ms
  - Max: ${(metrics.http_req_duration?.values?.max || 0).toFixed(2)} ms

Custom Metrics:
  - Timetable Add Success: ${metrics.timetable_add_success?.values?.count || 0}
  - Timetable Remove Success: ${metrics.timetable_remove_success?.values?.count || 0}
  - Wishlist Add Success: ${metrics.wishlist_add_success?.values?.count || 0}
  - Wishlist Remove Success: ${metrics.wishlist_remove_success?.values?.count || 0}
  - Combination Generate Success: ${metrics.combination_generate_success?.values?.count || 0}
  - Error Rate: ${((metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%

Throughput by Request Type:
  - GET requests: ${metrics.http_req_duration?.values?.count || 'N/A'}
  - API Response Time (avg): ${(metrics.api_response_time?.values?.avg || 0).toFixed(2)} ms

Thresholds:
  - http_req_duration p(95) < 2000ms: ${(metrics.http_req_duration?.values?.['p(95)'] || 0) < 2000 ? 'PASS' : 'FAIL'}
  - http_req_duration p(99) < 5000ms: ${(metrics.http_req_duration?.values?.['p(99)'] || 0) < 5000 ? 'PASS' : 'FAIL'}
  - http_req_failed < 10%: ${(metrics.http_req_failed?.values?.rate || 0) < 0.1 ? 'PASS' : 'FAIL'}
  - errors < 15%: ${(metrics.errors?.values?.rate || 0) < 0.15 ? 'PASS' : 'FAIL'}

================================================================================
`;

  return summary;
}

function formatDuration(ms) {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  } else {
    return `${seconds}s`;
  }
}