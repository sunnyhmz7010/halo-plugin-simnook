<script setup lang="ts">
import { useSimnookI18n } from '../locales'
import { ref, onMounted, watch } from 'vue'
import { axiosInstance } from '@halo-dev/api-client'
import { Dialog, Toast, VCard, VButton, VEntityContainer, VEntity, VEntityField, VPagination, VLoading, VEmpty, VModal } from '@halo-dev/components'
import { PaymentAccountV1alpha1Api, SimCardV1alpha1Api, type PaymentAccount, type PaymentAccountSpec, type SimCard } from '../api/generated'

const { t, recordTypeLabel } = useSimnookI18n()

const emit = defineEmits<{ 'edit-card': [card: SimCard] }>()
const api = new PaymentAccountV1alpha1Api(undefined, '', axiosInstance)
const cardApi = new SimCardV1alpha1Api(undefined, '', axiosInstance)
const types = { bank: '银行卡', virtual: '虚拟卡', alipay: '支付宝', wechat: '微信支付', paypal: 'PayPal', balance: '平台余额', other: '其他' }
const statuses = { ok: '正常', low: '余额不足', frozen: '已冻结', closed: '已注销' }
const options = (values: Record<string, string>) => Object.entries(values).map(([value, label]) => ({ value, label: t(label) }))

const accounts = ref<PaymentAccount[]>([])
const page = ref(1), size = ref(10), total = ref(0)
const loading = ref(false), loadError = ref(false), saving = ref(false), visible = ref(false), conflict = ref(false)
const deleting = ref('')
const editing = ref<PaymentAccount>()
const form = ref<PaymentAccountSpec>({ name: '' })
const related = ref<{ account: PaymentAccount; cards: SimCard[]; records: { card: SimCard; title: string; date?: string }[] }>()
const loadingLinks = ref('')
let sequence = 0

async function load() {
 const current = ++sequence
 loading.value = true; loadError.value = false
 try {
  const { data } = await api.listPaymentAccount({ page: page.value, size: size.value, fieldSelector: ['deleting=false'] })
  if (current !== sequence) return
  accounts.value = data.items; total.value = data.total
  if (page.value > Math.max(1, data.totalPages)) page.value = Math.max(1, data.totalPages)
 } catch (error) {
  if (current === sequence) { loadError.value = true; Toast.error(t('支付账户加载失败')); console.error(error) }
 } finally {
  if (current === sequence) loading.value = false
 }
}

function edit(account?: PaymentAccount) {
 editing.value = account
 form.value = account ? { ...account.spec } : { name: '' }
 conflict.value = false
 visible.value = true
}

async function reloadEditing() {
 if (!editing.value) return
 try {
  const { data } = await api.getPaymentAccount({ name: editing.value.metadata.name })
  edit(data)
  Toast.info(t('已加载最新版本，请重新编辑'))
 } catch (error) { Toast.error(t('重新加载支付账户失败')); console.error(error) }
}

async function save() {
 if (saving.value || conflict.value) return
 saving.value = true
 try {
  const spec = { name: form.value.name.trim(), type: form.value.type || undefined, status: form.value.status || undefined }
  const resource: PaymentAccount = editing.value ? { ...editing.value, spec } : {
   apiVersion: 'simnook.halo.run/v1alpha1', kind: 'PaymentAccount', metadata: { name: crypto.randomUUID() }, spec,
  }
  if (editing.value) await api.updatePaymentAccount({ name: resource.metadata.name, paymentAccount: resource })
  else await api.createPaymentAccount({ paymentAccount: resource })
  visible.value = false
  Toast.success(t('支付账户已保存'))
  await load()
 } catch (error) {
  if ((error as { response?: { status: number } }).response?.status === 409) {
   conflict.value = true
   Toast.warning(t('其他人已修改此账户，请重新加载后编辑'))
  } else { Toast.error(t('支付账户保存失败')); console.error(error) }
 } finally { saving.value = false }
}

async function getLinks(account: PaymentAccount) {
 const id = account.metadata.name
 const { data } = await cardApi.listSimCard({ size: 0, fieldSelector: ['deleting=false'] })
 return {
  account,
  cards: data.items.filter(card => card.spec.paymentAccountIds?.includes(id)),
  records: data.items.flatMap(card => (card.spec.records ?? [])
   .filter(record => record.paymentAccountIds?.includes(id))
   .map(record => ({ card, title: record.title?.trim() || recordTypeLabel(record.type) || t('未命名消费记录'), date: record.date }))),
 }
}

async function showLinks(account: PaymentAccount) {
 if (loadingLinks.value) return
 loadingLinks.value = account.metadata.name
 try { related.value = await getLinks(account) }
 catch (error) { Toast.error(t('加载关联失败')); console.error(error) }
 finally { loadingLinks.value = '' }
}

