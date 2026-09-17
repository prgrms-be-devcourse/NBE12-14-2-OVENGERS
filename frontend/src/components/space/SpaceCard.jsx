import Link from 'next/link';
import { ROUTES } from '../../constants/routePaths';
import { SPACE_STATUS, SPACE_STATUS_META } from '../../constants/enums';
import { formatPricePerSlot } from '../../utils/price';
import { MetaBadge } from '../common/Badge';

/**
 * availability 가 있으면 그날의 슬롯 점유 현황을 막대로 보여줍니다.
 * (프로토타입의 .availability 표시와 동일)
 */
export default function SpaceCard({ space, availability }) {
  const freeCount = availability?.filter((slot) => slot.available).length ?? 0;

  return (
    <article className="spacecard">
      <div className="photo">
        {space.imagePath && <img src={space.imagePath} alt="" />}
        <MetaBadge meta={SPACE_STATUS_META[space.status]} />
      </div>
      <div className="cardbody">
        <h2>{space.name}</h2>
        <p>
          {space.location} · 최대 {space.capacity}명
        </p>

        {availability && (
          <>
            <div className="availability" aria-hidden="true">
              {availability.map((slot) => (
                <i key={slot.startTime} className={slot.available ? 'free' : undefined} />
              ))}
            </div>
            <div className="slotslabel">
              <span>{space.openingTime}</span>
              <span>
                예약 가능 {freeCount} / {availability.length} 슬롯
              </span>
              <span>{space.closingTime}</span>
            </div>
          </>
        )}

        <div className="divider" />
        <div className="row between">
          <span className="price">
            {formatPricePerSlot(space.pricePerSlot)}
          </span>
        </div>

        <Link
          href={ROUTES.spaceDetail(space.id)}
          className={`btn wide ${space.status === SPACE_STATUS.ACTIVE ? 'primary' : ''}`}
        >
          {space.status === SPACE_STATUS.ACTIVE ? '예약하기' : '상세 보기'}
        </Link>
      </div>
    </article>
  );
}
