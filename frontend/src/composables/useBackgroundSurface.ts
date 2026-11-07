import { computed } from 'vue'
import { useAppStore } from '@/stores/app'

export function useBackgroundSurface() {
  const appStore = useAppStore()
  const hasCustomBackground = computed(() => !!appStore.currentBackgroundUrl)

  function pickSurfaceClass(defaultClass: string, customBackgroundClass: string): string {
    return hasCustomBackground.value ? customBackgroundClass : defaultClass
  }

  return {
    hasCustomBackground,
    pickSurfaceClass,
  }
}
