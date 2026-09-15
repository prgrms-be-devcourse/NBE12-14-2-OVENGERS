import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { getAdminSpaces } from '../../api/adminSpaceApi';
import { useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { ROUTES } from '../../constants/routePaths';
import { SPACE_STATUS_META } from '../../constants/enums';
import { formatWon } from '../../utils/price';
import { formatDateTime } from '../../utils/date';
import { MetaBadge } from '../../components/common/Badge';
import SpaceFilter from '../../components/space/SpaceFilter';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';

export default function AdminSpaceListPage() {
  const [filter, setFilter] = useState({ keyword: '', status: '' });
  const { page, size, setPage } = usePagination({ initialSize: 20 });

  const fetchSpaces = useCallback(
    () => getAdminSpaces({ page, size, keyword: filter.keyword, status: filter.status }),
    [page, size, filter.keyword, filter.status],
  );
  const { data, loading, error, run } = useAsync(fetchSpaces, [fetchSpaces]);
  const spaces = data?.content ?? [];

  return (
    <>
      <div className="pagehead">
        <div>
          <h1>공간 관리</h1>
          <p>등록한 공간의 요금과 운영시간을 관리합니다.</p>
        </div>
        <Link to={ROUTES.adminSpaceNew} className="btn primary">
          공간 등록
        </Link>
      </div>

      <SpaceFilter
        value={filter}
        showStatus
        onChange={(next) => {
          setFilter(next);
          setPage(0);
        }}
      />

      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={run} />

      {!loading && !error && spaces.length === 0 && (
        <EmptyState title="등록된 공간이 없습니다" description="첫 공간을 등록해 주세요." />
      )}

      {spaces.length > 0 && (
        <>
          <div className="tablebox">
            <table>
              <caption className="sr-only">공간 목록</caption>
              <thead>
                <tr>
                  <th scope="col">공간</th>
                  <th scope="col">위치</th>
                  <th scope="col">수용</th>
                  <th scope="col">30분당 요금</th>
                  <th scope="col">운영시간</th>
                  <th scope="col">상태</th>
                  <th scope="col">최종 수정</th>
                  <th scope="col">관리</th>
                </tr>
              </thead>
              <tbody>
                {spaces.map((space) => (
                  <tr key={space.id}>
                    <td>
                      {space.name}
                      <small>ID {space.id}</small>
                    </td>
                    <td>{space.location}</td>
                    <td>{space.capacity}명</td>
                    <td>{formatWon(space.pricePerSlot)}</td>
                    <td>
                      {space.openingTime} ~ {space.closingTime}
                    </td>
                    <td>
                      <MetaBadge meta={SPACE_STATUS_META[space.status]} />
                    </td>
                    <td>
                      {formatDateTime(space.updatedAt)}
                      <small>{space.updatedByNickname}</small>
                    </td>
                    <td>
                      <Link to={ROUTES.adminSpaceEdit(space.id)} className="btn small">
                        수정
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
