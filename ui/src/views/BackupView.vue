<script setup lang="ts">
import { computed, ref } from 'vue'
import { axiosInstance } from '@halo-dev/api-client'
import { Dialog, Toast, VButton, VCard, VLoading } from '@halo-dev/components'
import { SimnookManagementV1alpha1Api, type BackupPreview, type BackupPreviewItem, type BackupRestoreDecision, type BackupRestoreResult } from '../api/generated'
import { useSimnookI18n } from '../locales'

const emit = defineEmits<{ restored: [] }>()
const { t } = useSimnookI18n()
const api = new SimnookManagementV1alpha1Api(undefined, '', axiosInstance)
const content = ref(''), filename = ref('')
const preview = ref<BackupPreview>(), result = ref<BackupRestoreResult>()
const choices = ref<Record<string, string>>({})
const busy = ref(false), exporting = ref(''), error = ref('')
let generation = 0
const key = (item: BackupPreviewItem) => `${item.kind}/${item.name}`
const actionLabel = (action: string) => t(({ create: '新增', skip: '跳过', overwrite: '覆盖', append: '仅追加消费记录' } as Record<string, string>)[action] || action)
const statusLabel = (status: string) => t(({ success: '成功', skipped: '已跳过', failed: '失败', pending: '未处理' } as Record<string, string>)[status] || status)
const selectedCount = computed(() => Object.values(choices.value).filter(value => value !== 'skip').length)
function message(cause: unknown): string {
 const response = (cause as { response?: { data?: { detail?: string; message?: string } } }).response?.data
 return response?.detail || response?.message || t('备份操作失败')
}

async function download(format: string) {
 if (exporting.value) return
 exporting.value = format
 try {
  const { data } = await api.exportBackup({ format }, { responseType: 'blob' })
  const url = URL.createObjectURL(data as unknown as Blob)
  const anchor = document.createElement('a'); anchor.href = url
  anchor.download = format === 'json' ? 'simnook-backup.json' : `simnook-${format}.csv`
  anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
 } catch (cause) { Toast.error(t('备份操作失败')); console.error(cause) }
 finally { exporting.value = '' }
}

async function selectFile(event: Event) {
 const current = ++generation
 const file = (event.target as HTMLInputElement).files?.[0]
 preview.value = undefined; result.value = undefined; error.value = ''; content.value = ''; filename.value = file?.name || ''
 if (!file) return
 if (file.size > 32 * 1024 * 1024) { error.value = t('备份文件不能超过 32 MiB'); return }
 try { const text = await file.text(); if (current === generation) { content.value = text; await inspect() } }
 catch (cause) { if (current === generation) error.value = message(cause) }
}

async function inspect() {
 if (!content.value || busy.value) return
 const current = generation
 busy.value = true; error.value = ''; result.value = undefined; preview.value = undefined
 try {
  const { data } = await api.previewBackup({ backupPreviewRequest: { content: content.value } })
  if (current !== generation) return
  preview.value = data
  choices.value = Object.fromEntries((data.items ?? []).map(item => [key(item), item.actions?.[0] || 'skip']))
 } catch (cause) { if (current === generation) error.value = message(cause) }
 finally { busy.value = false }
}

function restore() {
 if (!preview.value || busy.value || result.value || !selectedCount.value) return
 Dialog.warning({ title: t('确认恢复数据'), description: t('恢复确认说明', { count: selectedCount.value }), confirmText: t('按预览恢复'), showCancel: true,
  onConfirm: async () => {
   if (!preview.value || busy.value) return
   busy.value = true; error.value = ''
   try {
    const decisions: BackupRestoreDecision[] = (preview.value.items ?? []).map(item => ({ kind: item.kind, name: item.name, action: choices.value[key(item)], expectedVersion: item.expectedVersion }))
    result.value = (await api.restoreBackup({ backupRestoreRequest: { content: content.value, fileHash: preview.value.fileHash, decisions } })).data
    emit('restored')
   } catch (cause) { error.value = message(cause) }
   finally { busy.value = false }
  },
 })
}
</script>

