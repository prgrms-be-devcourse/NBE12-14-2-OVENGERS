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

// toDate()가 실행 환경의 로컬 시간대를 쓰므로 기준 시각도 같은 로컬 기준으로 만든다(UTC CI에서도 동일하게 동작).
const NOW = new Date(2026, 9, 15, 20, 10, 0);
const DATE = '2026-10-15';

function slots() {
  const slot = load('utils/slot.ts');
  // 19:00 지난 칸(비어 있음), 20:00 지난 칸(예약됨), 22:00 예약된 미래 칸, 22:30 비어 있는 미래 칸
  return slot.decorateSlots(
    [
      { startTime: '19:00', available: true },
      { startTime: '20:00', available: false },
      { startTime: '22:00', available: false },
      { startTime: '22:30', available: true },
    ],
    DATE,
    NOW,
  );
}

test('예약된 미래 칸과 지난 칸을 구분한다', () => {
  const wl = load('utils/waitlist.ts');
  const byTime = Object.fromEntries(slots().map((s) => [s.startTime, s]));

  assert.equal(wl.isReservedFutureSlot(byTime['22:00']), true);
  assert.equal(wl.isReservedFutureSlot(byTime['20:00']), false, '지난 칸은 예약되었어도 대기 대상이 아니다');
  assert.equal(wl.isReservedFutureSlot(byTime['22:30']), false, '비어 있는 칸은 대기 대상이 아니다');
  assert.equal(byTime['22:00'].reason, '예약됨');
  assert.equal(byTime['20:00'].reason, '지난 시간');
});

test('예약된 미래 칸이 없으면 대기 모드를 보여 주지 않는다', () => {
  const wl = load('utils/waitlist.ts');
  assert.equal(wl.hasReservedFutureSlot(slots()), true);
  assert.equal(wl.hasReservedFutureSlot(slots().filter((s) => s.startTime !== '22:00')), false);
});

test('대기 모드에서는 지난 칸만 선택할 수 없다', () => {
  const wl = load('utils/waitlist.ts');
  const byTime = Object.fromEntries(wl.toWaitlistSlots(slots()).map((s) => [s.startTime, s]));

  assert.equal(byTime['22:00'].selectable, true, '예약된 미래 칸은 선택 가능');
  assert.equal(byTime['22:30'].selectable, true, '비어 있는 미래 칸도 구간에 포함 가능');
  assert.equal(byTime['20:00'].selectable, false);
  assert.equal(byTime['19:00'].selectable, false);
});

test('구간에 예약된 칸이 하나라도 있어야 대기 신청이 성립한다', () => {
  const wl = load('utils/waitlist.ts');
  const s = slots();

  assert.equal(wl.rangeHasReservedSlot(s, '22:00', '23:00'), true, '22:00(예약됨)+22:30(빈 칸)');
  assert.equal(wl.rangeHasReservedSlot(s, '22:30', '23:00'), false, '빈 칸만 고르면 바로 예약해야 한다');
  assert.equal(wl.rangeHasReservedSlot(s, null, null), false);
});

test('서버 LocalDateTime 변환은 시각을 그대로 유지한다', () => {
  const wl = load('utils/waitlist.ts');
  assert.equal(wl.toLocalDateTime('2026-10-15', '22:30'), '2026-10-15T22:30:00');
  assert.deepEqual(wl.splitLocalDateTime('2026-10-15T22:30:00'), { date: '2026-10-15', time: '22:30' });
});

test('대기 API는 구간을 LocalDateTime 으로 바꿔 보내고 취소는 DELETE 를 쓴다', async () => {
  const calls = [];
  const api = {
    post: async (...args) => { calls.push(['post', ...args]); return { id: 7 }; },
    get: async (...args) => { calls.push(['get', ...args]); return []; },
    delete: async (...args) => { calls.push(['delete', ...args]); },
  };
  const waitlistApi = load('api/waitlistApi.ts', { './client': { __esModule: true, default: api } });

  await waitlistApi.applyWaitlist({ spaceId: 3, date: '2026-10-15', startTime: '22:00', endTime: '23:00' });
  await waitlistApi.getMyWaitlist();
  await waitlistApi.cancelWaitlist(7);

  assert.deepEqual(calls, [
    ['post', '/waitlist', { spaceId: 3, startTime: '2026-10-15T22:00:00', endTime: '2026-10-15T23:00:00' }],
    ['get', '/waitlist/me'],
    ['delete', '/waitlist/7'],
  ]);
});

test('대기 오류 코드마다 사용자 문구가 있다', () => {
  const { toMessage, ERROR_CODE } = load('constants/errorCodes.ts');
  for (const code of ['WAITLIST_NOT_FOUND', 'WAITLIST_NOT_NEEDED', 'WAITLIST_ALREADY_HOLDING', 'WAITLIST_DUPLICATE', 'WAITLIST_LIMIT_EXCEEDED']) {
    assert.equal(ERROR_CODE[code], code);
    assert.notEqual(toMessage(code), '요청을 처리하지 못했습니다.', `${code} 문구 누락`);
  }
});
