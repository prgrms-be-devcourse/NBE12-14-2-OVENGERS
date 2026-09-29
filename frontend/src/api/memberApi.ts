import type { Member } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api from './client';

export function getMe(): Promise<Member> {
  return api.get<Member>(API_ROUTES.members.me);
}

export function updateNickname(nickname: string): Promise<Member> {
  return api.patch<Member>(API_ROUTES.members.me, { nickname });
}
