/**
 * 관리자 콘솔 1440px 브라우저 스모크.
 *
 * 전제:
 *   - `docker compose up -d --wait` + `./gradlew bootRun`으로 앱이 떠 있어야 한다.
 *   - `npm install --no-save playwright` 후 `node scripts/admin-smoke.mjs`.
 *
 * 동작:
 *   - 실제 Chrome으로 `/admin/login` 로그인(CSRF 폼 포함)
 *   - 각 콘솔 화면을 1440x900으로 방문해 상태·가로 오버플로·콘솔 오류를 수집
 *   - 공지 등록/삭제 POST로 실제 브라우저 CSRF 흐름 확인
 *   - 실패(오버플로/콘솔 오류) 시 exit 1
 *
 * 환경변수: BASE_URL(기본 http://localhost:8080), OUT_DIR(기본 build/admin-smoke)
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const base = process.env.BASE_URL ?? 'http://localhost:8080';
const outDir = process.env.OUT_DIR ?? 'build/admin-smoke';
const adminEmail = process.env.ADMIN_EMAIL ?? 'admin@lookation.local';
const adminPassword = process.env.ADMIN_PASSWORD ?? 'lookation-admin1!';
mkdirSync(outDir, { recursive: true });

const pages = [
  ['/admin', 'dashboard'],
  ['/admin/accounts', 'accounts'],
  ['/admin/reports', 'reports'],
  ['/admin/inspections', 'inspections'],
  ['/admin/charges', 'charges'],
  ['/admin/withdrawals', 'withdrawals'],
  ['/admin/notices', 'notices'],
  ['/admin/helps', 'helps'],
  ['/admin/qnas', 'qnas'],
  ['/admin/reviews', 'reviews'],
];

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
const consoleErrors = [];
page.on('console', (message) => {
  if (message.type() === 'error') consoleErrors.push(`${page.url()} :: ${message.text()}`);
});
page.on('pageerror', (error) => consoleErrors.push(`${page.url()} :: pageerror ${error.message}`));

const results = [];

async function visit(path, slug) {
  const response = await page.goto(`${base}${path}`, { waitUntil: 'networkidle' });
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  await page.screenshot({ path: `${outDir}/${slug}-1440.png`, fullPage: true });
  results.push({ path, status: response?.status(), ...metrics, overflow: metrics.scrollWidth > metrics.innerWidth });
}

await page.goto(`${base}/admin/login`, { waitUntil: 'networkidle' });
await page.fill('#admin_id', adminEmail);
await page.fill('#admin_pw', adminPassword);
await Promise.all([
  page.waitForURL('**/admin'),
  page.click('form[action="/admin/login"] button[type=submit]'),
]);
results.push({ path: '/admin/login', status: 'logged-in' });

for (const [path, slug] of pages) {
  await visit(path, slug);
  if (path === '/admin/accounts') {
    const detailHref = await page.locator('a[href^="/admin/accounts/"]').first().getAttribute('href');
    if (detailHref) await visit(detailHref, 'account-detail');
  }
}

// 실제 브라우저 POST의 CSRF 토큰이 동작하는지 확인하고 데이터를 정리한다.
await page.goto(`${base}/admin/notices/new`, { waitUntil: 'networkidle' });
await page.fill('#title', `스모크 공지 ${Date.now()}`);
await page.fill('textarea[name=content]', '1440px 스모크에서 생성한 공지');
await Promise.all([
  page.waitForURL(/\/admin\/notices\/\d+$/),
  page.click('button:has-text("저장")'),
]);
const noticePath = new URL(page.url()).pathname;
results.push({ path: noticePath, status: 'created' });
await Promise.all([
  page.waitForURL('**/admin/notices'),
  page.click('button:has-text("삭제")'),
]);
results.push({ path: noticePath, status: 'deleted' });

await browser.close();

const overflowPages = results.filter((result) => result.overflow);
console.log(JSON.stringify({ results, consoleErrors, overflowPages }, null, 2));
if (consoleErrors.length > 0 || overflowPages.length > 0) process.exit(1);
