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
  const localRequire = (name) => {
    if (Object.hasOwn(mocks, name)) return mocks[name];
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

test('credit API uses the charge-order endpoints and confirm idempotency header', async () => {
  const calls = [];
  const api = {
    post: async (...args) => { calls.push(['post', ...args]); return { status: 'PENDING' }; },
    get: async (...args) => { calls.push(['get', ...args]); return { status: 'CONFIRMING' }; },
  };
  const creditApi = load('api/creditApi.ts', { './client': { __esModule: true, default: api } });

  await creditApi.createChargeOrder(1_000);
  await creditApi.confirmChargeOrder(
    { paymentKey: 'payment-key', orderId: 'chg_order', amount: 1_000 },
    'request-key',
  );
  await creditApi.getChargeOrder('chg_order');

  assert.deepEqual(calls, [
    ['post', '/credits/charge-orders', { amount: 1_000 }],
    [
      'post',
      '/credits/charge-orders/confirm',
      { paymentKey: 'payment-key', orderId: 'chg_order', amount: 1_000 },
      { headers: { 'Idempotency-Key': 'request-key' } },
    ],
    ['get', '/credits/charge-orders/chg_order'],
  ]);
});
