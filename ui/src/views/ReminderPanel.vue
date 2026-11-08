<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { axiosInstance } from '@halo-dev/api-client'
import { Toast, VButton, VLoading } from '@halo-dev/components'
import { SimnookManagementV1alpha1Api, type ReminderSubscriptionView } from '../api/generated'
import { useSimnookI18n } from '../locales'

const { t } = useSimnookI18n()
const api = new SimnookManagementV1alpha1Api(undefined, '', axiosInstance)
const state = ref<ReminderSubscriptionView>()
const loading = ref(false), saving = ref(false), failed = ref(false)
async function load() {
 loading.value = true; failed.value = false
 try { state.value = (await api.getReminderSubscription()).data }
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
  </template>
 </div>
</template>
