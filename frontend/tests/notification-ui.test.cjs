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


test('알림 API 경로·파라미터와 응답을 보존한다', async () => {
  const calls = [];
  const page = {content: [{id: 7, waitlistEntryId: 3, message: '빈자리', read: false, createdAt: '2026-10-16T12:00:00'}], page: 2, size: 10, totalPages: 3, totalElements: 21};
  const api = { get: async (...args) => {calls.push(['get', ...args]); return args[0].endsWith('unread-count') ? {unreadCount: 4} : page;}, post: async (...args) => {calls.push(['post', ...args]); return {updatedCount: 4};} };
  const mod = load('api/notificationApi.ts', {'./client': {__esModule: true, default: api}});
  assert.deepEqual(await mod.getNotifications({page: 2, size: 10}), page);
  assert.deepEqual(await mod.getUnreadCount(), {unreadCount: 4});
  assert.deepEqual(await mod.readAllNotifications(), {updatedCount: 4});
  await mod.getNotifications();
  assert.deepEqual(calls, [['get', '/notifications', {query: {page: 2, size: 10}}], ['get', '/notifications/unread-count'], ['post', '/notifications/read-all'], ['get', '/notifications', {query: {page: 0, size: 20}}]]);
});

test('배지는 0이면 숨기고 비로그인은 요청하지 않는다', async () => {
  const {renderToStaticMarkup} = require('react-dom/server');
  let calls = 0;
  const effects = [];
  const React = require('react');
  const mod = load('components/notification/NotificationLink.tsx', {
    react: {...React, useState: () => [null, () => {}], useEffect: (fn) => effects.push(fn)},
    '../../api/notificationApi': {getUnreadCount: async () => {calls++; return {unreadCount: 1};}},
    'next/link': {__esModule: true, default: ({href, children, ...props}) => React.createElement('a', {href, ...props}, children)},
  });
  assert.equal(renderToStaticMarkup(mod.default({memberId: undefined, pathname: '/'})), '');
  effects.forEach(fn => fn());
  await Promise.resolve();
  assert.equal(calls, 0);
  const badge = load('components/notification/UnreadBadge.tsx').default;
  assert.equal(renderToStaticMarkup(React.createElement(badge, {count: 0})), '');
  assert.match(renderToStaticMarkup(React.createElement(badge, {count: 3})), /3/);
});
