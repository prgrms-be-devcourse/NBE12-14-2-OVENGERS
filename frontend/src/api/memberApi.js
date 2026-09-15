import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';

export function getMe() {
  return api.get(API_ROUTES.members.me);
}
