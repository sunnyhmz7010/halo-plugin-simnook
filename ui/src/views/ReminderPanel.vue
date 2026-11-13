<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { axiosInstance, consoleApiClient } from '@halo-dev/api-client'
import { Dialog, Toast, VAlert, VButton, VDescription, VDescriptionItem, VDropdownItem, VEmpty, VEntity, VEntityContainer, VEntityField, VLoading, VModal, VSpace, VSwitch } from '@halo-dev/components'
import { events } from '@halo-dev/ui-shared'
import { ReminderChannelsApi, type ReminderChannelInput, type ReminderChannelView, type ReminderChannelsView } from '../api/reminders'
import { useSimnookI18n } from '../locales'

const { t } = useSimnookI18n()
const emit = defineEmits<{ saved: [] }>()
const api = new ReminderChannelsApi(undefined, '', axiosInstance)
const state = ref<ReminderChannelsView>()
const loading = ref(false), failed = ref(false), busy = ref(''), saving = ref(false)
const savingSettings = ref(false), visible = ref(false), editorError = ref('')
const draft = ref<ReminderChannelInput>({})
const identity = ref<Pick<ReminderChannelInput, 'name' | 'version'>>({})
const schedule = ref({ sendTime: '09:00', timezone: 'Asia/Shanghai', daysAhead: 7 })
const eventOptions = computed(() => [
 { value: 'upcoming', label: t('即将到期') }, { value: 'window', label: t('续费窗口开放') }, { value: 'overdue', label: t('已逾期') },
])
const recipients = computed(() => {
 const options = (state.value?.recipients ?? []).filter(user => user.name).map(user => ({ value: user.name!, label: user.displayName ? `${user.displayName} (${user.name})` : user.name! }))
 for (const name of draft.value.recipients ?? []) if (!options.some(option => option.value === name)) options.push({ value: name, label: `${name} · ${t('用户不可用')}` })
 return options
})
const recipientLabel = (name: string) => state.value?.recipients?.find(user => user.name === name)?.displayName || name
const eventLabel = (values: string[] = []) => values.map(value => eventOptions.value.find(option => option.value === value)?.label || value).join('、')
const dateLabel = (value?: string) => value ? new Date(value).toLocaleString() : t('尚未发送')
function errorMessage(error: unknown) {
 const data = (error as { response?: { data?: { detail?: string; message?: string } } }).response?.data
 return data?.detail || data?.message || t('提醒设置保存失败')
}
async function load(resetSchedule = true) {
 loading.value = true; failed.value = false
 try {
  state.value = (await api.listReminderChannels()).data
  if (resetSchedule) schedule.value = { sendTime: state.value.sendTime ?? '09:00', timezone: state.value.timezone ?? 'Asia/Shanghai', daysAhead: state.value.daysAhead ?? 7 }
 } catch (error) { failed.value = true; console.error(error) }
 finally { loading.value = false }
}
function edit(channel?: ReminderChannelView) {
 identity.value = channel ? { name: channel.name, version: channel.version } : {}
 draft.value = channel ? { name: channel.name, version: channel.version, displayName: channel.displayName, enabled: channel.enabled, recipients: [...channel.recipients ?? []], events: [...channel.events ?? []] }
  : { displayName: '', enabled: true, recipients: [], events: ['upcoming', 'window', 'overdue'] }
 editorError.value = ''; visible.value = true
}
async function save() {
 if (saving.value) return
 saving.value = true; editorError.value = ''
 try {
  await api.saveReminderChannel({ reminderChannelInput: { ...draft.value, ...identity.value } })
  visible.value = false; Toast.success(t('已保存')); await load(false)
 } catch (error) { editorError.value = errorMessage(error); console.error(error) }
 finally { saving.value = false }
}
async function toggle(channel: ReminderChannelView, enabled: boolean) {
 if (busy.value || !channel.name) return
 busy.value = channel.name
 try {
  await api.saveReminderChannel({ reminderChannelInput: { name: channel.name, version: channel.version, displayName: channel.displayName, recipients: channel.recipients, events: channel.events, enabled } })
  Toast.success(t('已保存')); await load(false)
 } catch (error) { Toast.error(errorMessage(error)); console.error(error) }
 finally { busy.value = '' }
}
function remove(channel: ReminderChannelView) {
 Dialog.warning({ title: t('删除通知渠道'), description: t('删除渠道说明', { name: channel.displayName }), confirmText: t('删除'), showCancel: true,
  onConfirm: async () => {
   if (busy.value || !channel.name || channel.version === undefined) return
   busy.value = channel.name
   try { await api.deleteReminderChannel({ name: channel.name, version: channel.version }); Toast.success(t('已删除')); await load(false) }
   catch (error) { Toast.error(errorMessage(error)); console.error(error) }
   finally { busy.value = '' }
  },
 })
}
async function test(channel: ReminderChannelView) {
 if (busy.value || !channel.name) return
 busy.value = channel.name
 try {
  const { data } = await api.testReminderChannel({ name: channel.name })
  if (data.lastTestError) Toast.error(data.lastTestError)
  else Toast.success(t('测试已提交给 Halo'))
  await load(false)
 } catch (error) { Toast.error(errorMessage(error)); console.error(error) }
 finally { busy.value = '' }
}
async function saveSettings() {
 if (savingSettings.value) return
 savingSettings.value = true
 try {
  new Intl.DateTimeFormat('en', { timeZone: schedule.value.timezone }).format()
  // 保留其他设置分组，渠道配置由独立 API 保存。
  const { data } = await consoleApiClient.plugin.plugin.fetchPluginJsonConfig({ name: 'simnook' })
  const config = data as { reminders?: object }
  await consoleApiClient.plugin.plugin.updatePluginJsonConfig({ name: 'simnook', body: { ...data, reminders: { ...config.reminders, ...schedule.value, daysAhead: Number(schedule.value.daysAhead) } } })
  events.emit('core:plugin:configMap:updated', { pluginName: 'simnook', group: 'reminders' })
  Toast.success(t('已保存')); emit('saved'); await load()
 } catch (error) { Toast.error(errorMessage(error)); console.error(error) }
 finally { savingSettings.value = false }
}
function submit(id: string) { (document.getElementById(id) as HTMLFormElement | null)?.requestSubmit() }
onMounted(() => load())
</script>

