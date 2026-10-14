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

test('setupChargeWidgets follows the official checkout-style SDK sequence', async () => {
  const calls = [];
  let agreementListener;
  const paymentMethods = { destroy: async () => { calls.push(['destroyPaymentMethods']); } };
  const agreement = {
    on: (event, listener) => { calls.push(['agreementOn', event]); agreementListener = listener; },
    destroy: async () => { calls.push(['destroyAgreement']); },
  };
  const widgets = {
    setAmount: async (amount) => { calls.push(['setAmount', amount]); },
    renderPaymentMethods: async (params) => { calls.push(['renderPaymentMethods', params]); return paymentMethods; },
    renderAgreement: async (params) => { calls.push(['renderAgreement', params]); return agreement; },
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
  const agreementStates = [];

  const session = await setupChargeWidgets({
    clientKey: 'test_gck_client',
    amount: 1_000,
    onAgreementChange: (agreed) => agreementStates.push(agreed),
  });
  agreementListener({ agreedRequiredTerms: true, agreements: [] });
  await session.requestPayment({
    orderId: 'chg_order',
    orderName: 'Slot Key 크레딧 1,000원 충전',
    successUrl: 'https://slotkey.test/credits/charge/success?returnTo=%2Fmypage',
    failUrl: 'https://slotkey.test/credits/charge/fail?returnTo=%2Fmypage',
    customerEmail: 'member@test.com',
    customerName: 'member',
  });
  await session.destroy();

  assert.deepEqual(calls, [
    ['load', 'test_gck_client'],
    ['widgets', { customerKey: '@@ANONYMOUS' }],
    ['setAmount', { currency: 'KRW', value: 1_000 }],
    ['renderPaymentMethods', { selector: '#payment-method', variantKey: 'DEFAULT' }],
    ['renderAgreement', { selector: '#agreement', variantKey: 'AGREEMENT' }],
    ['agreementOn', 'agreementStatusChange'],
    ['requestPayment', {
      orderId: 'chg_order',
      orderName: 'Slot Key 크레딧 1,000원 충전',
      successUrl: 'https://slotkey.test/credits/charge/success?returnTo=%2Fmypage',
      failUrl: 'https://slotkey.test/credits/charge/fail?returnTo=%2Fmypage',
      customerEmail: 'member@test.com',
      customerName: 'member',
    }],
    ['destroyPaymentMethods'],
    ['destroyAgreement'],
  ]);
  assert.deepEqual(agreementStates, [true]);
});
test('setupChargeWidgets removes payment methods when agreement rendering fails', async () => {
  let destroyed = 0;
  const widgets = {
    setAmount: async () => {},
    renderPaymentMethods: async () => ({ destroy: async () => { destroyed += 1; } }),
    renderAgreement: async () => { throw new Error('agreement failed'); },
  };
  const tossModule = {
    ANONYMOUS: '@@ANONYMOUS',
    loadTossPayments: async () => ({ widgets: () => widgets }),
  };
  const { setupChargeWidgets } = load('payments/tossChargeWidgets.ts', {
    '@tosspayments/tosspayments-sdk': tossModule,
  });

  await assert.rejects(
    setupChargeWidgets({
      clientKey: 'test_gck_client',
      amount: 1_000,
      onAgreementChange: () => {},
    }),
    /agreement failed/,
  );
  assert.equal(destroyed, 1);
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
        onAgreementChange: () => {},
      }),
      /클라이언트 키 형식이 아닙니다/,
    );
  }
  assert.deepEqual(loadedKeys, []);
});
