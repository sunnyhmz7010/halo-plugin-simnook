import type { CardSpec, ExpenseRecord } from '../api/generated'

export const CYCLE_OPTIONS = [
  { value: '30', label: '每 30 天' },
  { value: '90', label: '每 90 天' },
  { value: '180', label: '每 180 天' },
  { value: '365', label: '每 365 天' },
  { value: 'varies', label: '不固定' },
  { value: 'custom', label: '自定义' },
]

export function recordKind(type: string | undefined): 'purchase' | 'renewal' | 'topup' | 'other' {
 const value = (type ?? '').trim().toLowerCase()
 if (['购卡', '开卡', '首次', '首购', 'purchase', 'activation', 'initial'].some(word => value.includes(word))) return 'purchase'
 if (['续费', '保号', 'renew'].some(word => value.includes(word))) return 'renewal'
 if (['充值', 'top-up', 'top up', 'topup', 'recharge'].some(word => value.includes(word))) return 'topup'
 return 'other'
}

export function cycleChoice(card: CardSpec): string {
  if (card.cycleDays == null && !card.cycleLabel) return ''
  const days = card.cycleDays ?? Number(card.cycleLabel?.match(/[0-9]+/)?.[0] ?? 0)
  const preset = CYCLE_OPTIONS.find(option => option.value === String(days))
  if (preset && (!card.cycleLabel || card.cycleLabel === preset.label)) return preset.value
  return days === 0 && card.cycleLabel?.startsWith('不固定') ? 'varies' : 'custom'
}

export function cardName(carrier: string | undefined, number: string | undefined): string {
  if (!carrier?.trim()) return ''
  const digits = (number ?? '').replace(/[^0-9]/g, '')
  return [carrier.trim(), digits.length >= 4 ? digits.slice(-4) : ''].filter(Boolean).join(' · ')
}

export function copyCardConfiguration(source: CardSpec): CardSpec {
  return JSON.parse(JSON.stringify({
    name: '',
    carrier: source.carrier || source.name.split(/\s*[·•|]\s*/)[0],
    region: source.region,
    countryName: source.countryName,
    typeKind: source.typeKind,
    typeColor: source.typeColor,
    typeLabel: source.typeLabel,
    logo: source.logo,
    keepAlive: source.keepAlive,
    cycleDays: source.cycleDays,
    cycleLabel: source.cycleLabel,
    price: source.price,
    currency: source.currency,
    paymentAccountIds: source.paymentAccountIds,
    records: [],
  }))
}

export function entryExpiry(card: CardSpec): string | undefined {
  return card.expiresAt || card.records?.filter(record => record.date && record.extendsTo)
    .sort((a, b) => b.date!.localeCompare(a.date!) || b.extendsTo!.localeCompare(a.extendsTo!))[0]?.extendsTo
}

export function localDay(): string {
  const date = new Date()
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}

export function newExpense(card: CardSpec, accountIds: string[], today = localDay()): ExpenseRecord {
  const type = card.records?.length ? '续费/保号' : '首次购买/开卡'
  const defaults = card.paymentAccountIds?.filter(id => accountIds.includes(id)) ?? []
  return {
    type,
    date: today,
    currency: card.currency,
    title: [cardName(card.carrier, card.number) || card.name, type, today].filter(Boolean).join(' · '),
    paymentAccountIds: defaults.length === 1 ? defaults : [],
  }
}

export function suggestedExtension(card: CardSpec, date: string | undefined, type: string | undefined): string {
  const days = card.cycleDays ?? Number(card.cycleLabel?.match(/[0-9]+/)?.[0] ?? 0)
  const kind = recordKind(type)
  if (!date || days <= 0 || !Number.isInteger(days) || !['purchase', 'renewal'].includes(kind)) return ''
  const expiry = kind === 'renewal' ? entryExpiry(card) : undefined
  const start = expiry && expiry > date ? expiry : date
  const value = new Date(start + 'T00:00:00Z')
  if (!Number.isFinite(value.getTime()) || value.toISOString().slice(0, 10) !== start) return ''
  value.setUTCDate(value.getUTCDate() + days)
  return Number.isFinite(value.getTime()) ? value.toISOString().slice(0, 10) : ''
}
