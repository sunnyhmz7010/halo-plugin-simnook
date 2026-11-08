<script setup lang="ts">
import { useSimnookI18n } from '../locales'
import { computed, ref, watch } from 'vue'
import { VButton } from '@halo-dev/components'
import type { CardSpec, ExpenseRecord } from '../api/generated'
import { cardName, newExpense, suggestedExtension } from '../utils/cardEntry'

const { t, recordTypeLabel } = useSimnookI18n()

const props = defineProps<{
 record: ExpenseRecord
 initialize?: boolean
 required?: boolean
 card: CardSpec
 accountOptions: { value: string; label: string }[]
 accountIds: string[]
 types: { value: string; label: string }[]
 currencies: { value: string; label: string }[]
}>()
const defaults: ExpenseRecord = props.initialize !== false && Object.keys(props.record).length === 0 ? newExpense(props.card, props.accountIds) : {}
if (defaults.type) defaults.title = [cardName(props.card.carrier, props.card.number) || props.card.name, recordTypeLabel(defaults.type), defaults.date].filter(Boolean).join(' · ')
const type = ref(props.record.type ?? defaults.type)
const date = ref(props.record.date ?? defaults.date)
const currency = ref(props.record.currency ?? defaults.currency)
const title = ref(props.record.title ?? defaults.title)
const extendsTo = ref(props.record.extendsTo)
const suggestion = computed(() => suggestedExtension(props.card, date.value, type.value))
const recordTitle = (kind: string | undefined, day: string | undefined) =>
 [cardName(props.card.carrier, props.card.number) || props.card.name, recordTypeLabel(kind), day].filter(Boolean).join(' · ')

watch([type, date], ([kind, day], [previousKind, previousDay]) => {
 if (!title.value || title.value === recordTitle(previousKind, previousDay)) title.value = recordTitle(kind, day)
})
</script>

<template>
 <FormKit name="id" type="hidden" :value="record.id" />
 <FormKit v-model="type" name="type" type="select" :label="t('消费类型')" :placeholder="t('选择消费类型')" :options="types" clearable :validation="required ? 'required' : ''" />
 <FormKit v-model="date" name="date" type="date" :label="t('交易日期')" :validation="required ? 'required' : ''" />
 <FormKit name="amount" type="number" :label="t('原币金额')" :value="record.amount" :validation="required ? 'required|min:0' : 'min:0'" min="0" step="any" />
 <FormKit v-model="currency" name="currency" type="select" :label="t('币种')" :placeholder="t('选择币种')" :options="currencies" searchable allow-create clearable :validation="required ? 'required|matches:/^[A-Z]{3}$/' : 'matches:/^[A-Z]{3}$/'" />
 <FormKit v-show="currency !== 'CNY'" name="rate" type="number" :label="t('录入时汇率')" :value="record.rate" :validation="required && currency !== 'CNY' ? 'required|min:0.00000001' : 'min:0.00000001'" min="0.00000001" step="any" :help="t('填写这笔交易实际使用的汇率，1 单位原币对应的人民币金额。')" />
 <FormKit v-model="extendsTo" name="extendsTo" type="date" :label="t('本次有效期延至')" :validation="required ? 'required' : ''" />
 <VButton v-if="suggestion" size="sm" class="mb-4" :disabled="extendsTo === suggestion" @click="extendsTo = suggestion">{{ t('按周期填入日期', { date: suggestion }) }}</VButton>
 <FormKit name="paymentAccountIds" type="select" :label="t('实付扣款账户（私有）')" :placeholder="t('选择支付账户')" :value="record.paymentAccountIds ?? defaults.paymentAccountIds" :options="accountOptions" multiple searchable />
 <FormKit v-model="title" name="title" type="text" :label="t('消费记录标题（私有）')" :help="t('根据号卡、消费类型和日期生成，可以修改。')" />
 <FormKit v-if="record.account" name="account" type="text" :label="t('旧版付款账户名称（私有）')" :value="record.account" />
</template>
