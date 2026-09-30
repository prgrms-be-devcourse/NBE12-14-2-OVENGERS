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

class MockApiError extends Error {
  constructor({ code, status, message }) {
    super(message || code);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/**
 * Standard React Hook Simulator supporting index-based useState, useRef, useCallback, and useEffect.
 */
function createHookHarness(hookFn, initialProps) {
  let stateSlots = [];
  let refSlots = [];
  let callbackSlots = [];
  let effectSlots = [];
  let hookOutput = null;
  let isMounted = true;
  let currentProps = initialProps;

  let stateIdx = 0;
  let refIdx = 0;
  let callbackIdx = 0;
  let effectIdx = 0;
  let isRendering = false;
  let renderQueued = false;
  let pendingEffects = [];

  let unmountedSetStateCalls = 0;

  const mockReact = {
    useState(initial) {
      const idx = stateIdx++;
      if (stateSlots.length <= idx) {
        stateSlots.push(typeof initial === 'function' ? initial() : initial);
      }
      const setState = (action) => {
        if (!isMounted) {
          unmountedSetStateCalls++;
          return;
        }
        const prev = stateSlots[idx];
        const next = typeof action === 'function' ? action(prev) : action;
        stateSlots[idx] = next;
        if (isRendering) {
          renderQueued = true;
        } else {
          render();
        }
      };
      return [stateSlots[idx], setState];
    },
    useRef(initial) {
      const idx = refIdx++;
      if (refSlots.length <= idx) {
        refSlots.push({ current: initial });
      }
      return refSlots[idx];
    },
    useCallback(fn, deps) {
      const idx = callbackIdx++;
      const prev = callbackSlots[idx];
      if (prev && deps && prev.deps.every((d, i) => Object.is(d, deps[i]))) {
        return prev.fn;
      }
      callbackSlots[idx] = { fn, deps };
      return fn;
    },
    useEffect(fn, deps) {
      const idx = effectIdx++;
      const prev = effectSlots[idx];
      let hasChanged = true;
      if (prev && deps && prev.deps) {
        hasChanged = !prev.deps.every((d, i) => Object.is(d, deps[i]));
      }
      if (hasChanged) {
        const slot = { fn, deps, cleanup: null };
        effectSlots[idx] = slot;
        pendingEffects.push({ fn, prev, slot });
      }
    },
  };

  const useApiModule = load('hooks/useApi.ts', {
    react: mockReact,
    '../api/client': { ApiError: MockApiError },
  });

  function flushEffects() {
    const effectsToRun = pendingEffects;
    pendingEffects = [];
    for (const eff of effectsToRun) {
      if (eff.prev?.cleanup) {
        eff.prev.cleanup();
      }
      const cleanup = eff.fn();
      eff.slot.cleanup = typeof cleanup === 'function' ? cleanup : null;
    }
  }

  function render() {
    if (isRendering) {
      renderQueued = true;
      return;
    }
    isRendering = true;
    try {
      stateIdx = 0;
      refIdx = 0;
      callbackIdx = 0;
      effectIdx = 0;
      hookOutput = hookFn(useApiModule, currentProps);
    } finally {
      isRendering = false;
    }
    flushEffects();
    if (renderQueued) {
      renderQueued = false;
      render();
    }
  }

  // Initial render
  render();

  function unmount() {
    isMounted = false;
    for (const eff of effectSlots) {
      if (eff.cleanup) eff.cleanup();
    }
  }

  function updateProps(nextProps) {
    currentProps = nextProps;
    render();
  }

  return {
    get data() { return stateSlots[0]; },
    get loading() { return stateSlots[1]; },
    get error() { return stateSlots[2]; },
    get hookOutput() { return hookOutput; },
    run: (...args) => hookOutput.run(...args),
    unmount,
    updateProps,
    get unmountedSetStateCalls() { return unmountedSetStateCalls; },
  };
}

test('useAsync: out-of-order responses do not overwrite newer response (race condition defense)', async () => {
  let resolverFn = null;
  const harness = createHookHarness(
    (mod) => mod.useAsync(() => resolverFn(), [], { immediate: false }),
    {},
  );

  assert.equal(harness.data, null);
  assert.equal(harness.loading, false);
  assert.equal(harness.error, null);

  // Request 1: slower
  let resolveReq1;
  const p1 = new Promise((resolve) => { resolveReq1 = resolve; });
  resolverFn = () => p1;
  const run1Promise = harness.run();

  // Request 2: faster
  let resolveReq2;
  const p2 = new Promise((resolve) => { resolveReq2 = resolve; });
  resolverFn = () => p2;
  const run2Promise = harness.run();

  // Request 2 completes first
  resolveReq2({ id: 2, name: 'Request 2 (Newer)' });
  await run2Promise;

  assert.equal(harness.loading, false);
  assert.deepEqual(harness.data, { id: 2, name: 'Request 2 (Newer)' });
  assert.equal(harness.error, null);

  // Request 1 completes later
  resolveReq1({ id: 1, name: 'Request 1 (Older)' });
  await run1Promise;

  // Data must still be Request 2, NOT overwritten by older Request 1!
  assert.deepEqual(harness.data, { id: 2, name: 'Request 2 (Newer)' });
  assert.equal(harness.loading, false);
  assert.equal(harness.error, null);
});

test('useAsync: older error does not overwrite newer successful response', async () => {
  let resolverFn = null;
  const harness = createHookHarness(
    (mod) => mod.useAsync(() => resolverFn(), [], { immediate: false }),
    {},
  );

  // Request 1: slower that fails
  let rejectReq1;
  const p1 = new Promise((_, reject) => { rejectReq1 = reject; });
  resolverFn = () => p1;
  const run1Promise = harness.run().catch(() => {});

  // Request 2: faster that succeeds
  let resolveReq2;
  const p2 = new Promise((resolve) => { resolveReq2 = resolve; });
  resolverFn = () => p2;
  const run2Promise = harness.run();

  resolveReq2({ id: 2, success: true });
  await run2Promise;

  assert.deepEqual(harness.data, { id: 2, success: true });
  assert.equal(harness.error, null);
  assert.equal(harness.loading, false);

  // Request 1 fails later
  rejectReq1(new MockApiError({ code: 'ERR_1', status: 500, message: 'Older error' }));
  await run1Promise;

  // Newer successful state must be preserved
  assert.deepEqual(harness.data, { id: 2, success: true });
  assert.equal(harness.error, null);
  assert.equal(harness.loading, false);
});

test('useAsync: newer error correctly sets error state and older success does not overwrite it', async () => {
  let resolverFn = null;
  const harness = createHookHarness(
    (mod) => mod.useAsync(() => resolverFn(), [], { immediate: false }),
    {},
  );

  // Request 1: slower that succeeds
  let resolveReq1;
  const p1 = new Promise((resolve) => { resolveReq1 = resolve; });
  resolverFn = () => p1;
  const run1Promise = harness.run();

  // Request 2: faster that fails
  let rejectReq2;
  const p2 = new Promise((_, reject) => { rejectReq2 = reject; });
  resolverFn = () => p2;
  const run2Promise = harness.run().catch(() => {});

  // Request 2 fails first
  const expectedError = new MockApiError({ code: 'REQ2_FAIL', status: 400, message: 'Invalid query parameter' });
  rejectReq2(expectedError);
  await run2Promise;

  // Error must be set in error state (not in data!), loading is false
  assert.equal(harness.data, null);
  assert.ok(harness.error, 'Error state must be populated on failure');
  assert.equal(harness.error.code, 'REQ2_FAIL');
  assert.equal(harness.loading, false);

  // Request 1 finishes later
  resolveReq1({ id: 1, valid: true });
  await run1Promise;

  // Error state of newer request 2 must NOT be cleared by older request 1
  assert.equal(harness.data, null);
  assert.ok(harness.error, 'Error must be preserved when older success arrives');
  assert.equal(harness.error.code, 'REQ2_FAIL');
  assert.equal(harness.loading, false);
});

test('useAsync: loading remains true if older request finishes while newer request is still in-flight', async () => {
  let resolverFn = null;
  const harness = createHookHarness(
    (mod) => mod.useAsync(() => resolverFn(), [], { immediate: false }),
    {},
  );

  // Request 1 (first)
  let resolveReq1;
  const p1 = new Promise((resolve) => { resolveReq1 = resolve; });
  resolverFn = () => p1;
  const run1Promise = harness.run();

  // Request 2 (second, newer)
  let resolveReq2;
  const p2 = new Promise((resolve) => { resolveReq2 = resolve; });
  resolverFn = () => p2;
  const run2Promise = harness.run();

  assert.equal(harness.loading, true);

  // Request 1 finishes before Request 2
  resolveReq1({ id: 1 });
  await run1Promise;

  // Loading must REMAIN TRUE because the newer Request 2 is still pending!
  assert.equal(harness.loading, true, 'Loading must remain true while newer request is still in-flight');
  assert.equal(harness.data, null, 'Data must not be populated with older request when newer is pending');

  // Request 2 finishes
  resolveReq2({ id: 2 });
  await run2Promise;

  assert.equal(harness.loading, false);
  assert.deepEqual(harness.data, { id: 2 });
});

test('useAsync: unmounted component ignores pending resolution without calling setState', async () => {
  let resolveReq;
  const p = new Promise((resolve) => { resolveReq = resolve; });
  const harness = createHookHarness(
    (mod) => mod.useAsync(() => p, [], { immediate: false }),
    {},
  );

  const runPromise = harness.run();
  assert.equal(harness.loading, true);

  // Unmount before completion
  harness.unmount();

  // Complete the promise
  resolveReq({ id: 999 });
  await runPromise;

  // Unmounted state should not update data, and hook's mounted.current defense must prevent any setState calls
  assert.equal(harness.data, null);
  assert.equal(harness.unmountedSetStateCalls, 0, 'Hook must check mounted.current and avoid calling setState after unmount');
});

test('useAsync: unmounted component ignores pending rejection without calling setState', async () => {
  let rejectReq;
  const p = new Promise((_, reject) => { rejectReq = reject; });
  const harness = createHookHarness(
    (mod) => mod.useAsync(() => p, [], { immediate: false }),
    {},
  );

  const runPromise = harness.run().catch(() => {});
  assert.equal(harness.loading, true);

  // Unmount before rejection
  harness.unmount();

  // Reject the promise
  rejectReq(new Error('네트워크 실패'));
  await runPromise;

  assert.equal(harness.error, null);
  assert.equal(harness.unmountedSetStateCalls, 0, 'Hook must not call setState on rejection after unmount');
});

test('useAsync: remounting creates a fresh mounted instance that updates state normally', async () => {
  let resolveReq1;
  const p1 = new Promise((resolve) => { resolveReq1 = resolve; });
  const harness1 = createHookHarness(
    (mod) => mod.useAsync(() => p1, [], { immediate: false }),
    {},
  );
  harness1.run();
  harness1.unmount();
  resolveReq1({ id: 1 });
  assert.equal(harness1.unmountedSetStateCalls, 0);

  // Fresh harness simulates component remounting
  let resolveReq2;
  const p2 = new Promise((resolve) => { resolveReq2 = resolve; });
  const harness2 = createHookHarness(
    (mod) => mod.useAsync(() => p2, [], { immediate: false }),
    {},
  );
  const run2 = harness2.run();
  assert.equal(harness2.loading, true);
  resolveReq2({ id: 2 });
  await run2;
  assert.equal(harness2.loading, false);
  assert.deepEqual(harness2.data, { id: 2 });
  assert.equal(harness2.unmountedSetStateCalls, 0);
});

test('useAsync: deps change triggers new execution when immediate is true and race defense applies', async () => {
  let resolveA;
  const pA = new Promise((resolve) => { resolveA = resolve; });
  let resolveB;
  const pB = new Promise((resolve) => { resolveB = resolve; });
  const resolverFn = (f) => (f === 'A' ? pA : pB);

  const harness = createHookHarness(
    (mod, props) => mod.useAsync(() => resolverFn(props.filter), [props.filter], { immediate: true }),
    { filter: 'A' },
  );

  // Trigger deps change to B before A resolves
  harness.updateProps({ filter: 'B' });

  // Resolve B (filter B) first
  resolveB({ result: 'Data for B' });
  await new Promise((r) => setImmediate(r));

  assert.deepEqual(harness.data, { result: 'Data for B' });
  assert.equal(harness.loading, false);

  // Resolve A (older filter A) later
  resolveA({ result: 'Data for A' });
  await new Promise((r) => setImmediate(r));

  // B must be preserved
  assert.deepEqual(harness.data, { result: 'Data for B' });
});
