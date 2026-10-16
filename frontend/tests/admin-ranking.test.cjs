/* eslint-disable @typescript-eslint/no-require-imports -- Node test harness loads actual TS modules in CommonJS isolation. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '../src');

function load(relative, mocks = {}) {
  const file = path.resolve(root, relative);
  const source = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const loadedModule = { exports: {} };
  const localRequire = (name) => {
    if (Object.hasOwn(mocks, name)) return mocks[name];
    if (name.endsWith('.css')) return new Proxy({}, { get: (_, key) => key === '__esModule' ? false : String(key) });
    if (!name.startsWith('.')) return require(name);
    const target = path.resolve(path.dirname(file), name);
    const resolved = ['', '.ts', '.tsx'].map((ext) => target + ext).find((p) => fs.existsSync(p));
    return load(path.relative(root, resolved), mocks);
  };
  vm.runInThisContext(`(function(require,module,exports){${source}\n})`, { filename: file })(
    localRequire, loadedModule, loadedModule.exports);
  return loadedModule.exports;
}

// ---------- API ----------

test('통계 API는 api-spec §14 경로와 쿼리를 그대로 보낸다', async () => {
  const calls = [];
  const api = { get: async (...args) => { calls.push(args); return { ok: true }; } };
  const mod = load('api/adminStatsApi.ts', { './client': { __esModule: true, default: api } });

  await mod.getStatusCounts({ date: '2026-10-16', spaceId: '3' });
  await mod.getStatusCounts({ date: '2026-10-16' });
  await mod.getSpaceRanking({ period: 'DAILY', date: '2026-10-15', metric: 'SLOT_COUNT', location: '판교', limit: 5 });
  await mod.getLocationRanking({ period: 'WEEKLY', date: '2026-10-07', metric: 'NET_REVENUE', limit: 3 });

  assert.deepEqual(calls, [
    ['/admin/stats/status-counts', { query: { date: '2026-10-16', spaceId: '3' } }],
    ['/admin/stats/status-counts', { query: { date: '2026-10-16', spaceId: undefined } }],
    ['/admin/stats/space-ranking', { query: { period: 'DAILY', date: '2026-10-15', metric: 'SLOT_COUNT', location: '판교', limit: 5 } }],
    ['/admin/stats/location-ranking', { query: { period: 'WEEKLY', date: '2026-10-07', metric: 'NET_REVENUE', limit: 3 } }],
  ]);
});

// ---------- 날짜·상태 도우미 ----------

const ranking = load('utils/adminRanking.ts');

test('기본 조회 날짜는 가장 최근에 끝난 기간이다(KST 기준)', () => {
  const now = new Date('2026-10-15T16:30:00Z'); // KST 2026-10-16 01:30 (금)
  assert.equal(ranking.defaultRankingDate('DAILY', now), '2026-10-15');
  assert.equal(ranking.rankingPeriodStart('WEEKLY', ranking.defaultRankingDate('WEEKLY', now)), '2026-10-05');
  assert.equal(ranking.rankingPeriodStart('MONTHLY', ranking.defaultRankingDate('MONTHLY', now)), '2026-09-01');
});

test('기간 이동은 일간 1일, 주간 7일, 월간 1개월(말일 보정)이다', () => {
  assert.equal(ranking.shiftRankingDate('DAILY', '2026-03-01', -1), '2026-02-28');
  assert.equal(ranking.shiftRankingDate('WEEKLY', '2026-12-30', 1), '2027-01-06');
  assert.equal(ranking.shiftRankingDate('MONTHLY', '2026-01-31', 1), '2026-02-01');
  assert.equal(ranking.shiftRankingDate('MONTHLY', '2026-03-31', -1), '2026-02-01');
});

test('현재 기간까지만 볼 수 있고 미래 기간으로는 못 간다', () => {
  const now = new Date('2026-10-16T01:00:00Z'); // KST 금요일
  assert.equal(ranking.canViewRankingDate('DAILY', '2026-10-16', now), true);
  assert.equal(ranking.canViewRankingDate('DAILY', '2026-10-17', now), false);
  assert.equal(ranking.canViewRankingDate('WEEKLY', '2026-10-18', now), true);
  assert.equal(ranking.canViewRankingDate('WEEKLY', '2026-10-19', now), false);
  assert.equal(ranking.canViewRankingDate('MONTHLY', '2026-10-31', now), true);
  assert.equal(ranking.canViewRankingDate('MONTHLY', '2026-11-01', now), false);
});

test('기간 라벨: 일간은 날짜, 주간은 월~일 범위, 월간은 연월', () => {
  assert.equal(ranking.rankingPeriodLabel('DAILY', '2026-10-15', '2026-10-16'), '2026-10-15');
  assert.equal(ranking.rankingPeriodLabel('WEEKLY', '2026-10-12', '2026-10-19'), '2026-10-12 ~ 2026-10-18');
  assert.equal(ranking.rankingPeriodLabel('MONTHLY', '2026-10-01', '2026-11-01'), '2026년 10월');
});

test('순위 변동 표시: 상승·하락·동일·직전 순위 없음', () => {
  assert.deepEqual(ranking.rankChange(1, 3), { kind: 'up', label: '▲2' });
  assert.deepEqual(ranking.rankChange(4, 2), { kind: 'down', label: '▼2' });
  assert.deepEqual(ranking.rankChange(2, 2), { kind: 'same', label: '–' });
  assert.deepEqual(ranking.rankChange(2, null), { kind: 'none', label: '–' });
});

test('상태별 안내 문구: 집계 중, 기록 없음, 실적 없음', () => {
  assert.match(ranking.rankingStatusNotice({ status: 'PENDING', confirmAt: '2026-10-17T00:00:00', items: [] }), /집계 중입니다.*2026-10-17 00:00/);
  assert.match(ranking.rankingStatusNotice({ status: 'UNAVAILABLE', items: [] }), /확정된 기록이 없습니다/);
  assert.match(ranking.rankingStatusNotice({ status: 'CONFIRMED', items: [] }), /집계된 실적이 없습니다/);
  assert.equal(ranking.rankingStatusNotice({ status: 'CONFIRMED', items: [{}] }), null);
});

test('화면 유의사항은 계획 문서의 문구 5개를 담는다', () => {
  assert.equal(ranking.RANKING_NOTES.length, 5);
  assert.match(ranking.RANKING_NOTES[0], /해당 기간이 끝난 뒤 24:00/);
  assert.match(ranking.RANKING_NOTES[3], /공간 ID가 작은 순서/);
  assert.match(ranking.RANKING_NOTES[4], /위약금은 순매출에 포함/);
});

// ---------- 지역 선택 ----------

test('지역은 판교·강남·하남만 고를 수 있고 기존 값이 다르면 변경을 요구한다', () => {
  const region = load('utils/spaceRegion.ts');
  assert.deepEqual(region.SPACE_REGIONS, ['판교', '강남', '하남']);
  assert.equal(region.isSpaceRegion('판교'), true);
  assert.equal(region.isSpaceRegion('서울시 강남구'), false);
  assert.deepEqual(region.regionOptions('').map(o => o.value), ['', '판교', '강남', '하남']);
  // 기존 데이터가 세 값이 아니면 그 값을 선택지에 남겨 사용자가 무엇이 바뀌는지 보게 한다
  assert.deepEqual(region.regionOptions('서울시 강남구').map(o => o.value), ['서울시 강남구', '판교', '강남', '하남']);
});

test('공간 폼은 위치를 선택형으로 받고 지원하지 않는 값은 제출할 수 없다', () => {
  const useStateCalls = [];
  const mod = load('components/space/SpaceForm.tsx', {
    react: { ...React, useState: (init) => { useStateCalls.push(init); return React.useState(init); } },
    '../common/ErrorMessage': { __esModule: true, default: () => null },
    './OperatingHoursField': { __esModule: true, default: () => null },
    './SpacePhoto': { __esModule: true, default: () => null },
  });
  const html = renderToStaticMarkup(React.createElement(mod.default, { error: null, onSubmit() {}, onCancel() {} }));
  assert.match(html, /<select[^>]*>[\s\S]*<option value="판교"[\s\S]*<option value="강남"[\s\S]*<option value="하남"/);
  assert.doesNotMatch(html, /label[^>]*>[^<]*<span>위치<\/span><input/);
  assert.equal(useStateCalls.length > 0, true);
});

// ---------- 랭킹 섹션 ----------

function renderSection(data, { loading = false, error = null } = {}) {
  const mod = load('components/admin/SpaceRankingSection.tsx', {
    '../../hooks/useApi': { useAsync: () => ({ data, loading, error, run: async () => data }) },
    '../../api/adminStatsApi': { getSpaceRanking: async () => data, getLocationRanking: async () => data },
    '../common/ErrorMessage': { __esModule: true, default: () => null },
  });
  return renderToStaticMarkup(React.createElement(mod.default));
}

const spaceItem = (rank, previousRank, spaceId, name, extra = {}) => ({
  rank, previousRank, spaceId, spaceName: name, location: '판교', spaceStatus: 'ACTIVE',
  netRevenue: 12000, reservationCount: 3, slotCount: 7, ...extra });

test('확정된 공간 랭킹은 순위·변동·세 지표·확정 시각을 보여 준다', () => {
  const html = renderSection({
    period: 'DAILY', periodStart: '2026-10-15', periodEnd: '2026-10-16', metric: 'NET_REVENUE',
    status: 'CONFIRMED', confirmAt: null, computedAt: '2026-10-16T00:01:00',
    items: [spaceItem(1, 3, 10, '판교 A룸'), spaceItem(2, null, 11, '폐쇄된 룸', { spaceStatus: 'INACTIVE' })],
  });
  assert.match(html, /판교 A룸/);
  assert.match(html, /▲2/);
  assert.match(html, /12,000/);
  assert.match(html, /3건/);
  assert.match(html, /7칸|7슬롯|7/);
  assert.match(html, /운영 중지/);
  assert.match(html, /2026-10-16 00:01 확정/);
});

test('집계 중(PENDING)이면 순위 표 대신 안내 문구를 보여 준다', () => {
  const html = renderSection({
    period: 'DAILY', periodStart: '2026-10-16', periodEnd: '2026-10-17', metric: 'NET_REVENUE',
    status: 'PENDING', confirmAt: '2026-10-17T00:00:00', computedAt: null, items: [],
  });
  assert.match(html, /집계 중입니다/);
  assert.doesNotMatch(html, /<table/);
});

test('기록 없음(UNAVAILABLE)과 빈 기간을 구분해 안내한다', () => {
  const base = { period: 'DAILY', periodStart: '2026-08-01', periodEnd: '2026-08-02', metric: 'NET_REVENUE', confirmAt: null, computedAt: null, items: [] };
  assert.match(renderSection({ ...base, status: 'UNAVAILABLE' }), /확정된 기록이 없습니다/);
  assert.match(renderSection({ ...base, status: 'CONFIRMED' }), /집계된 실적이 없습니다/);
});

test('유의사항 5개가 항상 표시된다', () => {
  const html = renderSection(null, { loading: true });
  for (const note of ranking.RANKING_NOTES) assert.ok(html.includes(note), note);
});

// ---------- 대시보드 상태별 건수 ----------

test('대시보드 건수는 서버가 준 상태별 건수에서 표시 대상 4개만 꺼낸다', () => {
  const dashboard = load('utils/adminDashboard.ts');
  const counts = { HELD: 2, EXPIRED: 9, CONFIRMED: 3, IN_USE: 1, COMPLETED: 4, CANCELLED: 8, NO_SHOW: 7 };
  assert.deepEqual(dashboard.pickDashboardCounts(counts), { HELD: 2, CONFIRMED: 3, IN_USE: 1, COMPLETED: 4 });
  assert.deepEqual(dashboard.pickDashboardCounts(null), { HELD: 0, CONFIRMED: 0, IN_USE: 0, COMPLETED: 0 });
  assert.equal(dashboard.countDashboardStatuses, undefined, '브라우저에서 예약을 세던 함수는 제거되었다');
});

test('운영 대시보드 건수 타일은 서버가 센 값을 보여 주고 아직 없으면 –를 보여 준다', () => {
  const today = '2026-10-16';
  const dashboardData = { date: today, spaces: [], fetchedAt: 0, reservations: [] };
  const render = (countsData) => {
    let call = 0;
    const mod = load('components/admin/AdminDashboard.tsx', {
      '../../hooks/useAuth': { useAuth: () => ({ isAdmin: true, member: { memberId: 1 }, initializing: false }) },
      '../../hooks/useApi': { useAsync: () => (call++ % 2 === 0
        ? { data: dashboardData, loading: false, error: null, run: async () => dashboardData }
        : { data: countsData, loading: false, error: null, run: async () => countsData }) },
      '../../api/adminDashboardApi': { getAdminDashboard: async () => dashboardData },
      '../../api/adminStatsApi': { getStatusCounts: async () => countsData },
      'next/link': { __esModule: true, default: ({ href, children, ...p }) => React.createElement('a', { href, ...p }, children) },
      '../common/ErrorMessage': { __esModule: true, default: () => null },
      '../../utils/adminDashboard': { ...load('utils/adminDashboard.ts'), seoulDate: () => today },
    });
    return renderToStaticMarkup(React.createElement(mod.default, { compact: false }));
  };
  const counts = { HELD: 2, EXPIRED: 9, CONFIRMED: 3, IN_USE: 1, COMPLETED: 4, CANCELLED: 8, NO_SHOW: 7 };
  const withCounts = render({ date: today, spaceId: '', counts });
  assert.match(withCounts, /결제 대기<\/span><strong>2/);
  assert.match(withCounts, /결제 완료<\/span><strong>3/);
  assert.match(withCounts, /사용 중<\/span><strong>1/);
  assert.match(withCounts, /이용 완료<\/span><strong>4/);
  const withoutCounts = render(null);
  assert.match(withoutCounts, /결제 대기<\/span><strong>–/);
});