function openCard(card: SimCard) {
 related.value = undefined
 emit('edit-card', card)
}

function remove(account: PaymentAccount) {
 Dialog.warning({
  title: t('删除支付账户'), description: t('删除支付账户确认', { name: account.spec.name }), confirmText: t('删除'), showCancel: true,
  onConfirm: async () => {
   deleting.value = account.metadata.name
   try {
    const links = await getLinks(account)
    if (links.cards.length || links.records.length) {
     related.value = links
     Toast.warning(t('此账户仍有关联，请先解除关联或将账户状态设为已注销'))
     return
    }
    await api.deletePaymentAccount({ name: account.metadata.name })
    Toast.success(t('支付账户已删除'))
    await load()
   } catch (error) { Toast.error(t('支付账户删除失败')); console.error(error) }
   finally { deleting.value = '' }
  },
 })
}

watch([page, size], load)
onMounted(load)
function submit() { (document.getElementById('simnook-account-form') as HTMLFormElement | null)?.requestSubmit() }
defineExpose({ edit })
</script>

<template>
 <VCard :body-class="['!p-0']">
  <template v-if="$slots.header" #header><slot name="header" /></template>
  <VLoading v-if="loading" />
  <VEmpty v-else-if="loadError" :title="t('支付账户加载失败')"><template #actions><VButton @click="load">{{ t('重试') }}</VButton></template></VEmpty>
  <VEmpty v-else-if="!accounts.length" :title="t('还没有支付账户')" :message="t('可录入用于续费的银行卡或钱包。')" />
  <VEntityContainer v-else>
   <VEntity v-for="account in accounts" :key="account.metadata.name">
    <template #start><VEntityField :title="account.spec.name" :description="account.spec.type ? t(types[account.spec.type]) : t('未填写类型')" @click="edit(account)" /></template>
    <template #end>
     <VEntityField :title="account.spec.status ? t(statuses[account.spec.status]) : t('未填写状态')" />
     <VButton size="sm" :loading="loadingLinks === account.metadata.name" @click="showLinks(account)">{{ t('关联') }}</VButton>
     <VButton size="sm" @click="edit(account)">{{ t('编辑') }}</VButton>
     <VButton size="sm" type="danger" :loading="deleting === account.metadata.name" @click="remove(account)">{{ t('删除') }}</VButton>
    </template>
   </VEntity>
  </VEntityContainer>
  <template #footer><VPagination v-model:page="page" v-model:size="size" :total="total" :size-options="[10, 20, 50]" :page-label="t('页')" :size-label="t('条 / 页')" :total-label="t('分页总数', { count: total })" /></template>
 </VCard>

 <VModal v-if="visible" :title="editing ? t('编辑支付账户') : t('新增支付账户')" mount-to-body @close="visible = false">
  <div v-if="conflict" class="mb-4"><p>{{ t('编辑冲突提示') }}</p><VButton @click="reloadEditing">{{ t('重新加载') }}</VButton></div>
  <FormKit id="simnook-account-form" v-model="form" type="form" :actions="false" :config="{ validationVisibility: 'submit' }" @submit="save">
   <FormKit name="name" type="text" :label="t('账户名称')" validation="required|length:1,100" :help="t('例如 示例银行 · 4242。仅记录账户名称。')" />
   <FormKit name="type" type="select" :label="t('账户类型')" :placeholder="t('选择账户类型')" :options="options(types)" clearable />
   <FormKit name="status" type="select" :label="t('账户状态')" :placeholder="t('选择账户状态')" :options="options(statuses)" clearable />
  </FormKit>
  <template #footer><VButton type="secondary" :loading="saving" :disabled="conflict" @click="submit">{{ t('保存') }}</VButton></template>
 </VModal>

 <VModal v-if="related" :title="`${related.account.spec.name} · ${t('关联')}`" mount-to-body @close="related = undefined">
  <VEmpty v-if="!related.cards.length && !related.records.length" :title="t('暂无关联')" />
  <VEntityContainer v-else>
   <VEntity v-for="card in related.cards" :key="card.metadata.name">
    <template #start><VEntityField :title="card.spec.name" :description="t('默认扣款账户')" @click="openCard(card)" /></template>
   </VEntity>
   <VEntity v-for="(record, index) in related.records" :key="index">
    <template #start><VEntityField :title="record.title" :description="[record.card.spec.name, record.date].map(value => value?.trim()).filter(Boolean).join(' · ')" @click="openCard(record.card)" /></template>
   </VEntity>
  </VEntityContainer>
 </VModal>
</template>
