import type { ManagedCardView } from '../api/generated'

export const URGENCY_CODES = ['window', 'overdue', 'days3', 'days7', 'days15', 'days30', 'days60', 'beyond', 'noDate', 'cancelled']
export const TODO_CODES = ['window', 'overdue', 'days3', 'days7', 'noDate']

export function filterCardViews(cards: ManagedCardView[], filters: { keyword: string; country: string; status: string; urgency: string; todo: boolean }): ManagedCardView[] {
 const keyword = filters.keyword.trim().toLocaleLowerCase()
 const digits = keyword.replace(/[^0-9]/g, '')
 return cards.filter(card => {
  if (filters.todo && (!TODO_CODES.includes(card.urgencyCode ?? '') || card.status === 'cancelled')) return false
  if (filters.country && card.countryName !== filters.country) return false
  if (filters.status && card.status !== filters.status) return false
  if (filters.urgency && card.urgencyCode !== filters.urgency) return false
  if (!keyword) return true
  return [card.name, card.number, card.carrier].some(value => value?.toLocaleLowerCase().includes(keyword))
   || Boolean(digits && /^[+\d\s()-]+$/.test(keyword) && card.number?.replace(/[^0-9]/g, '').includes(digits))
 }).sort((a, b) => {
  const rank = (card: ManagedCardView) => {
   const index = URGENCY_CODES.indexOf(card.urgencyCode ?? 'noDate')
   return index < 0 ? URGENCY_CODES.length : index
  }
  return rank(a) - rank(b)
   || (a.nextRenewalEnd || a.nextRenewalAt || '\uffff').localeCompare(b.nextRenewalEnd || b.nextRenewalAt || '\uffff')
   || (a.name ?? '').localeCompare(b.name ?? '')
   || (a.id ?? '').localeCompare(b.id ?? '')
 })
}
