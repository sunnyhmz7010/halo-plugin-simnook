import { useI18n } from 'vue-i18n'

const en = {
 '分页总数': '{count} items', '页': 'page', '条 / 页': 'per page',
 '号卡': 'Cards', '支付账户': 'Payment accounts', '基本信息': 'Basic information', '续费与消费': 'Renewal and expenses', '更多设置': 'More settings',
 '实体 SIM': 'Physical SIM', 'eSIM · 直写手机': 'eSIM · built into phone', 'eSIM · 卡托': 'eSIM · adapter', '虚拟号码（仅 App）': 'Virtual number (app only)',
 '中性（默认）': 'Neutral (default)', '灰色': 'Gray', '棕色': 'Brown', '橙色': 'Orange', '黄色': 'Yellow', '绿色': 'Green', '蓝色': 'Blue', '紫色': 'Purple', '粉色': 'Pink', '红色': 'Red',
 '充值余额': 'Top up balance', '购买/启用增值服务': 'Buy or enable an add-on', '有效使用（含收发短信）': 'Regular use (incl. SMS)', '产生余额变动（如发漫游短信）': 'Balance change (e.g. roaming SMS)', '自动扣费': 'Auto-charge', '购买续费套餐': 'Buy a renewal plan',
 '主力机': 'Primary phone', '备用机': 'Spare phone', '卡托': 'Adapter', '未装机': 'Not installed', '银行/支付': 'Banking / payments', '其他': 'Other',
 '🇨🇳 中国': '🇨🇳 China', '🇭🇰 中国香港': '🇭🇰 Hong Kong', '🇲🇴 中国澳门': '🇲🇴 Macao', '🇹🇼 中国台湾': '🇹🇼 Taiwan', '🇯🇵 日本': '🇯🇵 Japan', '🇰🇷 韩国': '🇰🇷 South Korea', '🇸🇬 新加坡': '🇸🇬 Singapore', '🇲🇾 马来西亚': '🇲🇾 Malaysia',
 '🇹🇭 泰国': '🇹🇭 Thailand', '🇵🇭 菲律宾': '🇵🇭 Philippines', '🇮🇩 印度尼西亚': '🇮🇩 Indonesia', '🇻🇳 越南': '🇻🇳 Vietnam', '🇱🇰 斯里兰卡': '🇱🇰 Sri Lanka', '🇦🇪 阿联酋': '🇦🇪 UAE', '🇹🇷 土耳其': '🇹🇷 Turkey', '🇬🇧 英国': '🇬🇧 United Kingdom',
 '🇩🇪 德国': '🇩🇪 Germany', '🇫🇷 法国': '🇫🇷 France', '🇳🇱 荷兰': '🇳🇱 Netherlands', '🇪🇸 西班牙': '🇪🇸 Spain', '🇮🇹 意大利': '🇮🇹 Italy', '🇵🇱 波兰': '🇵🇱 Poland', '🇪🇪 爱沙尼亚': '🇪🇪 Estonia', '🇨🇭 瑞士': '🇨🇭 Switzerland',
 '🇺🇸 美国': '🇺🇸 United States', '🇨🇦 加拿大': '🇨🇦 Canada', '🇲🇽 墨西哥': '🇲🇽 Mexico', '🇧🇷 巴西': '🇧🇷 Brazil', '🇦🇺 澳大利亚': '🇦🇺 Australia', '🇳🇿 新西兰': '🇳🇿 New Zealand',
 '首次购买/开卡': 'Initial purchase', '续费/保号': 'Renewal', '额外充值': 'Extra top-up', '其他费用': 'Other fees',
 '每 30 天': 'Every 30 days', '每 90 天': 'Every 90 days', '每 180 天': 'Every 180 days', '每 365 天': 'Every 365 days', '不固定': 'Irregular', '自定义': 'Custom',
 '未找到的账户': 'Account not found', '加载失败，请重试': 'Failed to load. Please try again.', '扣款账户选项加载失败': 'Failed to load payment account options', '已加载最新版本，请重新编辑': 'Latest version loaded. Please edit again.',
 '重新加载失败，卡片可能已删除': 'Could not reload. The card may have been deleted.', '请填写续费起始日，且截止日不能早于起始日': 'Enter a renewal start date; the deadline must not precede it.',
 '数据检查加载失败': 'Failed to load data checks', '已保存': 'Saved', '其他人已修改此卡片，请重新加载后编辑': 'This card was changed elsewhere. Reload before editing.', '保存失败，请检查表单或稍后重试': 'Could not save. Check the form or try again later.',
 '删除号卡': 'Delete card', '删除': 'Delete', '已删除': 'Deleted', '删除失败': 'Could not delete', '删除号卡确认': 'Delete “{name}” and all its expense records?',
 '新增号卡': 'Add card', '新增支付账户': 'Add payment account', '加载失败': 'Failed to load', '请检查权限或网络后重试': 'Check your permissions and connection, then try again.',
 '还没有号卡': 'No cards yet', '首次安装为空库，点击新增号卡开始录入。': 'Start by adding your first card.', '未填写号码': 'Number not provided', '已注销': 'Closed', '消费记录数量': '{count} expense record | {count} expense records',
 '数据检查': 'Data check', '再添同款': 'Add similar', '编辑': 'Edit', '编辑号卡': 'Edit card', '保存': 'Save', '重新加载': 'Reload', '编辑冲突提示': 'This resource has changed. Reloading discards your unsaved edits.',
 '运营商': 'Carrier', '选择或输入运营商': 'Select or enter a carrier', '运营商图标预览': 'Carrier logo preview', '已匹配运营商图标，可在更多设置中更换。': 'A carrier logo was matched. Change it under More settings.',
 '完整号码（私有）': 'Full number (private)', '填写国际格式，如 +85255550123。': 'Use international format, e.g. +85255550123.', '号码国家': 'Number country', '选择号码国家': 'Select a country or region',
 '自定义地区请用「国旗 emoji + 空格 + 名称」，用于地图定位。': 'For a custom region, use a flag emoji followed by a space and its name for map placement.', '卡片类型': 'Card type', '选择卡片类型': 'Select a card type',
 '展示名称（公开）': 'Display name (public)', '自动生成「运营商 · 号码末四位」，可以修改。': 'Generated from the carrier and last four digits. You can edit it.',
 '保号动作': 'Keep-alive actions', '续费周期': 'Renewal cycle', '选择续费周期': 'Select a renewal cycle', '续费周期（天）': 'Renewal cycle (days)', '周期说明': 'Cycle description',
 '续费价格': 'Renewal price', '续费币种': 'Renewal currency', '选择续费币种': 'Select a renewal currency', '新增时按号码国家推荐币种，可以修改。': 'Suggested from the number country for new cards. You can change it.',
 '默认扣款账户（私有）': 'Default payment accounts (private)', '选择支付账户': 'Select payment accounts', '消费记录': 'Expense records', '类型图标颜色': 'Card icon color', '随卡片类型': 'Use the card-type default',
 '具体卡片类型（私有）': 'Detailed card type (private)', '内置运营商图标': 'Built-in carrier logo', '按运营商自动匹配': 'Match the carrier automatically', '自定义运营商图标': 'Custom carrier logo', '选择或上传后优先使用此图标。': 'A selected or uploaded logo takes priority.',
 '有效期（旧版手填）': 'Expiry date (legacy override)', '清空后，按交易日期最近一条填写了延长日期的记录计算，同日取最晚。': 'Clear to derive it from the latest dated transaction with a valid-until date; the latest extension wins for the same day.',
 '首次购买日期（旧版手填）': 'First purchase date (legacy override)', '清空后取最早一笔消费日期。': 'Clear to use the earliest transaction date.', '下次续费起始日': 'Next renewal start date', '留空时跟随有效期。': 'Leave empty to use the expiry date.',
 '续费截止日': 'Renewal deadline', '可选。填写后，前台显示续费日期区间。': 'Optional. Shows a renewal date range on the public page.', '已注销（旧版标记）': 'Closed (legacy flag)', '停用日期': 'Deactivation date', '填写后自动标记为已注销。': 'Entering a date marks the card as closed.',
 '余额（私有）': 'Balance (private)', '余额更新日期（私有）': 'Balance update date (private)', '主要使用设备（私有）': 'Primary device (private)', '选择主要使用设备': 'Select a primary device',
 '账户名称（私有）': 'Account name (private)', '仅记录名称，不存储密码、密钥或令牌。': 'Store names only, without passwords, keys, or tokens.', '旧版扣款账户名称（私有）': 'Legacy payment account names (private)',
 '关联对应的支付账户后，可以清除旧名称。': 'You can clear the legacy names after linking the corresponding payment accounts.', '绑定的服务（私有）': 'Linked services (private)', '旧版数据检查备注（私有）': 'Legacy data-check notes (private)', '展示排序': 'Display order',
 '号卡没有检查提示': 'No card warnings', '未命名消费记录': 'Untitled expense record', '银行卡': 'Bank card', '虚拟卡': 'Virtual card', '支付宝': 'Alipay', '微信支付': 'WeChat Pay', '平台余额': 'Platform balance',
 '正常': 'Normal', '余额不足': 'Low balance', '已冻结': 'Frozen', '支付账户加载失败': 'Failed to load payment accounts', '重新加载支付账户失败': 'Could not reload the payment account',
 '支付账户已保存': 'Payment account saved', '其他人已修改此账户，请重新加载后编辑': 'This account was changed elsewhere. Reload before editing.', '支付账户保存失败': 'Could not save the payment account',
 '加载关联失败': 'Failed to load linked records', '删除支付账户': 'Delete payment account', '删除支付账户确认': 'Delete “{name}”?', '此账户仍有关联，请先解除关联或将账户状态设为已注销': 'This account is still linked. Remove its links or mark it as closed first.',
 '支付账户已删除': 'Payment account deleted', '支付账户删除失败': 'Could not delete the payment account', '还没有支付账户': 'No payment accounts yet', '可录入用于续费的银行卡或钱包。': 'Add a bank card or wallet used for renewals.',
 '未填写类型': 'Type not provided', '未填写状态': 'Status not provided', '关联': 'Links', '编辑支付账户': 'Edit payment account', '账户名称': 'Account name', '例如 示例银行 · 4242。仅记录账户名称。': 'For example: Sample Bank · 4242. Store the account name only.',
 '账户类型': 'Account type', '选择账户类型': 'Select an account type', '账户状态': 'Account status', '选择账户状态': 'Select an account status', '暂无关联': 'No linked records', '默认扣款账户': 'Default payment account',
 '消费类型': 'Expense type', '选择消费类型': 'Select an expense type', '交易日期': 'Transaction date', '原币金额': 'Amount in original currency', '币种': 'Currency', '选择币种': 'Select a currency',
 '录入时汇率': 'Exchange rate at entry', '填写这笔交易实际使用的汇率，1 单位原币对应的人民币金额。': 'Enter the rate used for this transaction: CNY per unit of the original currency.', '本次有效期延至': 'Valid until',
 '按周期填入日期': 'Use cycle date {date}', '实付扣款账户（私有）': 'Actual payment accounts (private)', '消费记录标题（私有）': 'Expense title (private)', '根据号卡、消费类型和日期生成，可以修改。': 'Generated from the card, expense type, and date. You can edit it.', '旧版付款账户名称（私有）': 'Legacy payment account name (private)',
} as const

