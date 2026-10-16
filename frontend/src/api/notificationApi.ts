import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';
import type { Notification, Page, UnreadCountResponse, ReadAllResponse } from '../types/api';

export function getNotifications({ page = 0, size = 20 }: { page?: number; size?: number } = {}) {
  return api.get<Page<Notification>>(API_ROUTES.notifications.list, { query: { page, size } });
}

export function getUnreadCount() {
  return api.get<UnreadCountResponse>(API_ROUTES.notifications.unreadCount);
}

export function readAllNotifications() {
  return api.post<ReadAllResponse>(API_ROUTES.notifications.readAll);
}
