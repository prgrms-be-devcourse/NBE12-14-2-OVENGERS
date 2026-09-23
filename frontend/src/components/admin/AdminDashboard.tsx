'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { getAdminDashboard } from '../../api/adminDashboardApi';
import { useAuth } from '../../hooks/useAuth';
import { useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import { RESERVATION_STATUS_META } from '../../constants/enums';
import { DASHBOARD_LABELS, DASHBOARD_STATES, countDashboardStatuses, selectDashboardReservations, seoulDate, sortDashboardReservations } from '../../utils/adminDashboard';
import type { ReservationStatus } from '../../types/api';
import Badge from '../common/Badge';
import Input from '../common/Input';
import Select from '../common/Select';
import Button from '../common/Button';
import Pagination from '../common/Pagination';
import ErrorMessage from '../common/ErrorMessage';
import LoadingSpinner from '../common/LoadingSpinner';
import styles from './AdminDashboard.module.css';

function SpacePhoto({src,name}:{src?:string|null;name:string}) {
  const [failed,setFailed]=useState(false);
  return src && !failed ? <img src={src} alt={name} loading="lazy" onError={()=>setFailed(true)}/> : <div className={styles.placeholder}>공간 사진 없음</div>;
}
export default function AdminDashboard({compact=false}:{compact?:boolean}) {
  const {isAdmin,member,initializing}=useAuth();
  if(initializing || !isAdmin) return null;
  return <DashboardContent key={member?.memberId} compact={compact}/>;
}
function DashboardContent({compact}:{compact:boolean}) {
  const {data,loading,error,run}=useAsync(getAdminDashboard,[]);
  const [date,setDate]=useState(()=>seoulDate());
  const [spaceId,setSpaceId]=useState('');
  const [status,setStatus]=useState<ReservationStatus|''>('');
  const [page,setPage]=useState(0);
  const refreshing=useRef(false);
  useEffect(()=>{
    const refresh=()=>{
      if(document.hidden || refreshing.current) return;
      refreshing.current=true;
      void run().catch(()=>{}).finally(()=>{refreshing.current=false;});
    };
    const timer=window.setInterval(refresh,60000);
    window.addEventListener('focus',refresh);
    return ()=>{window.clearInterval(timer);window.removeEventListener('focus',refresh);};
  },[run]);
  const effectiveDate=compact?seoulDate():date;
  const rows=selectDashboardReservations(data?.reservations??[],effectiveDate,spaceId);
  const counts=countDashboardStatuses(rows);
  const visible=sortDashboardReservations(rows.filter(row=>compact ? ['HELD','CONFIRMED','IN_USE'].includes(row.status) : !status || row.status===status));
  const size=compact?3:8;
  const actualPage=Math.min(page,Math.max(0,Math.ceil(visible.length/size)-1));
  const displayed=visible.slice(actualPage*size,actualPage*size+size);
  const photos=new Map(data?.spaces.map(space=>[space.id,space]));
  const Heading=compact?'h2':'h1';
  return <section className={`${styles.dashboard} ${compact?styles.compact:''}`} aria-label="관리자 예약 현황">
    <div className={styles.heading}><div><Heading>{compact?'오늘의 운영 현황':'운영 대시보드'}</Heading>
      <p>전체 관리 공간 · {effectiveDate||'전체 날짜'} · 이용 시간 기준</p></div>
      {compact?<Link href={ROUTES.adminDashboard} className={styles.link}>대시보드 전체 보기 →</Link>:<Button disabled={loading} onClick={()=>{void run().catch(()=>{});}}>새로고침</Button>}
    </div>
    {!compact && <div className={styles.filters}>
      <Input label="이용 날짜" type="date" value={date} onChange={e=>{setDate(e.target.value);setPage(0);}}/>
      <Select label="공간" value={spaceId} onChange={e=>{setSpaceId(e.target.value);setPage(0);}} options={[{value:'',label:'전체 관리 공간'},...(data?.spaces??[]).map(space=>({value:String(space.id),label:space.name}))]}/>
      <Select label="예약 상태" value={status} onChange={e=>{setStatus(e.target.value as ReservationStatus|'');setPage(0);}} options={[{value:'',label:'전체 상태'},...Object.entries(RESERVATION_STATUS_META).map(([value,meta])=>({value,label:DASHBOARD_LABELS[value as keyof typeof DASHBOARD_LABELS]??meta.label}))]}/>
    </div>}
    <ErrorMessage error={error} onRetry={()=>{void run().catch(()=>{});}}/>
    {!data && loading && <LoadingSpinner label="공간과 예약 현황을 불러오고 있습니다…"/>}
    {data && <>
      <div className={styles.stats}>{DASHBOARD_STATES.map(state=><button key={state} type="button" disabled={compact} aria-pressed={!compact && status===state} onClick={()=>{setStatus(status===state?'':state);setPage(0);}}>
        <span>{DASHBOARD_LABELS[state]}</span><strong>{counts[state].toLocaleString()}<small>건</small></strong>
      </button>)}</div>
      <div className={styles.listHeading}><h3>{compact?'확인이 필요한 예약':'예약 현황'}</h3><span>{compact?`진행 중 ${visible.length}건 · 최대 3건 표시`:`${visible.length}건`}</span></div>
      {displayed.length===0?<p className={styles.empty}>{compact?'오늘 결제 대기·결제 완료·사용 중인 예약이 없습니다.':'선택한 조건에 해당하는 예약이 없습니다.'}</p>:<div className={styles.list}>
        {displayed.map(row=><Link className={styles.row} href={ROUTES.adminReservationDetail(row.reservationId)} key={row.reservationId}>
          <SpacePhoto key={`${row.spaceId}:${photos.get(row.spaceId)?.imagePath}`} src={photos.get(row.spaceId)?.imagePath} name={row.spaceName}/>
          <div className={styles.details}><div className={styles.rowMeta}><Badge tone={RESERVATION_STATUS_META[row.status]?.tone}>{DASHBOARD_LABELS[row.status as keyof typeof DASHBOARD_LABELS]??RESERVATION_STATUS_META[row.status]?.label}</Badge><span>예약 #{row.reservationId}</span></div>
            <h3>{row.spaceName}</h3><p>{row.startTime.replace('T',' ').slice(0,16)} ~ {row.endTime.replace('T',' ').slice(0,16)}</p>
            {!compact && <p>{row.memberEmail}</p>}
          </div><span className={styles.detailLink}>상세 보기 →</span>
        </Link>)}
      </div>}
      {!compact && <Pagination page={actualPage} totalPages={Math.ceil(visible.length/size)} totalElements={visible.length} onChange={setPage}/>}
      <p className={styles.updated} aria-live="polite">{loading?'현황 갱신 중…':`${new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',hour:'2-digit',minute:'2-digit'}).format(data.fetchedAt)} 기준 · 1분마다 갱신`}{error?' · 갱신 실패로 이전 조회 결과를 표시합니다.':''}</p>
    </>}
  </section>;
}
