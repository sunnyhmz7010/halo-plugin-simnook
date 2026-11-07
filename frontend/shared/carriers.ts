const carrierAssets = '/plugins/simnook/assets/frontend/assets/carriers/'

export const BUILTIN_CARRIERS = [
  { name: '中国移动', aliases: ['China Mobile', 'CMCC'], file: 'china-mobile.ico' },
  { name: '中国联通', aliases: ['China Unicom', 'Unicom'], file: 'china-unicom.ico' },
  { name: '中国电信', aliases: ['China Telecom'], file: 'china-telecom.ico' },
  { name: 'Vodafone', aliases: [], file: 'vodafone.svg' },
  { name: 'Orange', aliases: [], file: 'orange.svg' },
  { name: 'AT&T', aliases: ['ATT'], file: 'atandt.svg' },
  { name: 'Deutsche Telekom', aliases: ['德国电信'], file: 'deutschetelekom.svg' },
  { name: 'O2', aliases: ['O₂'], file: 'o2.svg' },
  { name: 'Telenor', aliases: [], file: 'telenor.svg' },
  { name: 'Verizon', aliases: [], file: 'verizon.svg' },
  { name: 'Airtel', aliases: [], file: 'airtel.svg' },
  { name: 'Easy PASS', aliases: ['Easy PASS 預付卡', 'Easy PASS 预付卡', 'Easy PASS（澳门）', 'Easy PASS 預付卡（澳门）', '中国电信澳门', '中國電信澳門', 'China Telecom Macau'], file: 'easy-pass.ico' },
  { name: 'Club Sim', aliases: ['Club Sim（香港）'], file: 'club-sim.png' },
  { name: 'RedPocket', aliases: ['Red Pocket Mobile', 'RedPocket（美国）'], file: 'redpocket.png' },
  { name: 'eSIM.GG', aliases: [], file: 'esim-gg.png' },
  { name: 'DITO', aliases: ['DITO eSIM', 'DITO Telecommunity', 'DITO（菲律宾）', 'DITO eSIM（菲律宾）'], file: 'dito.png' },
]

export function carrierLogo(carrier: string | undefined): string | undefined {
  const key = (value: string) => value.normalize('NFKC').toLowerCase().replace(/[\s\p{P}\p{S}]+/gu, '')
  const name = key(carrier ?? '')
  const found = BUILTIN_CARRIERS.find(item => [item.name, ...item.aliases].some(alias => key(alias) === name))
  return found ? carrierAssets + found.file : undefined
}

export const builtinLogoOptions = BUILTIN_CARRIERS.map(item => ({
  label: item.name,
  value: carrierAssets + item.file,
}))
