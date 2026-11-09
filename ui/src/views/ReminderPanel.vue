<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { axiosInstance, consoleApiClient } from '@halo-dev/api-client'
import { Toast, VButton, VLoading } from '@halo-dev/components'
import { events } from '@halo-dev/ui-shared'
import { SimnookManagementV1alpha1Api, type ReminderSubscriptionView } from '../api/generated'
import { useSimnookI18n } from '../locales'

const { t } = useSimnookI18n()
const emit = defineEmits<{ saved: [] }>()
const api = new SimnookManagementV1alpha1Api(undefined, '', axiosInstance)
const state = ref<ReminderSubscriptionView>()
const loading = ref(false), saving = ref(false), failed = ref(false)
const savingSettings = ref(false)
const form = ref({ sendTime: '09:00', timezone: 'Asia/Shanghai', daysAhead: 7 })
async function load() {
 loading.value = true; failed.value = false
 try {
  state.value = (await api.getReminderSubscription()).data
  form.value = { sendTime: state.value.sendTime ?? '09:00', timezone: state.value.timezone ?? 'Asia/Shanghai', daysAhead: state.value.daysAhead ?? 7 }
 }
 catch (error) { failed.value = true; console.error(error) }
 finally { loading.value = false }
}
async function toggle() {
 if (!state.value || saving.value) return
 saving.value = true
 try { state.value = (await api.updateReminderSubscription({ reminderSubscriptionUpdate: { subscribed: !state.value.subscribed } })).data; Toast.success(t('已保存')) }
 catch (error) { Toast.error(t('提醒设置保存失败')); console.error(error) }
 finally { saving.value = false }
}
async function saveSettings() {
 if (savingSettings.value) return
 savingSettings.value = true
 try {
  // 保存前读取最新配置，保留其他设置分组及提醒组中的已有字段。
  const { data } = await consoleApiClient.plugin.plugin.fetchPluginJsonConfig({ name: 'simnook' })
  const config = data as { reminders?: object }
  await consoleApiClient.plugin.plugin.updatePluginJsonConfig({ name: 'simnook', body: {
   ...data, reminders: { ...config.reminders, ...form.value, daysAhead: Number(form.value.daysAhead) },
  } })
  events.emit('core:plugin:configMap:updated', { pluginName: 'simnook', group: 'reminders' })
  Toast.success(t('已保存')); emit('saved'); await load()
 }
 catch (error) { Toast.error(t('提醒设置保存失败')); console.error(error) }
 finally { savingSettings.value = false }
}
function submitSettings() { (document.getElementById('simnook-reminder-settings') as HTMLFormElement | null)?.requestSubmit() }
onMounted(load)
</script>

<template>
 <div class="mb-4 rounded border border-gray-200 bg-white p-4 text-sm">
  <VLoading v-if="loading" />
  <div v-else-if="failed" class="flex items-center gap-3"><span>{{ t('提醒状态加载失败') }}</span><VButton size="sm" @click="load">{{ t('重试') }}</VButton></div>
  <template v-else-if="state">
   <div class="flex flex-wrap items-center justify-between gap-3">
    <div><strong>{{ t('自动续费提醒') }}</strong><p class="mt-1 text-gray-600">{{ state.sendTime }} · {{ state.timezone }} · {{ t('提醒范围', { days: state.daysAhead }) }}</p></div>
    <VButton :loading="saving" @click="toggle">{{ t(state.subscribed ? '取消我的订阅' : '订阅我的续费提醒') }}</VButton>
   </div>
   <p class="mt-2 text-gray-500">{{ t('提醒配置说明') }}</p>
   <p v-if="state.lastSubmittedAt" class="mt-2">{{ t('上次提交给 Halo') }}：{{ new Date(state.lastSubmittedAt).toLocaleString() }}</p>
   <p v-if="state.subscribed && state.nextRunAt" class="mt-1">{{ t('下次检查时间') }}：{{ new Date(state.nextRunAt).toLocaleString() }}</p>
   <p v-if="state.lastError" class="mt-2 text-red-600">{{ state.lastError }}</p>
   <div v-permission="['system:plugins:manage']" class="mt-4 border-t border-gray-100 pt-4">
    <FormKit id="simnook-reminder-settings" v-model="form" type="form" :actions="false" :disabled="savingSettings" @submit="saveSettings">
     <div class="grid gap-4 md:grid-cols-3">
      <FormKit name="sendTime" type="text" :label="t('每日汇总时间')" validation="required|matches:/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/" :help="t('每日汇总时间说明')" />
      <FormKit name="timezone" type="text" :label="t('业务时区')" validation="required" :help="t('业务时区说明')" />
      <FormKit name="daysAhead" type="number" :label="t('提前提醒天数')" validation="required|number|min:1|max:60" min="1" max="60" step="1" :help="t('提前提醒天数说明')" />
     </div>
     <VButton type="secondary" :loading="savingSettings" @click="submitSettings">{{ t('保存设置') }}</VButton>
    </FormKit>
   </div>
  </template>
 </div>
</template>
