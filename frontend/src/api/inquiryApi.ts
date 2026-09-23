import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';
import type { Page } from '../types/api';

export type InquiryStatus = 'WAITING' | 'ANSWERED';
export interface Inquiry {
  id: number;
  memberId: number;
  title: string;
  content: string;
  status: InquiryStatus;
  answerContent: string | null;
  answeredByMemberId: number | null;
  answeredAt: string | null;
  createdAt: string;
}
export interface InquiryInput { title: string; content: string }
export function getInquiries({ admin = false, page = 0, size = 10, status = '' }: {
  admin?: boolean; page?: number; size?: number; status?: InquiryStatus | '';
} = {}) {
  return api.get<Page<Inquiry>>(admin ? API_ROUTES.admin.inquiries : API_ROUTES.inquiries.list, {
    query: { page, size, sort: 'createdAt,desc', ...(admin ? { status } : {}) },
  });
}
export function getInquiry(id: string | number, admin = false) {
  return api.get<Inquiry>(admin ? API_ROUTES.admin.inquiry(id) : API_ROUTES.inquiries.detail(id));
}
export function createInquiry(input: InquiryInput) {
  return api.post<Inquiry>(API_ROUTES.inquiries.list, input);
}
export function updateInquiry(id: string | number, input: InquiryInput) {
  return api.patch<Inquiry>(API_ROUTES.inquiries.detail(id), input);
}
export function answerInquiry(id: string | number, content: string) {
  return api.post<Inquiry>(API_ROUTES.admin.inquiryAnswer(id), { content });
}