<template>
 <VCard>
  <h2 class="mb-2 font-semibold">{{ t('导出备份') }}</h2>
  <p class="mb-3 text-sm text-gray-600">{{ t('备份范围说明') }}</p>
  <div class="flex flex-wrap gap-2">
   <VButton :loading="exporting === 'json'" :disabled="Boolean(exporting)" @click="download('json')">{{ t('完整 JSON 备份') }}</VButton>
   <VButton :loading="exporting === 'cards'" :disabled="Boolean(exporting)" @click="download('cards')">{{ t('号卡清单 CSV') }}</VButton>
   <VButton :loading="exporting === 'records'" :disabled="Boolean(exporting)" @click="download('records')">{{ t('消费记录 CSV') }}</VButton>
  </div>
  <hr class="my-5 border-gray-100" />
  <h2 class="mb-2 font-semibold">{{ t('恢复备份') }}</h2>
  <p class="mb-3 text-sm text-gray-600">{{ t('恢复范围说明') }}</p>
  <label class="block text-sm"><span class="mr-3">{{ t('选择 JSON 备份') }}</span><input type="file" accept=".json,application/json" :disabled="busy" @change="selectFile" /></label>
  <p v-if="filename" class="mt-2 text-sm text-gray-500">{{ filename }}</p>
  <VLoading v-if="busy" class="my-4" />
  <p v-if="error" class="my-3 text-sm text-red-600" role="alert">{{ error }}</p>
  <div v-if="preview && !result" class="mt-4">
   <div v-for="item in preview.items" :key="key(item)" class="mb-3 rounded border border-gray-200 p-3">
    <div class="flex flex-wrap items-center justify-between gap-3">
     <div><strong class="text-sm">{{ item.title }}</strong><p class="text-xs text-gray-500">{{ t('号卡') }} · {{ item.name }}</p></div>
     <FormKit v-model="choices[key(item)]" type="select" :label="t('恢复方式')" :options="(item.actions ?? []).map(value => ({ value, label: actionLabel(value) }))" :disabled="busy" />
    </div>
    <p v-if="choices[key(item)] === 'append'" class="mt-2 text-sm">{{ t('将追加记录', { count: item.appendCount }) }}</p>
    <p v-for="warning in item.warnings" :key="warning" class="mt-1 text-sm text-orange-700">{{ warning }}</p>
    <details v-if="item.differences?.length" class="mt-2 text-sm">
     <summary class="cursor-pointer">{{ t('查看字段差异') }}</summary>
     <div v-for="difference in item.differences" :key="difference.field" class="mt-2 rounded bg-gray-50 p-2">
      <p class="font-medium">{{ difference.field }}</p>
      <div class="grid gap-2 md:grid-cols-2"><div><span>{{ t('当前数据') }}</span><pre class="max-h-48 overflow-auto whitespace-pre-wrap break-all text-xs">{{ difference.before }}</pre></div><div><span>{{ t('备份数据') }}</span><pre class="max-h-48 overflow-auto whitespace-pre-wrap break-all text-xs">{{ difference.after }}</pre></div></div>
     </div>
    </details>
    <p v-else class="mt-2 text-sm text-gray-500">{{ t('业务字段一致') }}</p>
   </div>
   <VButton type="secondary" :disabled="busy || !selectedCount" @click="restore">{{ t('按预览恢复') }} ({{ selectedCount }})</VButton>
  </div>
  <div v-if="result" class="mt-4 text-sm">
   <p class="mb-2 font-medium">{{ t('恢复结果') }}</p>
   <p v-for="item in result.items" :key="`${item.kind}/${item.name}`" class="mb-1" :class="item.status === 'failed' ? 'text-red-600' : ''">{{ item.name }} · {{ statusLabel(item.status || '') }} {{ item.message }}</p>
  </div>
  <VButton v-if="content && !busy" class="mt-4" @click="inspect">{{ t('重新预览') }}</VButton>
 </VCard>
</template>
