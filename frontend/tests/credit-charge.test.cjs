/* eslint-disable @typescript-eslint/no-require-imports -- Node test harness loads actual TS modules in CommonJS isolation. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const root = path.resolve(__dirname, '../src');

function load(relative) {
  const file = path.resolve(root, relative);
  const source = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const loadedModule = { exports: {} };
  vm.runInThisContext(`(function(require,module,exports){${source}\n})`, { filename: file })(
    require,
    loadedModule,
    loadedModule.exports,
  );
  return loadedModule.exports;
}

test('normalizeReturnTo accepts only same-origin path syntax', () => {
  const { normalizeReturnTo } = load('utils/creditCharge.ts');
  const previousWindow = global.window;
  global.window = { location: { origin: 'https://slotkey.test' } };

  try {
    assert.equal(normalizeReturnTo('/reservations/7/payment?spaceVersion=2#confirm'), '/reservations/7/payment?spaceVersion=2#confirm');
    assert.equal(normalizeReturnTo('https://slotkey.test/mypage?tab=credit#balance'), '/mypage?tab=credit#balance');
    assert.equal(normalizeReturnTo('/'), '/');
    assert.equal(normalizeReturnTo('//evil.example/path'), '/mypage');
    assert.equal(normalizeReturnTo('https://evil.example/path'), '/mypage');
    assert.equal(normalizeReturnTo('/\\evil.example'), '/mypage');
    assert.equal(normalizeReturnTo('/\\/evil.example'), '/mypage');
    assert.equal(normalizeReturnTo('/\t/evil.example'), '/mypage');
    assert.equal(normalizeReturnTo('/\n/evil.example'), '/mypage');
    assert.equal(normalizeReturnTo('/..//evil.example'), '/mypage');
    assert.equal(normalizeReturnTo('/a/../..//evil.example'), '/mypage');
    assert.equal(normalizeReturnTo('/./..//evil.example'), '/mypage');
    assert.equal(normalizeReturnTo(''), '/mypage');
    assert.equal(normalizeReturnTo(null), '/mypage');
  } finally {
    global.window = previousWindow;
  }
});
test('suggestChargeAmount rounds the deficit to 100 won within server limits', () => {
  const { suggestChargeAmount } = load('utils/creditCharge.ts');

  assert.equal(suggestChargeAmount(6_500, 1_200), 5_300);
  assert.equal(suggestChargeAmount(1_100, 1_000), 1_000);
  assert.equal(suggestChargeAmount(120_000, 0), 100_000);
  assert.equal(suggestChargeAmount(undefined, undefined), 1_000);
});

test('buildChargeRedirectUrl keeps the validated return path in an absolute URL', () => {
  const { buildChargeRedirectUrl } = load('utils/creditCharge.ts');
  const previousWindow = global.window;
  global.window = { location: { origin: 'https://slotkey.test' } };

  try {
    assert.equal(
      buildChargeRedirectUrl(
        'https://slotkey.test',
        '/credits/charge/success',
        '/reservations/7/payment?spaceVersion=2',
      ),
      'https://slotkey.test/credits/charge/success?returnTo=%2Freservations%2F7%2Fpayment%3FspaceVersion%3D2',
    );
    assert.equal(
      buildChargeRedirectUrl('https://slotkey.test', '/credits/charge/fail', '//evil.example'),
      'https://slotkey.test/credits/charge/fail?returnTo=%2Fmypage',
    );
  } finally {
    global.window = previousWindow;
  }
});

test('pollChargeOrder stops when recovery reaches a terminal status', async () => {
  const { pollChargeOrder } = load('utils/creditCharge.ts');
  const responses = [
    { orderId: 'chg_order', amount: 1_000, status: 'CONFIRMING' },
    { orderId: 'chg_order', amount: 1_000, status: 'PAID' },
  ];
  let waits = 0;

  const result = await pollChargeOrder({
    getOrder: async () => responses.shift(),
    wait: async () => { waits += 1; },
    maxAttempts: 5,
  });

  assert.equal(result.status, 'PAID');
  assert.equal(waits, 1);
  assert.equal(responses.length, 0);
});

test('pollChargeOrder returns CONFIRMING after its attempt limit', async () => {
  const { pollChargeOrder } = load('utils/creditCharge.ts');
  let calls = 0;

  const result = await pollChargeOrder({
    getOrder: async () => {
      calls += 1;
      return { orderId: 'chg_order', amount: 1_000, status: 'CONFIRMING' };
    },
    wait: async () => {},
    maxAttempts: 2,
  });

  assert.equal(result.status, 'CONFIRMING');
  assert.equal(calls, 2);
});

test('pollChargeOrder stops when the result page is no longer active', async () => {
  const { pollChargeOrder } = load('utils/creditCharge.ts');
  let active = true;
  let calls = 0;

  await assert.rejects(
    pollChargeOrder({
      getOrder: async () => {
        calls += 1;
        return { orderId: 'chg_1', amount: 1000, status: 'CONFIRMING' };
      },
      wait: async () => {
        active = false;
      },
      maxAttempts: 40,
      isActive: () => active,
    }),
    { name: 'PollCancelledError' },
  );
  assert.equal(calls, 1);
});
