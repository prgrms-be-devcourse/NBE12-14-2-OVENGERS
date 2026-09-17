'use client';

import Link from 'next/link';
import { ROUTES } from '../constants/routePaths';
import { useAuth } from '../hooks/useAuth';

const STEPS = [
  {
    num: 'STEP 1',
    title: '공간과 시간을 고릅니다',
    body: '30분 단위로 나뉜 슬롯에서 시작과 끝을 고르면 이용 요금이 바로 계산됩니다.',
  },
  {
    num: 'STEP 2',
    title: '10분 안에 결제를 확인합니다',
    body: '슬롯을 확보하면 결제 대기(HOLD) 상태가 되고, 10분 안에 크레딧으로 결제하면 예약이 확정됩니다. 결제하지 않으면 자동으로 취소되어 슬롯이 반환됩니다.',
  },
  {
    num: 'STEP 3',
    title: '예약이 곧 출입 권한입니다',
    body: '예약자 본인만, 정해진 시간에만 출입할 수 있습니다. 취소하면 키도 함께 무효가 됩니다.',
  },
];

export default function HomePage() {
  const { isAuthenticated } = useAuth();

  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">MEETING ROOM · SHARED OFFICE</p>
          <h1>
            시간을 <span>Slot</span>으로 나누고,
            <br />
            예약한 Slot이 하나의 <span>Key</span>가 됩니다.
          </h1>
          <p>
            회의실과 공유오피스를 30분 단위로 예약하고, 예약 상태와 이용 시간에 맞춰 출입 권한을
            받습니다. 취소하거나 이용이 끝나면 출입도 함께 닫힙니다.
          </p>
          <div className="actions">
            <Link href={ROUTES.spaces} className="btn primary">
              공간 둘러보기
            </Link>
            {!isAuthenticated && (
              <Link href={ROUTES.signup} className="btn">
                회원가입
              </Link>
            )}
          </div>
          <p className="note">
            학습용 서비스입니다. 실제 결제와 스마트락은 연결되어 있지 않습니다.
          </p>
        </div>
        <div className="hero-art" aria-hidden="true" />
      </section>

      <section className="section">
        <h2>이렇게 이용합니다</h2>
        <div className="steps">
          {STEPS.map((step) => (
            <div key={step.num}>
              <p className="step-num">{step.num}</p>
              <h3>{step.title}</h3>
              <p>{step.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="section">
        <div className="callout">
          <div>
            <h3>같은 시간, 두 예약은 만들어지지 않습니다</h3>
            <p>
              화면에 예약 가능으로 보여도 확정은 서버가 판단합니다. 같은 슬롯에 요청이 몰려도
              한 건만 확정되고 나머지는 다른 시간을 안내받습니다.
            </p>
          </div>
        </div>
      </section>
    </>
  );
}
