// Нагрузочный тест k6 для contract-audit.
// Запуск:  k6 run -e BASE_URL=https://contract-audit.example.com load/contract-audit.js
//
// Регистрируем арендатора ОДИН раз в setup() и переиспользуем токен во всех VU —
// иначе per-VU /login упрётся в rate limit (10/мин на IP) и даст ложные 429.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errors = new Rate('app_errors');

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // разгон
    { duration: '1m', target: 20 },    // плато
    { duration: '30s', target: 50 },   // пик
    { duration: '30s', target: 0 },    // спад
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],         // < 1% сетевых ошибок
    http_req_duration: ['p(95)<1500'],      // p95 < 1.5с
    app_errors: ['rate<0.02'],              // < 2% бизнес-ошибок (не 2xx)
  },
};

export function setup() {
  const slug = `load-${Date.now()}`;
  const res = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
    companyName: 'Load Test Co',
    slug,
    email: `admin@${slug}.com`,
    password: 'password1',
  }), { headers: { 'Content-Type': 'application/json' } });

  check(res, { 'register 200': (r) => r.status === 200 });
  return { token: res.json('accessToken') };
}

export default function (data) {
  const authHeaders = {
    headers: {
      'Authorization': `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    },
  };

  // Лёгкий запрос — список документов арендатора.
  const list = http.get(`${BASE_URL}/api/documents`, authHeaders);
  errors.add(list.status !== 200);
  check(list, { 'documents 200': (r) => r.status === 200 });

  // Раз в ~5 итераций — более тяжёлый семантический поиск.
  if (Math.random() < 0.2) {
    const search = http.post(`${BASE_URL}/api/search`,
      JSON.stringify({ query: 'кто платит за доставку' }), authHeaders);
    errors.add(search.status !== 200);
    check(search, { 'search 200': (r) => r.status === 200 });
  }

  sleep(1);
}
