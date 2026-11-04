import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import exec from 'k6/execution';

const gateway = __ENV.GATEWAY_URL || 'http://gateway:8080';
const tokenUrl = __ENV.TOKEN_URL || 'http://keycloak:8080/realms/banco-demo/protocol/openid-connect/token';
const duration = __ENV.DURATION || '5m';
const rate = Number(__ENV.RATE || '20');
const runId = __ENV.RUN_ID || 'local';

export const options = {
  scenarios: {
    mixed_api: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 60,
      maxVUs: 120,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    'http_req_failed{scenario:mixed_api}': ['rate<0.01'],
    'http_req_duration{scenario:mixed_api}': ['p(95)<2000'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'max'],
};

const unexpected = new Rate('unexpected_responses');
const tokenFailures = new Counter('token_failures');
let token;
let tokenExpiresAt = 0;

function accessToken() {
  if (token && Date.now() < tokenExpiresAt) return token;
  const response = http.post(tokenUrl, {
    grant_type: 'password',
    client_id: 'banco-cli',
    username: 'cliente1',
    password: __ENV.DEMO_PASSWORD || 'cliente1-local',
  }, { tags: { name: 'token' } });
  if (response.status !== 200) {
    tokenFailures.add(1);
    return null;
  }
  const body = response.json();
  token = body.access_token;
  tokenExpiresAt = Date.now() + Math.max(30, body.expires_in - 30) * 1000;
  return token;
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/[x]/g, () =>
    Math.floor(Math.random() * 16).toString(16));
}

export default function () {
  const bearer = accessToken();
  if (!bearer) {
    unexpected.add(true);
    return;
  }
  const headers = { Authorization: `Bearer ${bearer}` };
  // 80% lecturas; 20% transferencias reales de 0,01 PEN.
  if (exec.scenario.iterationInTest % 5 !== 0) {
    const response = http.get(`${gateway}/api/v1/cuentas`, {
      headers,
      tags: { name: 'cuentas' },
    });
    const ok = check(response, { 'cuentas 200': (r) => r.status === 200 });
    unexpected.add(!ok);
    return;
  }
  const key = uuid();
  const response = http.post(`${gateway}/api/v1/transferencias`, JSON.stringify({
    cuentaOrigenId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1',
    cuentaDestinoId: 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1',
    importe: '0.01',
    moneda: 'PEN',
  }), {
    headers: { ...headers, 'Content-Type': 'application/json', 'Idempotency-Key': key },
    tags: { name: 'transferencias' },
  });
  const ok = check(response, { 'transferencia confirmada': (r) =>
    r.status === 201 && r.json('estado') === 'CONFIRMADA' });
  unexpected.add(!ok);
  if (!ok) console.error(`${runId} transfer ${key}: ${response.status} ${response.body.substring(0, 200)}`);
}
