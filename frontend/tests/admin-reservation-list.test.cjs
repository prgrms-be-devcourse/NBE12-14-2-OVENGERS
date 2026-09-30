/* eslint-disable @typescript-eslint/no-require-imports -- Node test harness loads actual TS modules in CommonJS isolation. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const root = path.resolve(__dirname, '../src');

function load(relative, mocks = {}) {
  const file = path.resolve(root, relative);
  const source = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
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
    localRequire,
    loadedModule,
    loadedModule.exports,
  );
  return loadedModule.exports;
}

test('assertPageResponse accepts valid PageResponse and rejects malformed responses including Spring Data Page', () => {
  const { assertPageResponse } = load('api/adminReservationApi.ts', {
    './client': { default: {} },
  });

  const validPage = {
    content: [{ reservationId: 1, spaceName: '대회의실' }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };
  assert.deepEqual(assertPageResponse(validPage), validPage);

  // 1. Spring Data Page (using 'number' instead of 'page') must be rejected
  const springDataPage = {
    content: [{ reservationId: 1 }],
    number: 0,
    size: 20,
    totalPages: 1,
    totalElements: 1,
  };
  assert.throws(() => assertPageResponse(springDataPage), /올바르지 않은 페이지 응답 형식/);

  // 2. Missing or invalid page
  assert.throws(() => assertPageResponse({ content: [], size: 20, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: -1, size: 20, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: '0', size: 20, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: 1.5, size: 20, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);

  // 3. Missing or invalid size
  assert.throws(() => assertPageResponse({ content: [], page: 0, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: 0, size: 0, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: 0, size: -10, totalPages: 1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);

  // 4. Missing or invalid totalPages / totalElements
  assert.throws(() => assertPageResponse({ content: [], page: 0, size: 20, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: 0, size: 20, totalPages: -1, totalElements: 0 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: 0, size: 20, totalPages: 1 }), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse({ content: [], page: 0, size: 20, totalPages: 1, totalElements: -5 }), /올바르지 않은 페이지 응답 형식/);

  // 5. Invalid content
  assert.throws(() => assertPageResponse({ content: 'invalid', page: 0, size: 20, totalPages: 1, totalElements: 1 }), /올바르지 않은 페이지 응답 형식/);

  // 6. Null or undefined or non-object
  assert.throws(() => assertPageResponse(null), /올바르지 않은 페이지 응답 형식/);
  assert.throws(() => assertPageResponse('string'), /올바르지 않은 페이지 응답 형식/);
});

test('getAdminReservations normalizes empty filter parameters before sending query', async () => {
  let capturedQuery = null;
  const mockApi = {
    get: async (_url, options) => {
      capturedQuery = options?.query;
      return {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      };
    },
  };
  mockApi.default = mockApi;

  const { getAdminReservations } = load('api/adminReservationApi.ts', {
    './client': mockApi,
  });

  // 1. Empty filter values must be excluded
  await getAdminReservations({
    page: 0,
    size: 20,
    date: '   ',
    spaceId: '',
    status: '',
  });
  assert.deepEqual(capturedQuery, { page: 0, size: 20 });

  // 2. Invalid spaceId <= 0 must be excluded
  await getAdminReservations({
    page: 1,
    size: 10,
    spaceId: 0,
  });
  assert.deepEqual(capturedQuery, { page: 1, size: 10 });

  await getAdminReservations({
    page: 1,
    size: 10,
    spaceId: -5,
  });
  assert.deepEqual(capturedQuery, { page: 1, size: 10 });

  // 3. Valid filters must be included
  await getAdminReservations({
    page: 0,
    size: 20,
    date: '2026-09-30',
    spaceId: '3',
    status: 'CONFIRMED',
  });
  assert.deepEqual(capturedQuery, {
    page: 0,
    size: 20,
    date: '2026-09-30',
    spaceId: 3,
    status: 'CONFIRMED',
  });
});

test('RESERVATION_STATUS_OPTIONS contains only valid 7 reservation statuses and no WITHDRAWN', () => {
  const enums = load('constants/enums.ts');
  const options = enums.RESERVATION_STATUS_OPTIONS;

  // Must not have WITHDRAWN
  const hasWithdrawn = options.some((opt) => opt.value === 'WITHDRAWN' || opt.label.includes('탈퇴'));
  assert.equal(hasWithdrawn, false, 'RESERVATION_STATUS_OPTIONS must not contain member withdrawal option');

  // Must contain total 8 options (empty + 7 reservation statuses: HELD, CONFIRMED, IN_USE, COMPLETED, CANCELLED, NO_SHOW, EXPIRED)
  assert.equal(options.length, 8);
  const values = options.map((opt) => opt.value);
  assert.deepEqual(values, [
    '',
    enums.RESERVATION_STATUS.HELD,
    enums.RESERVATION_STATUS.CONFIRMED,
    enums.RESERVATION_STATUS.IN_USE,
    enums.RESERVATION_STATUS.COMPLETED,
    enums.RESERVATION_STATUS.CANCELLED,
    enums.RESERVATION_STATUS.NO_SHOW,
    enums.RESERVATION_STATUS.EXPIRED,
  ]);
});

test('date and time formatting correctly slices ISO timestamp directly from AdminReservationListPage', () => {
  const dateUtil = load('utils/date.ts');
  const pageModule = load('views/admin/AdminReservationListPage.tsx', {
    '../../components/reservation/ReservationFilter': () => null,
    '../../components/common/Pagination': () => null,
    '../../components/common/LoadingSpinner': () => null,
    '../../components/common/ErrorMessage': () => null,
    '../../components/common/EmptyState': () => null,
    '../../components/reservation/ReservationStatusBadge': () => null,
    '../../hooks/usePagination': () => ({ page: 0, size: 20, setPage: () => {} }),
    '../../hooks/useApi': () => ({ data: null, loading: false, error: null, run: () => {} }),
    '../../api/adminReservationApi': { getAdminReservations: async () => ({}) },
    '../../api/adminSpaceApi': { getAllAdminSpaces: async () => [] },
  });

  const { extractDate, extractTime } = pageModule;
  assert.equal(typeof extractDate, 'function');
  assert.equal(typeof extractTime, 'function');

  const startIso = '2026-09-30T10:00:00';
  const endIso = '2026-09-30T12:00:00';

  const datePart = extractDate(startIso);
  const startTimePart = extractTime(startIso);
  const endTimePart = extractTime(endIso);

  assert.equal(datePart, '2026-09-30');
  assert.equal(startTimePart, '10:00');
  assert.equal(endTimePart, '12:00');

  // Ensure formatTimeRange formats correctly
  const timeRange = dateUtil.formatTimeRange(startTimePart, endTimePart);
  assert.equal(timeRange, '10:00 ~ 12:00');

  // Ensure formatDateLabel formats correctly
  const dateLabel = dateUtil.formatDateLabel(datePart);
  assert.ok(dateLabel.includes('2026년 9월 30일'));
});

test('getAllAdminSpaces collects across multiple pages (>100 items), handles failure, and supports retry', async () => {
  // Simulate 3 pages with total 120 spaces, containing ACTIVE and INACTIVE spaces
  const pageData = [
    {
      page: 0,
      size: 50,
      totalPages: 3,
      totalElements: 120,
      content: Array.from({ length: 50 }, (_, i) => ({ id: i + 1, name: `공간 ${i + 1}`, status: i % 2 === 0 ? 'ACTIVE' : 'INACTIVE' })),
    },
    {
      page: 1,
      size: 50,
      totalPages: 3,
      totalElements: 120,
      content: Array.from({ length: 50 }, (_, i) => ({ id: i + 51, name: `공간 ${i + 51}`, status: 'INACTIVE' })),
    },
    {
      page: 2,
      size: 50,
      totalPages: 3,
      totalElements: 120,
      content: Array.from({ length: 20 }, (_, i) => ({ id: i + 101, name: `공간 ${i + 101}`, status: 'ACTIVE' })),
    },
  ];

  let failPage1Once = true;
  let calls = 0;

  const mockApi = {
    get: async (_url, options) => {
      calls += 1;
      const reqPage = options?.query?.page ?? 0;
      if (failPage1Once && reqPage === 1) {
        throw new Error('네트워크 일시 오류');
      }
      return pageData[reqPage];
    },
  };
  mockApi.default = mockApi;

  const { getAllAdminSpaces } = load('api/adminSpaceApi.ts', {
    './client': mockApi,
  });

  // 1. Initial attempt fails when fetching page 1
  await assert.rejects(() => getAllAdminSpaces(50), /네트워크 일시 오류/);

  // 2. Retry succeeds and collects all 120 spaces
  failPage1Once = false;
  const spaces = await getAllAdminSpaces(50);
  assert.equal(spaces.length, 120);
  assert.equal(spaces[0].id, 1);
  assert.equal(spaces[49].id, 50);
  assert.equal(spaces[50].id, 51);
  assert.equal(spaces[119].id, 120);

  // Both ACTIVE and INACTIVE spaces are collected
  const inactiveCount = spaces.filter((s) => s.status === 'INACTIVE').length;
  assert.ok(inactiveCount > 0, 'Must include INACTIVE spaces for admin filter options');
  assert.ok(calls >= 4, 'Must make initial failing call and 3 pagination calls on retry');
});
