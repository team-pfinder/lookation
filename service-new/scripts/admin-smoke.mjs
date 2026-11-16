/**
 * 관리자 콘솔 1440px 브라우저 스모크.
 *
 * 전제:
 *   - `docker compose up -d --wait` + `./gradlew bootRun`으로 앱이 떠 있어야 한다.
 *   - `npm install --no-save playwright` 후 `node scripts/admin-smoke.mjs`.
 *
 * 동작:
 *   - 실제 Chrome으로 `/admin/login` 로그인(CSRF 폼 포함)
 *   - 각 Phase의 목록·상세·등록/수정 화면을 1440x900으로 방문해 상태·가로 오버플로·콘솔 오류를 수집
 *   - 공지·도움말 등록/수정/삭제 POST로 실제 브라우저 CSRF 흐름 확인
 *   - 실패(상세 링크 없음/오버플로/콘솔 오류/비200) 시 exit 1
 *
 * 환경변수: BASE_URL(기본 http://localhost:8080), OUT_DIR(기본 build/admin-smoke)
 */
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';

const base = process.env.BASE_URL ?? 'http://localhost:8080';
const outDir = process.env.OUT_DIR ?? 'build/admin-smoke';
const adminEmail = process.env.ADMIN_EMAIL ?? 'admin@lookation.local';
const adminPassword = process.env.ADMIN_PASSWORD ?? 'lookation-admin1!';
mkdirSync(outDir, { recursive: true });

const screens = [
  ['/admin', 'dashboard'],
  ['/admin/accounts', 'accounts', '/admin/accounts/'],
  ['/admin/reports', 'reports', '/admin/reports/'],
  ['/admin/inspections', 'inspections', '/admin/inspections/'],
  ['/admin/charges', 'charges', '/admin/charges/'],
  ['/admin/withdrawals', 'withdrawals', '/admin/withdrawals/'],
  ['/admin/notices', 'notices', '/admin/notices/'],
  ['/admin/helps', 'helps', '/admin/helps/'],
  ['/admin/qnas', 'qnas', '/admin/qnas/'],
  ['/admin/reviews', 'reviews', '/admin/reviews/'],
];

const newForms = [
  ['/admin/notices/new', 'notice-new'],
  ['/admin/helps/new', 'help-new'],
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
  const status = response?.status() ?? 0;
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  await page.screenshot({ path: `${outDir}/${slug}-1440.png`, fullPage: true });
  results.push({ path, slug, status, ...metrics, overflow: metrics.scrollWidth > metrics.innerWidth });
  return status;
}

async function firstDetailPath(prefix) {
  return page.evaluate((pathPrefix) => {
    const pattern = new RegExp(`^${pathPrefix}\\d+$`);
    const link = [...document.querySelectorAll('a[href]')].find((anchor) => pattern.test(anchor.getAttribute('href') ?? ''));
    return link?.getAttribute('href') ?? null;
  }, prefix);
}

await visit('/admin/login', 'login');
await page.fill('#admin_id', adminEmail);
await page.fill('#admin_pw', adminPassword);
await Promise.all([
  page.waitForURL('**/admin'),
  page.click('form[action="/admin/login"] button[type=submit]'),
]);

for (const [path, slug, detailPrefix] of screens) {
  await visit(path, slug);
  if (!detailPrefix) continue;

  const detailPath = await firstDetailPath(detailPrefix);
  if (!detailPath) {
    consoleErrors.push(`${path} :: 상세 링크가 없어 스모크할 수 없습니다`);
    continue;
  }
  await visit(detailPath, `${slug}-detail`);

  if (slug === 'notices' || slug === 'helps') {
    const editPath = await page.evaluate(() => {
      const link = [...document.querySelectorAll('a[href]')].find((anchor) =>
        /^\/admin\/(notices|helps)\/\d+\/edit$/.test(anchor.getAttribute('href') ?? ''),
      );
      return link?.getAttribute('href') ?? null;
    });
    if (editPath) await visit(editPath, `${slug}-edit`);
  }
}

for (const [path, slug] of newForms) {
  await visit(path, slug);
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
results.push({ path: noticePath, slug: 'notice-created', status: 'created' });

await Promise.all([
  page.waitForURL(/\/admin\/notices\/\d+\/edit$/),
  page.click('a:has-text("수정")'),
]);
await page.fill('#title', `스모크 공지 수정 ${Date.now()}`);
await Promise.all([
  page.waitForURL(/\/admin\/notices\/\d+$/),
  page.click('button:has-text("저장")'),
]);
results.push({ path: noticePath, slug: 'notice-updated', status: 'updated' });

await Promise.all([
  page.waitForURL('**/admin/notices'),
  page.click('button:has-text("삭제")'),
]);
results.push({ path: noticePath, slug: 'notice-deleted', status: 'deleted' });

await page.goto(`${base}/admin/helps/new`, { waitUntil: 'networkidle' });
await page.fill('input[name=category]', '스모크');
await page.fill('input[name=title]', `스모크 도움말 ${Date.now()}`);
await page.fill('textarea[name=content]', '1440px 스모크에서 생성한 도움말');
await Promise.all([
  page.waitForURL(/\/admin\/helps\/\d+$/),
  page.click('button:has-text("저장")'),
]);
const helpPath = new URL(page.url()).pathname;
results.push({ path: helpPath, slug: 'help-created', status: 'created' });

await Promise.all([
  page.waitForURL(/\/admin\/helps\/\d+\/edit$/),
  page.click('a:has-text("수정")'),
]);
await page.fill('input[name=title]', `스모크 도움말 수정 ${Date.now()}`);
await Promise.all([
  page.waitForURL(/\/admin\/helps\/\d+$/),
  page.click('button:has-text("저장")'),
]);
results.push({ path: helpPath, slug: 'help-updated', status: 'updated' });

await Promise.all([
  page.waitForURL('**/admin/helps'),
  page.click('button:has-text("삭제")'),
]);
results.push({ path: helpPath, slug: 'help-deleted', status: 'deleted' });

await browser.close();

const badStatus = results.filter((result) => typeof result.status === 'number' && result.status !== 200);
const overflowPages = results.filter((result) => result.overflow);
const summary = { base, results, consoleErrors, badStatus, overflowPages };
writeFileSync(`${outDir}/results.json`, JSON.stringify(summary, null, 2));
console.log(JSON.stringify(summary, null, 2));
if (consoleErrors.length > 0 || badStatus.length > 0 || overflowPages.length > 0) process.exit(1);
