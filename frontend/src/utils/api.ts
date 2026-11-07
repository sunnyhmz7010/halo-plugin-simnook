import type { CardsResponse, SiteInfo } from '@shared/types'
import { currentLocale, i18n, type AppLocale } from '@/i18n'

async function get<T>(path: string, locale: AppLocale = currentLocale()): Promise<T> {
  const response = await fetch('/apis/api.simnook.halo.run/v1alpha1/' + path, { credentials: 'same-origin', headers: { 'Accept-Language': locale } })
  if (!response.ok)
    throw new Error(i18n.global.t('errors.http.requestFailed', { status: response.status }))
  return response.json()
}

async function getCards(loggedIn: boolean, locale: AppLocale = currentLocale()): Promise<CardsResponse> {
  if (loggedIn) {
    const response = await fetch('/apis/console.api.simnook.halo.run/v1alpha1/cardviews', {
      credentials: 'same-origin',
      cache: 'no-store',
      redirect: 'manual',
      headers: { 'Accept-Language': locale },
    })
    if (response.ok)
      return response.json()
    if (response.type !== 'opaqueredirect' && response.status !== 401 && response.status !== 403)
      throw new Error(i18n.global.t('halo.managementLoadFailed', { status: response.status }))
  }
  return get<CardsResponse>('cards', locale)
}

export const api = { getSite: () => get<SiteInfo>('site'), getCards }
