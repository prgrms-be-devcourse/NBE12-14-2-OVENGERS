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
    const resolved = ['', '.ts', '.tsx'].map(ext => target + ext).find(p => fs.existsSync(p));
    return load(path.relative(root, resolved), mocks);
  };
  vm.runInThisContext(`(function(require,module,exports){${source}\n})`, { filename: file })(localRequire, loadedModule, loadedModule.exports);
  return loadedModule.exports;
}

const mocks = {
  'next/link': ({ href, children, ...props }) => React.createElement('a', { href, ...props }, children),
  '../space/SpacePhoto': ({ src, alt }) => React.createElement('img', { src: src || '/placeholder.png', alt }),
};

const ReservationCard = load('components/reservation/ReservationCard.tsx', mocks).default;

test('ReservationCard formats time as HH:mm and keeps single date for HELD reservation', () => {
  const heldReservation = {
    reservationId: 101,
    spaceId: 1,
    spaceName: '포커스룸 A',
    spaceLocation: '3층 서관',
    spaceImagePath: '/images/focus-a.jpg',
    date: '2026-09-23',
    startTime: '2026-09-23T09:00:00',
    endTime: '2026-09-23T10:30:00',
    status: 'HELD',
    totalAmount: 15000,
  };

  const html = renderToStaticMarkup(React.createElement(ReservationCard, { reservation: heldReservation }));

  // 시·분 표기와 날짜 포맷 검증
  assert.match(html, /2026년 9월 23일 \(수\) · 09:00 ~ 10:30/);

  // 날짜 중복 없음 (2026년 9월 23일은 정확히 1회만 등장)
  const dateMatches = html.match(/2026년 9월 23일/g);
  assert.equal(dateMatches ? dateMatches.length : 0, 1);

  // 'T' 나 초(:00) 노출 없음
  assert.doesNotMatch(html, /2026-09-23T/);
  assert.doesNotMatch(html, /09:00:00/);
  assert.doesNotMatch(html, /10:30:00/);

  // HELD 상태일 때 결제하기 버튼과 결제 경로 연결
  assert.match(html, /결제하기/);
  assert.match(html, /href="\/reservations\/101\/payment"/);
  assert.doesNotMatch(html, /상세 보기/);

  // 공간명, 금액, 예약번호 표기
  assert.match(html, /포커스룸 A/);
  assert.match(html, /15,000원/);
  assert.match(html, /SK-101/);
});

test('ReservationCard formats time as HH:mm and links to detail for CONFIRMED reservation', () => {
  const confirmedReservation = {
    reservationId: 202,
    spaceId: 2,
    spaceName: '세미나실 B',
    spaceLocation: '5층',
    spaceImagePath: null,
    date: '2026-09-23',
    startTime: '2026-09-23T14:00:00',
    endTime: '2026-09-23T16:00:00',
    status: 'CONFIRMED',
    totalAmount: 40000,
  };

  const html = renderToStaticMarkup(React.createElement(ReservationCard, { reservation: confirmedReservation }));

  // 시·분 표기와 날짜 포맷 검증
  assert.match(html, /2026년 9월 23일 \(수\) · 14:00 ~ 16:00/);

  // 날짜 중복 없음
  const dateMatches = html.match(/2026년 9월 23일/g);
  assert.equal(dateMatches ? dateMatches.length : 0, 1);

  // 'T' 나 초(:00) 노출 없음
  assert.doesNotMatch(html, /2026-09-23T/);
  assert.doesNotMatch(html, /14:00:00/);
  assert.doesNotMatch(html, /16:00:00/);

  // CONFIRMED 상태일 때 상세 보기 버튼과 상세 경로 연결
  assert.match(html, /상세 보기/);
  assert.match(html, /href="\/reservations\/202(?!.*payment)"/);
  assert.doesNotMatch(html, /결제하기/);

  // 공간명, 금액, 예약번호 표기
  assert.match(html, /세미나실 B/);
  assert.match(html, /40,000원/);
  assert.match(html, /SK-202/);
});
