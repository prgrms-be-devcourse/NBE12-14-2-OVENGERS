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
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const loadedModule = { exports: {} };
  const localRequire = (name) => Object.hasOwn(mocks, name) ? mocks[name] : require(name);
  vm.runInThisContext(`(function(require,module,exports){${source}\n})`, { filename: file })(
    localRequire,
    loadedModule,
    loadedModule.exports,
  );
  return loadedModule.exports;
}

test('setupChargeWidgets renders payment methods only; terms consent is our own unchecked checkbox', async () => {
  const calls = [];
  const paymentMethods = { destroy: async () => { calls.push(['destroyPaymentMethods']); } };
  const widgets = {
    setAmount: async (amount) => { calls.push(['setAmount', amount]); },
    renderPaymentMethods: async (params) => { calls.push(['renderPaymentMethods', params]); return paymentMethods; },
    renderAgreement: async (params) => { calls.push(['renderAgreement', params]); },
    requestPayment: async (params) => { calls.push(['requestPayment', params]); },
  };
  const sdk = { widgets: (params) => { calls.push(['widgets', params]); return widgets; } };
  const tossModule = {
    ANONYMOUS: '@@ANONYMOUS',
    loadTossPayments: async (key) => { calls.push(['load', key]); return sdk; },
  };
  const { setupChargeWidgets } = load('payments/tossChargeWidgets.ts', {
    '@tosspayments/tosspayments-sdk': tossModule,
  });

  const session = await setupChargeWidgets({ clientKey: 'test_gck_client', amount: 1_000 });
  await session.requestPayment({
    orderId: 'chg_order',
    orderName: 'Slot Key 크레딧 1,000원 충전',
    successUrl: 'https://slotkey.test/credits/charge/success?returnTo=%2Fmypage',
    failUrl: 'https://slotkey.test/credits/charge/fail?returnTo=%2Fmypage',
    customerEmail: 'member@test.com',
    customerName: 'member',
  });
  await session.destroy();

  // 토스 약관 위젯은 상점 설정(defaultAgreementChecked)으로 체크된 채 그려지므로 렌더링하지 않는다.
  assert.deepEqual(calls, [
    ['load', 'test_gck_client'],
    ['widgets', { customerKey: '@@ANONYMOUS' }],
    ['setAmount', { currency: 'KRW', value: 1_000 }],
    ['renderPaymentMethods', { selector: '#payment-method', variantKey: 'DEFAULT' }],
    ['requestPayment', {
      orderId: 'chg_order',
      orderName: 'Slot Key 크레딧 1,000원 충전',
      successUrl: 'https://slotkey.test/credits/charge/success?returnTo=%2Fmypage',
      failUrl: 'https://slotkey.test/credits/charge/fail?returnTo=%2Fmypage',
      customerEmail: 'member@test.com',
      customerName: 'member',
    }],
    ['destroyPaymentMethods'],
  ]);
});

test('TOSS_PAYMENT_TERMS 는 토스 필수 약관 3개를 토스 공식 약관 페이지로 연결한다', () => {
  const { TOSS_PAYMENT_TERMS } = load('constants/tossPaymentTerms.ts');
  assert.equal(TOSS_PAYMENT_TERMS.title, '[필수] 결제 서비스 이용 약관, 개인정보 처리 동의');
  assert.deepEqual(TOSS_PAYMENT_TERMS.items.map((item) => [item.title, item.url]), [
    ['서비스 이용약관', 'https://pages.tosspayments.com/terms/user'],
    ['개인(신용)정보 수집 및 이용 동의', 'https://pages.tosspayments.com/terms/privacy/consent1'],
    ['개인(신용)정보 제3자 제공 동의', 'https://pages.tosspayments.com/terms/privacy/consent2'],
  ]);
});

test('setupChargeWidgets rejects non-client keys before loading the SDK', async () => {
  const loadedKeys = [];
  const tossModule = {
    ANONYMOUS: '@@ANONYMOUS',
    loadTossPayments: async (key) => {
      loadedKeys.push(key);
      throw new Error('SDK must not load');
    },
  };
  const { getTossClientKeyError, setupChargeWidgets } = load('payments/tossChargeWidgets.ts', {
    '@tosspayments/tosspayments-sdk': tossModule,
  });

  assert.equal(getTossClientKeyError('test_gck_client'), null);
  assert.equal(getTossClientKeyError('live_gck_client'), null);
  assert.match(getTossClientKeyError(undefined), /설정되지 않았습니다/);

  for (const clientKey of ['test_gsk_secret', 'live_gsk_secret', 'test_ck_legacy', 'invalid']) {
    await assert.rejects(
      setupChargeWidgets({
        clientKey,
        amount: 1_000,
      }),
      /클라이언트 키 형식이 아닙니다/,
    );
  }
  assert.deepEqual(loadedKeys, []);
});
