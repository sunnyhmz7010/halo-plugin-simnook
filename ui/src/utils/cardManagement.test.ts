import { describe, expect, it } from 'vitest'
import type { ManagedCardView } from '../api/generated'
import { filterCardViews } from './cardManagement'

const defaults = { keyword: '', country: '', status: '', urgency: '', todo: false }
describe('号卡管理筛选', () => {
 const cards: ManagedCardView[] = [
  { id: 'a', name: '移动 · 1234', carrier: '中国移动', number: '+86 138 0000 1234', countryName: '中国', status: 'active', urgencyCode: 'days7', nextRenewalAt: '2026-11-15' },
  { id: 'b', name: 'Second', countryName: '美国', status: 'expired', urgencyCode: 'overdue', nextRenewalAt: '2026-11-01' },
  { id: 'c', name: 'No date', status: 'active', urgencyCode: 'noDate' },
  { id: 'd', name: 'Closed', status: 'cancelled', urgencyCode: 'cancelled' },
  { id: 'e', name: 'Later', status: 'active', urgencyCode: 'days30' },
 ]
 it('在完整数据上搜索，并支持格式化号码和组合筛选', () => {
  expect(filterCardViews(cards, { ...defaults, keyword: '+8613800001234' }).map(card => card.id)).toEqual(['a'])
  expect(filterCardViews(cards, { ...defaults, keyword: 'second', country: '美国', status: 'expired', urgency: 'overdue' }).map(card => card.id)).toEqual(['b'])
  expect(filterCardViews(cards, { ...defaults, keyword: 'Second', country: '中国' })).toEqual([])
 })
 it('待办包括缺日期，排除注销和远期，并稳定按紧急度排序', () => {
  expect(filterCardViews(cards, { ...defaults, todo: true }).map(card => card.id)).toEqual(['b', 'a', 'c'])
  expect(filterCardViews(cards, defaults).map(card => card.id)).toEqual(['b', 'a', 'e', 'c', 'd'])
  expect(cards[0]?.id).toBe('a')
 })
})
