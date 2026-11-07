/**
 * App initialization: fetches site metadata and card data.
 * 公开访客和号卡管理员使用各自的 Halo 接口。
 */

import { nextTick, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { useCardsStore } from '@/stores/cards'
import { api } from '@/utils/api'

const REFRESH_INTERVAL_MS = 10 * 60 * 1000

let refreshTimer: ReturnType<typeof setInterval> | null = null
let lastRefreshAt = 0
let visibilityHandler: (() => void) | null = null
let stopTitleWatch: (() => void) | null = null
let stopLocaleWatch: (() => void) | null = null

export async function refreshData(): Promise<void> {
  const appStore = useAppStore()
  const cardsStore = useCardsStore()
  try {
    const site = await api.getSite()
    appStore.publicSettings = site
    await nextTick()
    const cards = await api.getCards(site.loggedIn, appStore.locale)
    appStore.updateLoginState(site.loggedIn)
    cardsStore.setCards(cards)
    appStore.connectionError = false
    lastRefreshAt = Date.now()
  }
  catch (error) {
    if (cardsStore.full)
      cardsStore.setCards({ cards: [], syncedAt: null, demo: false, full: false })
    console.error('[init] Failed to fetch data:', error)
    appStore.connectionError = true
    throw error
  }
}

export async function initApp(): Promise<void> {
  const appStore = useAppStore()
  try {
    await refreshData()
  }
  finally {
    appStore.loading = false
  }

  stopTitleWatch = watch(() => appStore.sitename, (name) => {
    document.title = name
  }, { immediate: true })

  // 直接传入本次选择，避免先于 i18n 同步时仍用上一次语言请求检查提示。
  stopLocaleWatch = watch(() => appStore.locale, async (locale, _previous, onCleanup) => {
    const cardsStore = useCardsStore()
    if (!cardsStore.full && !cardsStore.demo)
      return
    let active = true
    onCleanup(() => { active = false })
    try {
      const cards = await api.getCards(appStore.isLoggedIn, locale)
      if (active)
        cardsStore.setCards(cards)
    }
    catch (error) {
      console.error('[init] Failed to reload localized cards:', error)
    }
  })

  refreshTimer = setInterval(() => {
    if (document.visibilityState === 'visible')
      refreshData().catch(error => console.warn('[SIMnook] 自动刷新失败', error))
  }, REFRESH_INTERVAL_MS)

  visibilityHandler = () => {
    if (document.visibilityState === 'visible' && Date.now() - lastRefreshAt > REFRESH_INTERVAL_MS)
      refreshData().catch(error => console.warn('[SIMnook] 自动刷新失败', error))
  }
  document.addEventListener('visibilitychange', visibilityHandler)
}

export function destroyInitManager(): void {
  if (refreshTimer)
    clearInterval(refreshTimer)
  refreshTimer = null
  if (visibilityHandler)
    document.removeEventListener('visibilitychange', visibilityHandler)
  visibilityHandler = null
  stopTitleWatch?.()
  stopTitleWatch = null
  stopLocaleWatch?.()
  stopLocaleWatch = null
}
