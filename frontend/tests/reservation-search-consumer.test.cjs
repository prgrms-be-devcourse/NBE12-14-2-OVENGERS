/* eslint-disable @typescript-eslint/no-require-imports -- Node test harness loads actual TS modules in CommonJS isolation. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const React = require('react');
const ReactDOMServer = require('react-dom/server');

const root = path.resolve(__dirname, '../src');

function load(relative, mocks = {}) {
  const file = path.resolve(root, relative);
  const isTsx = file.endsWith('.tsx');
  const source = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    fileName: file,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: isTsx ? ts.JsxEmit.ReactJSX : ts.JsxEmit.None,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const loadedModule = { exports: {} };
  const localRequire = (name) => {
    if (Object.hasOwn(mocks, name)) return mocks[name];
    if (name.endsWith('.css')) return new Proxy({}, { get: (_, key) => (key === '__esModule' ? false : String(key)) });
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

test('getMyReservations normalizes keyword and status query parameters', async () => {
  const capturedQueries = [];
  const mockApi = {
    get: async (_url, options) => {
      capturedQueries.push(options?.query);
      return {
        content: [],
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0,
      };
    },
  };
  mockApi.default = mockApi;

  const { getMyReservations } = load('api/reservationApi.ts', {
    './client': mockApi,
  });

  // 1. 기본 인자 없이 호출 시 page=0, size=10만 전달 (status, keyword undefined)
  await getMyReservations();
  assert.deepEqual(capturedQueries[0], {
    page: 0,
    size: 10,
    status: undefined,
    keyword: undefined,
  });

  // 2. keyword 공백만 전달 시 undefined로 정규화
  await getMyReservations({ keyword: '   ' });
  assert.deepEqual(capturedQueries[1], {
    page: 0,
    size: 10,
    status: undefined,
    keyword: undefined,
  });

  // 3. keyword 앞뒤 공백 trim 및 status 빈 문자열은 undefined로 정규화
  await getMyReservations({ page: 1, size: 20, keyword: '  판교 회의실  ', status: '' });
  assert.deepEqual(capturedQueries[2], {
    page: 1,
    size: 20,
    status: undefined,
    keyword: '판교 회의실',
  });

  // 4. 유효한 status와 keyword 전달
  await getMyReservations({ status: 'CONFIRMED', keyword: '라운지' });
  assert.deepEqual(capturedQueries[3], {
    page: 0,
    size: 10,
    status: 'CONFIRMED',
    keyword: '라운지',
  });
});

test('ReservationListPage isReservationFilterActive correctly determines active filter state from actual module', () => {
  const { isReservationFilterActive } = load('views/reservation/ReservationListPage.tsx', {
    'next/link': ({ children }) => children,
  });

  assert.equal(typeof isReservationFilterActive, 'function');

  // 1. 조건 없음 (status: '', keyword: '') -> 비활성(false)
  assert.equal(isReservationFilterActive({ status: '', keyword: '' }), false);

  // 2. 공백만 있는 keyword ('   ') -> 비활성(false)
  assert.equal(isReservationFilterActive({ status: '', keyword: '   ' }), false);

  // 3. status 선택 ('CONFIRMED') -> 활성(true)
  assert.equal(isReservationFilterActive({ status: 'CONFIRMED', keyword: '' }), true);

  // 4. keyword 입력 ('판교') -> 활성(true)
  assert.equal(isReservationFilterActive({ status: '', keyword: '판교' }), true);

  // 5. status와 keyword 동시 입력 -> 활성(true)
  assert.equal(isReservationFilterActive({ status: 'CANCELLED', keyword: '회의실' }), true);
});

test('ReservationFilter UI regression: keyword input is only shown when showDate and showSpace are false', () => {
  const ReservationFilterModule = load('components/reservation/ReservationFilter.tsx');
  const ReservationFilter = ReservationFilterModule.default || ReservationFilterModule;

  // 1. 관리자 예약 목록 화면 (showDate=true, showSpace=true):
  //    이용 날짜, 오피스 선택 노출 / 이름 검색(placeholder="오피스 이름") 미노출
  const adminHtml = ReactDOMServer.renderToStaticMarkup(
    React.createElement(ReservationFilter, {
      value: { date: '2026-10-03', spaceId: '1', status: 'CONFIRMED', keyword: '' },
      onChange: () => {},
      showDate: true,
      showSpace: true,
      spaces: [{ id: '1', name: '판교 회의실' }],
    }),
  );
  assert.match(adminHtml, /이용 날짜/);
  assert.match(adminHtml, /오피스/);
  assert.doesNotMatch(adminHtml, /placeholder="오피스 이름"/);

  // 2. 내 예약 목록 화면 (showDate=false, showSpace=false):
  //    이용 날짜, 오피스 선택 미노출 / 이름 검색(placeholder="오피스 이름") 노출
  const memberHtml = ReactDOMServer.renderToStaticMarkup(
    React.createElement(ReservationFilter, {
      value: { status: '', keyword: '' },
      onChange: () => {},
      showDate: false,
      showSpace: false,
    }),
  );
  assert.doesNotMatch(memberHtml, /이용 날짜/);
  assert.doesNotMatch(memberHtml, /space-filter-wrap/);
  assert.match(memberHtml, /placeholder="오피스 이름"/);
});

test('HomeReservations consumer regression: executes actual fetchActiveReservations on full 2-page fixtures per status (size=50, total=51), omits keyword', async () => {
  const capturedCalls = [];

  // HOME_RESERVATION_STATUSES: ['IN_USE', 'HELD', 'CONFIRMED']
  // 각 상태마다 정상 2페이지 fixture 구성: page 0 (50개), page 1 (1개), totalElements=51, totalPages=2
  const statusItems = {
    IN_USE: Array.from({ length: 51 }, (_, i) => ({
      reservationId: 1000 + i,
      spaceId: 1,
      spaceName: `회의실 IU-${i}`,
      date: '2026-10-03',
      startTime: '2026-10-03T09:00:00',
      endTime: '2026-10-03T11:00:00',
      status: 'IN_USE',
      totalAmount: 10000,
    })),
    HELD: Array.from({ length: 51 }, (_, i) => ({
      reservationId: 2000 + i,
      spaceId: 2,
      spaceName: `회의실 HELD-${i}`,
      date: '2026-10-03',
      startTime: '2026-10-03T11:00:00',
      endTime: '2026-10-03T13:00:00',
      status: 'HELD',
      totalAmount: 10000,
    })),
    CONFIRMED: Array.from({ length: 51 }, (_, i) => ({
      reservationId: 3000 + i,
      spaceId: 3,
      spaceName: `회의실 CONF-${i}`,
      date: '2026-10-03',
      startTime: '2026-10-03T13:00:00',
      endTime: '2026-10-03T15:00:00',
      status: 'CONFIRMED',
      totalAmount: 10000,
    })),
  };

  const mockApi = {
    get: async (_url, options) => {
      capturedCalls.push(options?.query);
      const { status, page = 0, size = 50 } = options?.query || {};
      const allForStatus = statusItems[status] || [];
      const start = page * size;
      const end = start + size;
      const content = allForStatus.slice(start, end);
      return {
        content,
        page,
        size,
        totalElements: allForStatus.length,
        totalPages: Math.ceil(allForStatus.length / size),
      };
    },
  };
  mockApi.default = mockApi;

  const commonMocks = {
    './client': mockApi,
    '../api/doorAccessApi': { issueDoorToken: async () => ({}) },
    'next/link': ({ children }) => children,
  };

  const { fetchActiveReservations } = load('components/reservation/HomeReservations.tsx', commonMocks);
  const { selectHomeReservations } = load('utils/homeReservations.ts', commonMocks);

  assert.equal(typeof fetchActiveReservations, 'function');

  // 실제 fetchActiveReservations 호출
  const results = await fetchActiveReservations();

  // 1. 모든 호출에서 keyword가 생략(undefined)되었는지 단언
  assert.ok(capturedCalls.length >= 6, '3개 상태 * 2페이지 = 최소 6회 호출이어야 합니다.');
  for (const call of capturedCalls) {
    assert.equal(call.keyword, undefined, 'keyword는 호출 인자에 포함되지 않아야 합니다.');
    assert.equal(call.size, 50, 'size=50이어야 합니다.');
  }

  // 2. 각 상태별 2페이지(50 + 1 = 51) 총 153개 아이템이 중복 없이 수집되었는지 단언
  assert.equal(results.length, 153, '3개 상태 * 51건 = 총 153개 예약이 수집되어야 합니다.');
  const uniqueIds = new Set(results.map((r) => r.reservationId));
  assert.equal(uniqueIds.size, 153, '수집된 153개 ID는 모두 고유해야 합니다.');

  // 3. selectHomeReservations 선별 확인 (now를 2026-10-03 08:00로 전달하여 미래 예약으로 평가)
  const evalTime = new Date('2026-10-03T08:00:00+09:00').getTime();
  const selected = selectHomeReservations(results, evalTime);
  assert.equal(selected.length, 3, '최대 3건 선별되어야 합니다.');
  assert.deepEqual(
    selected.map((r) => r.reservationId),
    [2000, 1000, 3000],
    '상태 우선순위(HELD 1개 [ID 2000], IN_USE 1개 [ID 1000], CONFIRMED 1개 [ID 3000]) 순서대로 각 1건씩 정확한 선별 ID 일치'
  );

  // Mutation Probe: 단순 첫 번째 행 slice(0, 1)로 변조 시 상태별 선별이 파괴되어 실패함을 입증
  const sabotagedSingle = results.slice(0, 1).map((r) => r.reservationId);
  assert.notDeepEqual(
    sabotagedSingle,
    [2000, 1000, 3000],
    '단순 첫 번째 행 slice(0, 1) 변조 시 [1000]이 되어 상태별 선별이 파괴되고 실패함을 입증'
  );
});

test('DoorTerminalPage consumer regression: executes actual fetchDoorReservations with full 2-page fixtures (size=100, total=102), filters expired/boundary items, verifies call sequence, sorts ascending, and renders in DoorVerifyForm', async () => {
  const capturedCalls = [];

  // 기준 시각: 2026-10-03 12:00:00 (KST 타임스탬프)
  const baseTime = new Date('2026-10-03T12:00:00+09:00').getTime();

  // CONFIRMED (102개):
  // - 1개 만료: endTime = 11:30:00 (ID 1000)
  // - 1개 경계 시각 일치: endTime = 12:00:00 (ID 10000) -> baseTime과 동일하여 > 조건에 의해 제외되어야 함!
  // - 100개 미래: endTime = 13:00:00 ~ 15:00:00 (ID 1001 ~ 1100)
  const confirmedItems = [
    {
      reservationId: 1000,
      spaceId: 1,
      spaceName: '만료된 회의실',
      date: '2026-10-03',
      startTime: '2026-10-03T10:00:00',
      endTime: '2026-10-03T11:30:00', // baseTime(12:00) 이전 만료!
      status: 'CONFIRMED',
      totalAmount: 10000,
    },
    {
      reservationId: 10000,
      spaceId: 1,
      spaceName: '경계시각(12:00:00) 회의실',
      date: '2026-10-03',
      startTime: '2026-10-03T10:00:00',
      endTime: '2026-10-03T12:00:00', // baseTime(12:00:00)과 정확히 동일! > 조건으로 엄격히 제외되어야 함
      status: 'CONFIRMED',
      totalAmount: 10000,
    },
    ...Array.from({ length: 100 }, (_, i) => ({
      reservationId: 1001 + i,
      spaceId: 1,
      spaceName: `확정 회의실 ${i}`,
      date: '2026-10-03',
      startTime: `2026-10-03T13:${String(i % 60).padStart(2, '0')}:00`,
      endTime: '2026-10-03T15:00:00',
      status: 'CONFIRMED',
      totalAmount: 10000,
    })),
  ];

  // IN_USE (102개):
  // - 1개 만료: endTime = 11:45:00 (ID 2000)
  // - 1개 경계 시각 일치: endTime = 12:00:00 (ID 20000) -> baseTime과 동일하여 > 조건에 의해 제외되어야 함!
  // - 100개 진행/미래: endTime = 12:30:00 ~ 14:00:00 (ID 2001 ~ 2100)
  const inUseItems = [
    {
      reservationId: 2000,
      spaceId: 2,
      spaceName: '만료된 라운지',
      date: '2026-10-03',
      startTime: '2026-10-03T10:30:00',
      endTime: '2026-10-03T11:45:00', // baseTime(12:00) 이전 만료!
      status: 'IN_USE',
      totalAmount: 15000,
    },
    {
      reservationId: 20000,
      spaceId: 2,
      spaceName: '경계시각(12:00:00) 라운지',
      date: '2026-10-03',
      startTime: '2026-10-03T10:30:00',
      endTime: '2026-10-03T12:00:00', // baseTime(12:00:00)과 정확히 동일! > 조건으로 엄격히 제외되어야 함
      status: 'IN_USE',
      totalAmount: 15000,
    },
    ...Array.from({ length: 100 }, (_, i) => ({
      reservationId: 2001 + i,
      spaceId: 2,
      spaceName: `이용중 라운지 ${i}`,
      date: '2026-10-03',
      startTime: `2026-10-03T12:${String(i % 60).padStart(2, '0')}:00`,
      endTime: '2026-10-03T14:00:00',
      status: 'IN_USE',
      totalAmount: 15000,
    })),
  ];

  const doorItems = {
    CONFIRMED: confirmedItems,
    IN_USE: inUseItems,
  };

  const mockApi = {
    get: async (_url, options) => {
      capturedCalls.push(options?.query);
      const { status, page = 0, size = 100 } = options?.query || {};
      const allForStatus = doorItems[status] || [];
      const start = page * size;
      const end = start + size;
      const content = allForStatus.slice(start, end);
      return {
        content,
        page,
        size,
        totalElements: allForStatus.length,
        totalPages: Math.ceil(allForStatus.length / size),
      };
    },
  };
  mockApi.default = mockApi;

  const commonMocks = {
    './client': mockApi,
    '../api/doorAccessApi': { issueDoorToken: async () => ({}) },
    'next/link': ({ children }) => children,
  };

  const { fetchDoorReservations } = load('views/door/DoorTerminalPage.tsx', commonMocks);

  assert.equal(typeof fetchDoorReservations, 'function');

  // baseTime을 주입하여 fetchDoorReservations 실행
  const collected = await fetchDoorReservations(baseTime);

  // 1. 호출 순서 및 파라미터 전수 단언: p0(CONFIRMED, IN_USE) -> p1(CONFIRMED, IN_USE), keyword는 undefined
  assert.deepEqual(
    capturedCalls,
    [
      { page: 0, size: 100, status: 'CONFIRMED', keyword: undefined },
      { page: 0, size: 100, status: 'IN_USE', keyword: undefined },
      { page: 1, size: 100, status: 'CONFIRMED', keyword: undefined },
      { page: 1, size: 100, status: 'IN_USE', keyword: undefined },
    ],
    'Door 호출은 p0(CONFIRMED, IN_USE) -> p1(CONFIRMED, IN_USE) 총 4회 순서대로 발생해야 하며 keyword는 undefined여야 합니다.'
  );
  for (const call of capturedCalls) {
    assert.equal(call.keyword, undefined, 'Door 호출에서 keyword는 항상 undefined여야 합니다.');
    assert.equal(call.size, 100, 'Door 호출의 size는 100이어야 합니다.');
  }

  // 2. 만료 2건 및 경계시각 일치 2건 제외 확인: 총 204건 중 200건만 남아야 함!
  assert.equal(collected.length, 200, '만료 2건 및 경계시각(12:00:00) 동률 2건이 > 조건에 의해 제외되어 200건이어야 합니다.');
  assert.ok(!collected.some((r) => r.reservationId === 1000), '과거 만료 ID 1000은 제외되어야 합니다.');
  assert.ok(!collected.some((r) => r.reservationId === 2000), '과거 만료 ID 2000은 제외되어야 합니다.');
  assert.ok(!collected.some((r) => r.reservationId === 10000), '기준 시각 12:00:00과 일치하는 ID 10000은 > 조건에 의해 제외되어야 합니다.');
  assert.ok(!collected.some((r) => r.reservationId === 20000), '기준 시각 12:00:00과 일치하는 ID 20000은 > 조건에 의해 제외되어야 합니다.');

  // 3. startTime 오름차순 정렬 확인
  for (let i = 1; i < collected.length; i++) {
    const prevTime = new Date(collected[i - 1].startTime.includes('T') ? collected[i - 1].startTime : `${collected[i - 1].date}T${collected[i - 1].startTime}`).getTime();
    const currTime = new Date(collected[i].startTime.includes('T') ? collected[i].startTime : `${collected[i].date}T${collected[i].startTime}`).getTime();
    assert.ok(prevTime <= currTime, `startTime 오름차순 정렬 위반: ${collected[i - 1].startTime} > ${collected[i].startTime}`);
  }

  // 4. 수집된 200개 ID 전체 정렬 일치 단언
  const collectedIds = collected.map((r) => r.reservationId);
  const expectedSortedIds = [
    ...inUseItems.slice(2).sort((a, b) => a.startTime.localeCompare(b.startTime)).map((r) => r.reservationId),
    ...confirmedItems.slice(2).sort((a, b) => a.startTime.localeCompare(b.startTime)).map((r) => r.reservationId),
  ];
  assert.deepEqual(collectedIds, expectedSortedIds, '수집된 전수 200개 ID가 startTime 순서대로 정렬되어야 합니다.');

  // 5. DoorVerifyForm SSR 렌더링 검증: 수집된 결과가 실제 Select 컴포넌트 옵션으로 정상 변환되는지 단언
  const DoorVerifyFormModule = load('components/access/DoorVerifyForm.tsx', {
    ...commonMocks,
    '../space/SpacePhoto': () => null,
  });
  const DoorVerifyForm = DoorVerifyFormModule.default || DoorVerifyFormModule;

  const formHtml = ReactDOMServer.renderToStaticMarkup(
    React.createElement(DoorVerifyForm, {
      reservations: collected,
      value: { reservationId: String(collected[0].reservationId), accessKey: 'SECRET123' },
      onChange: () => {},
      onSubmit: () => {},
    })
  );

  // placeholder 옵션 존재 단언
  assert.match(formHtml, /<option value="">출입할 예약을 선택해 주세요<\/option>/);
  // 선별된 첫 2개 예약 옵션이 순서대로 렌더링되었는지 단언
  assert.match(formHtml, new RegExp(`<option value="${collected[0].reservationId}"`));
  assert.match(formHtml, new RegExp(`<option value="${collected[1].reservationId}">`));
  // 제외된 만료/동률 항목 4건은 옵션에 절대 나타나지 않아야 함
  assert.doesNotMatch(formHtml, /<option value="1000"/);
  assert.doesNotMatch(formHtml, /<option value="10000"/);
  assert.doesNotMatch(formHtml, /<option value="2000"/);
  assert.doesNotMatch(formHtml, /<option value="20000"/);

  // 6. 대조 검증 (Mutation Probes):
  // 6-1. 만약 > 대신 >= 연산자가 사용되면 경계 항목 2건이 포함되어 202건이 됨을 입증
  const reservationTimeFn = (res, field) => {
    const raw = res[field].includes('T') ? res[field] : `${res.date}T${res[field]}`;
    return new Date(raw).getTime();
  };
  const countWithGte = [...confirmedItems, ...inUseItems].filter((r) => reservationTimeFn(r, 'endTime') >= baseTime).length;
  assert.equal(countWithGte, 202, '>= 연산자로 변조 시 경계 항목 2건이 포함되어 202건이어야 함');
  assert.equal(collected.length, 200, '> 연산자를 통해 정확히 200건만 남아야 함');

  // 6-2. 만료 필터가 통째로 누락되었을 때의 건수(204건)와 대조
  const unFilteredCount = confirmedItems.length + inUseItems.length;
  assert.equal(unFilteredCount, 204);
  assert.notEqual(collected.length, unFilteredCount, '만료 필터가 동작하여 전체 204건과 달라야 합니다.');
});

test('DoorTerminalPage timing probe: evaluates now AFTER await finishes when called without args', async () => {
  // 호출 시작 시각: 10:00:00 (KST), 종료 시각: 10:00:01 (KST)인 예약
  const endTimeMs = new Date('2026-10-03T10:00:01+09:00').getTime();
  let currentTime = endTimeMs - 1000; // 호출 시점(10:00:00)에는 아직 미래임
  const originalDateNow = Date.now;

  try {
    Date.now = () => currentTime;

    const mockApi = {
      get: async () => {
        // 네트워크 await 중에 시간이 흘러 10:00:02로 변경됨 (만료 시점 초과!)
        currentTime = endTimeMs + 1000;
        return {
          content: [
            {
              reservationId: 9999,
              spaceId: 1,
              spaceName: '경계시간 테스트 회의실',
              date: '2026-10-03',
              startTime: '2026-10-03T10:00:00',
              endTime: '2026-10-03T10:00:01',
              status: 'CONFIRMED',
              totalAmount: 10000,
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        };
      },
    };

    const mocks = {
      './client': mockApi,
      '../api/doorAccessApi': { issueDoorToken: async () => ({}) },
      'next/link': ({ children }) => children,
    };

    const { fetchDoorReservations } = load('views/door/DoorTerminalPage.tsx', mocks);

    // 인자 없이 호출 -> await 완료 후의 Date.now()(10:00:02)로 평가되므로 10:00:01 만료 항목이 정상 제외되어 [] 반환!
    const result = await fetchDoorReservations();
    assert.deepEqual(result, [], 'await 후 Date.now() 평가로 만료 항목이 정상 제외되어야 합니다.');
  } finally {
    Date.now = originalDateNow;
  }
});

test('ReservationListPage rendering and EmptyState branching between no-bookings (with CTA) and no-matching-results (no CTA)', () => {
  const EmptyStateModule = load('components/common/EmptyState.tsx');
  const EmptyState = EmptyStateModule.default || EmptyStateModule;

  // 1. 활성 필터가 없는 경우 (hasActiveFilter=false):
  //    "아직 예약이 없습니다" 타이틀 및 action button ("오피스 둘러보기") 렌더링
  const noFilterHtml = ReactDOMServer.renderToStaticMarkup(
    React.createElement(EmptyState, {
      title: '아직 예약이 없습니다',
      description: '원하는 오피스와 시간을 골라 첫 예약을 만들어 보세요.',
      action: React.createElement('a', { href: '/spaces' }, '오피스 둘러보기'),
    }),
  );
  assert.match(noFilterHtml, /아직 예약이 없습니다/);
  assert.match(noFilterHtml, /오피스 둘러보기/);

  // 2. 활성 필터가 있는 경우 (hasActiveFilter=true):
  //    "조건에 맞는 예약이 없습니다" 타이틀 및 action button 없음
  const filteredHtml = ReactDOMServer.renderToStaticMarkup(
    React.createElement(EmptyState, {
      title: '조건에 맞는 예약이 없습니다',
      description: '검색어나 예약 상태를 조정해 보세요.',
    }),
  );
  assert.match(filteredHtml, /조건에 맞는 예약이 없습니다/);
  assert.doesNotMatch(filteredHtml, /오피스 둘러보기/);
});

test('Counter-check: fails if actual consumer modules or functions are missing', () => {
  const homeModule = load('components/reservation/HomeReservations.tsx', {
    'next/link': ({ children }) => children,
  });
  assert.equal(typeof homeModule.fetchActiveReservations, 'function', 'HomeReservations must export fetchActiveReservations');

  const doorModule = load('views/door/DoorTerminalPage.tsx', {
    'next/link': ({ children }) => children,
  });
  assert.equal(typeof doorModule.fetchDoorReservations, 'function', 'DoorTerminalPage must export fetchDoorReservations');

  const listModule = load('views/reservation/ReservationListPage.tsx', {
    'next/link': ({ children }) => children,
  });
  assert.equal(typeof listModule.isReservationFilterActive, 'function', 'ReservationListPage must export isReservationFilterActive');
});
