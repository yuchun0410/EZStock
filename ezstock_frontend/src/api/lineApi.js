import { apiDelete, apiGet, apiPost } from './client'

/** { available, linked, botId } */
export function getLineStatus() {
  return apiGet('/api/members/me/line')
}

/** { code, expiresInSeconds } */
export function createBindingCode() {
  return apiPost('/api/members/me/line/binding-code')
}

export function unlinkLine() {
  return apiDelete('/api/members/me/line')
}
