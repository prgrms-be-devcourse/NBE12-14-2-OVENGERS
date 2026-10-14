import type { ChargeOrder, ConfirmChargeOrderRequest } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api from './client';

export function createChargeOrder(amount: number): Promise<ChargeOrder> {
  return api.post<ChargeOrder>(API_ROUTES.credits.chargeOrders, { amount });
}
export function confirmChargeOrder(
  request: ConfirmChargeOrderRequest,
  idempotencyKey: string,
): Promise<ChargeOrder> {
  return api.post<ChargeOrder>(
    API_ROUTES.credits.confirmChargeOrder,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  );
}

export function getChargeOrder(orderId: string): Promise<ChargeOrder> {
  return api.get<ChargeOrder>(API_ROUTES.credits.chargeOrder(orderId));
}
