import { defineComponent, h, nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { useSimnookI18n } from './locales'

describe('Console 宿主语言', () => {
 it('继承宿主切换并保留自定义选项原文', async () => {
  const host = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {}, en: {} } })
  const component = defineComponent({ setup() {
   const { t, label, recordTypeLabel } = useSimnookI18n()
   return () => h('div', [t('号卡'), label('🇭🇰 中国香港'), label('我自己的设备'), t('填写国际格式，如 +85255550123。'), recordTypeLabel('Renewal'), recordTypeLabel('我的特殊充值活动'), t('分页总数', { count: 4 })].join('|'))
  } })
  const wrapper = mount(component, { global: { plugins: [host] } })
  expect(wrapper.text()).toContain('号卡|🇭🇰 中国香港|我自己的设备')
  host.global.locale.value = 'en'
  await nextTick()
  expect(wrapper.text()).toContain('Cards|🇭🇰 Hong Kong|我自己的设备')
  expect(wrapper.text()).toContain('Use international format')
  expect(wrapper.text()).toContain('Renewal|我的特殊充值活动|4 items')
  wrapper.unmount()
 })
})