<template>
 <VSpace direction="column" align="stretch" spacing="md" class="w-full p-4">
  <VSpace spacing="md" class="flex-wrap justify-between">
   <h2 class="font-semibold">{{ t('通知渠道') }}</h2>
   <VButton type="secondary" :disabled="loading || failed || Boolean(busy)" @click="edit()">{{ t('添加渠道') }}</VButton>
  </VSpace>
  <VAlert type="info" :description="t('Halo渠道说明')" />
  <VLoading v-if="loading" />
  <VEmpty v-else-if="failed" :title="t('提醒状态加载失败')"><template #actions><VButton @click="load(false)">{{ t('重试') }}</VButton></template></VEmpty>
  <template v-else-if="state">
   <VEmpty v-if="!state.channels?.length" :title="t('暂无通知渠道')" :message="t('添加渠道说明')" />
   <VEntityContainer v-else>
    <VEntity v-for="channel in state.channels" :key="channel.name">
     <template #start>
      <VEntityField :title="channel.displayName" :description="`Halo · ${(channel.recipients ?? []).map(recipientLabel).join('、')}`" />
     </template>
     <template #end>
      <VButton size="sm" :loading="busy === channel.name" :disabled="Boolean(busy)" @click="test(channel)">{{ t('发送测试') }}</VButton>
      <VSwitch :model-value="channel.enabled" :disabled="Boolean(busy)" :aria-label="`${t('启用')} ${channel.displayName}`" @update:model-value="toggle(channel, $event)" />
     </template>
     <template #dropdownItems>
      <VDropdownItem :disabled="Boolean(busy)" @click="edit(channel)">{{ t('编辑') }}</VDropdownItem>
      <VDropdownItem :disabled="Boolean(busy)" type="danger" @click="remove(channel)">{{ t('删除') }}</VDropdownItem>
     </template>
     <template #footer>
      <VSpace direction="column" align="stretch" spacing="sm" class="w-full p-4">
       <VDescription>
        <VDescriptionItem :label="t('接收提醒')" :content="eventLabel(channel.events)" />
        <VDescriptionItem :label="t('上次提交给 Halo')" :content="dateLabel(channel.lastSubmittedAt)" />
        <VDescriptionItem v-if="channel.lastTestAt" :label="t('上次测试')" :content="dateLabel(channel.lastTestAt)" />
       </VDescription>
       <VAlert v-if="channel.lastError" type="error" :description="channel.lastError" />
       <VAlert v-if="channel.lastTestError" type="error" :description="channel.lastTestError" />
      </VSpace>
     </template>
    </VEntity>
   </VEntityContainer>
   <h2 class="font-semibold">{{ t('发送时间') }}</h2>
   <VDescription>
    <VDescriptionItem :label="t('每日汇总时间')" :content="`${state.sendTime} · ${state.timezone}`" />
    <VDescriptionItem :label="t('汇总范围')" :content="t('提醒范围', { days: state.daysAhead })" />
    <VDescriptionItem v-if="state.channels?.some(channel => channel.enabled)" :label="t('下次汇总时间')" :content="dateLabel(state.nextRunAt)" />
   </VDescription>
   <div v-permission="['system:plugins:manage']">
    <FormKit id="simnook-reminder-settings" v-model="schedule" type="form" :actions="false" :disabled="savingSettings" @submit="saveSettings">
     <FormKit name="sendTime" type="text" :label="t('每日汇总时间')" validation="required|matches:/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/" :help="t('渠道时间说明')" />
     <FormKit name="timezone" type="text" :label="t('业务时区')" validation="required" :help="t('业务时区说明')" />
     <FormKit name="daysAhead" type="number" :label="t('提前提醒天数')" validation="required|number|min:1|max:60" min="1" max="60" step="1" :help="t('提前提醒天数说明')" />
     <VButton type="secondary" :loading="savingSettings" @click="submit('simnook-reminder-settings')">{{ t('保存设置') }}</VButton>
    </FormKit>
   </div>
  </template>
 </VSpace>
 <VModal v-if="visible" :title="t(draft.name ? '编辑通知渠道' : '添加通知渠道')" :width="560" mount-to-body :layer-closable="false" @close="!saving && (visible = false)">
  <VAlert type="info" class="mb-4" :description="t('Halo接收说明')" />
  <VAlert v-if="editorError" type="error" class="mb-4" :description="editorError" />
  <FormKit id="simnook-channel" v-model="draft" type="form" :actions="false" :disabled="saving" @submit="save">
   <FormKit name="displayName" type="text" :label="t('渠道名称')" validation="required|length:1,100" />
   <FormKit name="recipients" type="select" :label="t('接收用户')" :options="recipients" multiple searchable validation="required" :help="t('接收用户说明')" />
   <FormKit name="events" type="checkbox" :label="t('接收提醒')" :options="eventOptions" validation="required" />
   <FormKit name="enabled" type="checkbox" :label="t('启用渠道')" />
  </FormKit>
  <template #footer>
   <VSpace><VButton type="secondary" :loading="saving" @click="submit('simnook-channel')">{{ t('保存渠道') }}</VButton><VButton :disabled="saving" @click="visible = false">{{ t('取消') }}</VButton></VSpace>
  </template>
 </VModal>
</template>
