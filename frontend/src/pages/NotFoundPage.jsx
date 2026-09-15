import { Link } from 'react-router-dom';
import { ROUTES } from '../constants/routePaths';
import EmptyState from '../components/common/EmptyState';

export default function NotFoundPage() {
  return (
    <EmptyState
      title="페이지를 찾을 수 없습니다"
      description="주소가 바뀌었거나 삭제된 화면일 수 있습니다."
      action={
        <Link to={ROUTES.home} className="btn primary">
          메인으로
        </Link>
      }
    />
  );
}