const zh = {
 ...Object.fromEntries(Object.keys(en).map(key => [key, key])),
 '删除号卡确认': '将删除「{name}」及其全部消费记录，是否继续？',
 '删除支付账户确认': '将删除「{name}」，是否继续？',
 '消费记录数量': '{count} 条消费记录',
 '编辑冲突提示': '资源已被修改。重新加载会放弃当前未保存的编辑。',
 '按周期填入日期': '按周期填入 {date}',
 '分页总数': '共 {count} 项数据',
 '页': '页', '条 / 页': '条 / 页',
}

export function useSimnookI18n() {
 const { t, locale } = useI18n({
  useScope: 'local', inheritLocale: true, fallbackLocale: 'en',
  messages: { en, zh, 'zh-CN': zh, 'zh-TW': zh },
 })
 const label = (value: string) => Object.hasOwn(en, value) ? t(value) : value
 const recordTypeLabel = (value: string | undefined) => {
  const type = value?.trim() ?? ''
  const labels: Record<string, string> = {
   '首次购买/开卡': '首次购买/开卡', '购卡': '首次购买/开卡', '首购': '首次购买/开卡', 'purchase': '首次购买/开卡', 'initial purchase / activation': '首次购买/开卡',
   '续费/保号': '续费/保号', '续费': '续费/保号', 'renewal': '续费/保号',
   '额外充值': '额外充值', '充值': '额外充值', 'top-up': '额外充值', 'topup': '额外充值',
   '其他费用': '其他费用', 'other fees': '其他费用',
  }
  return labels[type.toLowerCase()] ? t(labels[type.toLowerCase()]!) : type
 }
 return { t, locale, label, recordTypeLabel }
}
