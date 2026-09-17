import { Route, Routes } from 'react-router-dom';
import { ROUTES } from '../constants/routePaths';

import UserLayout from '../components/layout/UserLayout';
import AdminLayout from '../components/layout/AdminLayout';
import ProtectedRoute from './ProtectedRoute';
import AdminRoute from './AdminRoute';

import HomePage from '../pages/HomePage';
import NotFoundPage from '../pages/NotFoundPage';
import LoginPage from '../pages/auth/LoginPage';
import SignupPage from '../pages/auth/SignupPage';
import SpaceListPage from '../pages/space/SpaceListPage';
import SpaceDetailPage from '../pages/space/SpaceDetailPage';
import ReservationListPage from '../pages/reservation/ReservationListPage';
import ReservationDetailPage from '../pages/reservation/ReservationDetailPage';
import PaymentPage from '../pages/reservation/PaymentPage';
import DoorTerminalPage from '../pages/door/DoorTerminalPage';
import AdminSpaceListPage from '../pages/admin/AdminSpaceListPage';
import AdminSpaceFormPage from '../pages/admin/AdminSpaceFormPage';
import AdminReservationListPage from '../pages/admin/AdminReservationListPage';
import AdminReservationDetailPage from '../pages/admin/AdminReservationDetailPage';
import AdminMemberListPage from '../pages/admin/AdminMemberListPage';

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<UserLayout />}>
        <Route path={ROUTES.home} element={<HomePage />} />
        <Route path={ROUTES.login} element={<LoginPage />} />
        <Route path={ROUTES.signup} element={<SignupPage />} />
        <Route path={ROUTES.spaces} element={<SpaceListPage />} />
        <Route path={ROUTES.spaceDetail()} element={<SpaceDetailPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path={ROUTES.door} element={<DoorTerminalPage />} />
          <Route path={ROUTES.reservations} element={<ReservationListPage />} />
          <Route path={ROUTES.reservationPayment()} element={<PaymentPage />} />
          <Route path={ROUTES.reservationDetail()} element={<ReservationDetailPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>

      <Route element={<AdminRoute />}>
        <Route element={<AdminLayout />}>
          <Route path={ROUTES.adminSpaces} element={<AdminSpaceListPage />} />
          <Route path={ROUTES.adminSpaceNew} element={<AdminSpaceFormPage mode="create" />} />
          <Route path={ROUTES.adminSpaceEdit()} element={<AdminSpaceFormPage mode="edit" />} />
          <Route path={ROUTES.adminReservations} element={<AdminReservationListPage />} />
          <Route path={ROUTES.adminReservationDetail()} element={<AdminReservationDetailPage />} />
          <Route path={ROUTES.adminMembers} element={<AdminMemberListPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
