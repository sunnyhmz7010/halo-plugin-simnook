import { describe, expect, it } from 'vitest'
import { cardName, copyCardConfiguration, cycleChoice, newExpense, suggestedExtension } from './cardEntry'
import { carrierLogo } from '../../../frontend/shared/carriers'
import type { CardSpec } from '../api/generated'

describe('号卡录入默认值', () => {
 it('复用配置时只复制套餐字段并独立保存数组', () => {
  const source: CardSpec = {
   name: '示例运营商 · 1234', number: '+85255551234', currency: 'HKD', account: 'private-login',
   balance: 45, cancelled: true, nextRenewalAt: '2026-12-01',
   keepAlive: ['自动扣费'], records: [{ amount: 100 }],
  }
  const copied = copyCardConfiguration(source)
  expect(copied).toMatchObject({ name: '', carrier: '示例运营商', currency: 'HKD', records: [] })
  expect(copied).not.toHaveProperty('number')
  expect(copied).not.toHaveProperty('account')
  expect(copied).not.toHaveProperty('balance')
  expect(copied).not.toHaveProperty('cancelled')
  expect(copied).not.toHaveProperty('nextRenewalAt')
  copied.keepAlive!.push('购买续费套餐')
  expect(source.keepAlive).toEqual(['自动扣费'])
 })

 it('新消费沿用币种，同时保留金额与汇率待填', () => {
  const card: CardSpec = { name: '测试卡', carrier: '运营商', number: '+8613800123456', currency: 'USD' }
  const record = newExpense(card, '2026-11-07')
  expect(record).toMatchObject({ type: '首次购买/开卡', date: '2026-11-07', currency: 'USD' })
  expect(record.title).toContain('运营商 · 3456')
  expect(record.amount).toBeUndefined()
  expect(record.rate).toBeUndefined()
  expect(newExpense({ ...card, records: [{}] }).type).toBe('续费/保号')
 })

 it('保留已有的自定义周期，建议有效期从有效的消费日或当前到期日计算', () => {
  expect(cycleChoice({ name: '卡', cycleDays: 30, cycleLabel: '每 30 天' })).toBe('30')
  expect(cycleChoice({ name: '卡', cycleDays: 45, cycleLabel: '每 30 天' })).toBe('custom')
  expect(suggestedExtension({ name: '卡', cycleDays: 30, expiresAt: '2026-12-01' }, '2026-11-07', '续费/保号')).toBe('2026-12-31')
  expect(suggestedExtension({ name: '卡', cycleDays: 30, expiresAt: '2026-10-01' }, '2026-11-07', '续费/保号')).toBe('2026-12-07')
  expect(suggestedExtension({ name: '卡', cycleDays: 30 }, '2026-02-31', '首次购买/开卡')).toBe('')
  expect(suggestedExtension({ name: '卡', cycleDays: 30 }, '2026-11-07', '额外充值')).toBe('')
  expect(suggestedExtension({ name: '卡', cycleDays: 30, expiresAt: '2026-12-01' }, '2026-11-07', 'Renewal')).toBe('2026-12-31')
  expect(suggestedExtension({ name: '卡', cycleDays: 30 }, '2026-11-07', 'Initial purchase / activation')).toBe('2026-12-07')
  expect(suggestedExtension({ name: '卡', cycleDays: 30 }, '2026-11-07', 'Top-up')).toBe('')
 })

 it('名称只带号码末四位，内置图标按运营商别名匹配', () => {
  expect(cardName('中国移动', '+86 13800123456')).toBe('中国移动 · 3456')
  expect(carrierLogo('China Mobile')).toBe(carrierLogo('中国移动'))
  expect(carrierLogo('AT & T')).toContain('/atandt.svg')
  expect(carrierLogo('未知运营商')).toBeUndefined()
 })
})
