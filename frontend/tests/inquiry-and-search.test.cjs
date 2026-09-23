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
  const source = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true,
  } }).outputText;
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
const search = load('utils/spaceSearch.ts');
const filters = (patch = {}) => ({ ...search.EMPTY_SPACE_FILTERS, ...patch });
test('empty filters are valid; zero price and trimmed keyword survive serialization', () => {
  assert.equal(search.validateSpaceSearch(filters()), null);
  const result = search.toSpaceSearchParams(filters({ minPrice: '0', keyword: ' room ', location: '하남' }));
  assert.equal(result.minPrice, 0); assert.equal(result.keyword, 'room'); assert.equal(result.location, '하남');
  assert.equal(result.date, undefined);
});
test('reject negative, fractional, unsafe and inverted price ranges', () => {
  for (const minPrice of ['-1', '1.5', '9007199254740992']) assert.ok(search.validateSpaceSearch(filters({ minPrice })));
  assert.ok(search.validateSpaceSearch(filters({ minPrice: '5000', maxPrice: '4000' })));
});
test('date and both times must be supplied together', () => {
  for (const patch of [{ date: '2026-09-23' }, { startTime: '09:00' }, { date: '2026-09-23', endTime: '10:00' }])
    assert.throws(() => search.toSpaceSearchParams(filters(patch)));
});
test('only increasing half-hour time ranges are accepted', () => {
  for (const [startTime,endTime] of [['09:15','10:00'],['10:00','10:00'],['11:00','10:00']])
    assert.ok(search.validateSpaceSearch(filters({ date:'2026-09-23',startTime,endTime })));
  const result = search.toSpaceSearchParams(filters({date:'2026-09-23',startTime:'09:30',endTime:'10:00'}));
  assert.equal(result.startTime,'09:30:00'); assert.equal(result.endTime,'10:00:00');
});
test('invalid calendar dates are rejected', () => {
  assert.ok(search.validateSpaceSearch(filters({date:'2026-02-31',startTime:'09:00',endTime:'10:00'})));
});
test('inquiry operations use the member/admin endpoints and exact request bodies', async () => {
  const calls=[];
  const client=Object.fromEntries(['get','post','patch'].map(method=>[method,async(...args)=>{calls.push([method,...args]);return {};}]));
  const api=load('api/inquiryApi.ts',{'./client':client});
  await api.createInquiry({title:'제목',content:'내용'}); await api.updateInquiry(4,{title:'수정',content:'내용'});
  await api.getInquiry(4); await api.getInquiry(4,true); await api.answerInquiry(4,'답변');
  await api.getInquiries({admin:true,status:'WAITING',page:2}); await api.getInquiries();
  assert.deepEqual(calls.slice(0,5),[
    ['post','/inquiries',{title:'제목',content:'내용'}],['patch','/inquiries/4',{title:'수정',content:'내용'}],
    ['get','/inquiries/4'],['get','/admin/inquiries/4'],['post','/admin/inquiries/4/answer',{content:'답변'}],
  ]);
  assert.equal(calls[5][2].query.status,'WAITING');assert.equal(calls[5][2].query.page,2);
  assert.equal(calls[6][2].query.status,undefined);
});
function detail(status,admin) {
  const data={id:4,memberId:1,title:'예약 문의',content:'<script>test</script>',status,answerContent:'답변입니다',createdAt:'2026-09-23T09:00:00',answeredAt:'2026-09-23T10:00:00'};
  const mocks={
    'next/navigation':{useSearchParams:()=>new URLSearchParams('id=4')},
    'next/link':({href,children,...props})=>React.createElement('a',{href,...props},children),
    '../../hooks/useApi':{useAsync:()=>({data,loading:false,error:null,run:async()=>data,setData:()=>{}}),useAction:()=>({loading:false,error:null,setError:()=>{},execute:async()=>data})},
    '../../api/inquiryApi':{},
  };
  const Page=load('views/inquiry/InquiryDetailPage.tsx',mocks).default;
  return renderToStaticMarkup(React.createElement(Page,{admin}));
}
test('member can edit only unanswered inquiries; user content is escaped',()=>{
  assert.match(detail('WAITING',false),/문의 수정/);
  assert.doesNotMatch(detail('ANSWERED',false),/문의 수정/);
  assert.match(detail('WAITING',false),/&lt;script&gt;/);
});
test('admin can answer once; answered inquiry displays response without form',()=>{
  assert.match(detail('WAITING',true),/답변 등록/);
  const html=detail('ANSWERED',true);assert.match(html,/답변입니다/);assert.doesNotMatch(html,/<textarea/);
});

const dashboard = load('utils/adminDashboard.ts');
const reservation = (reservationId, status, startTime='2026-09-23T09:00:00',spaceId=1) => ({reservationId,status,startTime,endTime:'2026-09-23T10:00:00',spaceId});
test('dashboard calendar uses Seoul date near UTC midnight',()=>{
  assert.equal(dashboard.seoulDate(new Date('2026-09-22T16:00:00Z')),'2026-09-23');
});
test('dashboard counts selected date and space; cancelled bookings do not inflate active totals',()=>{
  const rows=[reservation(1,'HELD'),reservation(2,'CONFIRMED'),reservation(3,'IN_USE','2026-09-23T09:00:00',2),reservation(4,'CANCELLED')];
  const selected=dashboard.selectDashboardReservations(rows,'2026-09-23','1');
  assert.deepEqual(dashboard.countDashboardStatuses(selected),{HELD:1,CONFIRMED:1,IN_USE:0,COMPLETED:0});
  assert.equal(dashboard.selectDashboardReservations(rows,'2026-09-24').length,0);
});
test('dashboard prioritizes in-use then held then confirmed without mutating input',()=>{
  const rows=[reservation(1,'CONFIRMED'),reservation(2,'HELD'),reservation(3,'IN_USE')];
  assert.deepEqual(dashboard.sortDashboardReservations(rows).map(r=>r.reservationId),[3,2,1]);
  assert.equal(rows[0].reservationId,1);
});
test('dashboard reads all pages and rejects partial totals on request failure',async()=>{
  const api=load('api/adminDashboardApi.ts',{'./client':{},'./adminSpaceApi':{}});
  const requested=[];
  assert.deepEqual(await api.collectDashboardPages(async page=>{requested.push(page);return {content:[page],totalPages:3};}),[0,1,2]);
  assert.deepEqual(requested,[0,1,2]);
  await assert.rejects(()=>api.collectDashboardPages(async page=>{if(page===1) throw new Error('offline');return {content:[0],totalPages:2};}),/offline/);
});
