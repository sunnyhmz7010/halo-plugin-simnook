<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { axiosInstance } from '@halo-dev/api-client'
import { Toast, VButton, VEmpty, VLoading, VModal } from '@halo-dev/components'
import { SimCardV1alpha1Api, SimnookManagementV1alpha1Api, type SimCard, type ExpenseRecord } from '../api/generated'
import { useSimnookI18n } from '../locales'
import { newExpense, suggestedExtension } from '../utils/cardEntry'
import ExpenseFields from './ExpenseFields.vue'

const props = defineProps<{ name: string; today: string }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { t } = useSimnookI18n()
const api = new SimCardV1alpha1Api(undefined, '', axiosInstance)
const managementApi = new SimnookManagementV1alpha1Api(undefined, '', axiosInstance)
const card = ref<SimCard>()
const draft = ref<ExpenseRecord>()
const nextStart = ref(''), nextEnd = ref('')
const loading = ref(false), saving = ref(false), conflict = ref(false), failed = ref(false)

async function load() {
 loading.value = true; failed.value = false
 try {
  const resource = await api.getSimCard({ name: props.name })
  card.value = resource.data
  if (!draft.value) {
   draft.value = { ...newExpense(card.value.spec, props.today),
    id: crypto.randomUUID(), type: '续费/保号', amount: card.value.spec.price, currency: card.value.spec.currency || 'CNY',
   }
   draft.value.title = [card.value.spec.name, t('续费/保号'), props.today].join(' · ')
   draft.value.extendsTo = suggestedExtension(card.value.spec, props.today, '续费/保号') || undefined
  }
  conflict.value = false
 } catch (error) { failed.value = true; Toast.error(t('加载失败，请重试')); console.error(error) }
 finally { loading.value = false }
}

async function save() {
 if (!card.value || !draft.value || saving.value || conflict.value) return
 const start = nextStart.value || draft.value.extendsTo
 if (nextEnd.value && (!start || nextEnd.value < start)) { Toast.warning(t('请填写续费起始日，且截止日不能早于起始日')); return }
 saving.value = true
 try {
  const record = { ...draft.value, amount: Number(draft.value.amount), rate: draft.value.currency === 'CNY' ? undefined : Number(draft.value.rate) }
  await managementApi.quickRenewCard({ name: props.name, quickRenewalRequest: { expectedVersion: String(card.value.metadata.version), record, nextRenewalAt: nextStart.value || undefined, nextRenewalEnd: nextEnd.value || undefined } })
  Toast.success(t('已保存')); emit('saved')
 } catch (error) {
  if ((error as { response?: { status: number } }).response?.status === 409) conflict.value = true
  else { Toast.error(t('保存失败，请检查表单或稍后重试')); console.error(error) }
 } finally { saving.value = false }
}

onMounted(load)
function submit() { (document.getElementById('simnook-quick-renewal') as HTMLFormElement | null)?.requestSubmit() }
</script>

<template>
 <VModal :title="t('快捷续费')" :width="640" mount-to-body :closable="!saving" @close="!saving && emit('close')">
  <VLoading v-if="loading" />
  <VEmpty v-else-if="failed" :title="t('加载失败')"><template #actions><VButton @click="load">{{ t('重试') }}</VButton></template></VEmpty>
  <template v-else-if="card && draft">
   <p class="mb-3 font-medium">{{ card.spec.name }}</p>
   <div v-if="conflict" class="mb-4 rounded border border-orange-200 bg-orange-50 p-3 text-sm">
    <p>{{ t('续费冲突提示') }}</p><VButton class="mt-2" @click="load">{{ t('重新加载并保留草稿') }}</VButton>
   </div>
   <FormKit id="simnook-quick-renewal" v-model="draft" type="form" :actions="false" @submit="save">
    <ExpenseFields :record="draft" :initialize="false" required :card="card.spec"
     :types="[{ value: '续费/保号', label: t('续费/保号') }]" :currencies="[...new Set(['CNY', 'USD', 'HKD', 'EUR', 'GBP', 'JPY', draft.currency || 'CNY'])].map(value => ({ value, label: value }))" />
    <FormKit v-model="nextStart" type="date" :ignore="true" :label="t('下次续费起始日')" :help="t('留空时跟随有效期。')" />
    <FormKit v-model="nextEnd" type="date" :ignore="true" :label="t('续费截止日')" />
    <div class="rounded bg-gray-50 p-3 text-sm text-gray-700">
     <p>{{ t('新有效期') }}：{{ draft.extendsTo || '—' }}</p>
     <p>{{ t('下次续费') }}：{{ nextStart || draft.extendsTo || '—' }} {{ nextEnd ? `~ ${nextEnd}` : '' }}</p>
     <p v-if="card.spec.expiresAt || card.spec.nextRenewalAt || card.spec.nextRenewalEnd" class="mt-2">{{ t('续费日期更新提示') }}</p>
    </div>
   </FormKit>
  </template>
  <template #footer><VButton type="secondary" :loading="saving" :disabled="conflict || loading || failed" @click="submit">{{ t('保存续费') }}</VButton></template>
 </VModal>
</template>
