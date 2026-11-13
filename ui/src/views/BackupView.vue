<script setup lang="ts">
import { computed, ref } from 'vue'
import { axiosInstance } from '@halo-dev/api-client'
import { Dialog, Toast, VAlert, VButton, VDescription, VDescriptionItem, VEntity, VEntityContainer, VEntityField, VLoading, VModal, VSpace } from '@halo-dev/components'
import { SimnookManagementV1alpha1Api, type BackupPreview, type BackupPreviewItem, type BackupRestoreDecision, type BackupRestoreResult } from '../api/generated'
import { useSimnookI18n } from '../locales'

const emit = defineEmits<{ restored: [] }>()
const { t } = useSimnookI18n()
const api = new SimnookManagementV1alpha1Api(undefined, '', axiosInstance)
const content = ref('')
const preview = ref<BackupPreview>(), result = ref<BackupRestoreResult>()
const differences = ref<BackupPreviewItem>()
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

async function selectFile(files: { file?: File }[]) {
 const current = ++generation
 const file = files?.[0]?.file
 preview.value = undefined; result.value = undefined; differences.value = undefined; error.value = ''; content.value = ''
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
 <VSpace direction="column" align="stretch" spacing="lg" class="w-full p-4">
  <h2 class="font-semibold">{{ t('导出备份') }}</h2>
  <VAlert type="info" :description="t('备份范围说明')" />
  <VSpace spacing="sm" class="flex-wrap">
   <VButton :loading="exporting === 'json'" :disabled="Boolean(exporting)" @click="download('json')">{{ t('完整 JSON 备份') }}</VButton>
   <VButton :loading="exporting === 'cards'" :disabled="Boolean(exporting)" @click="download('cards')">{{ t('号卡清单 CSV') }}</VButton>
   <VButton :loading="exporting === 'records'" :disabled="Boolean(exporting)" @click="download('records')">{{ t('消费记录 CSV') }}</VButton>
  </VSpace>
  <h2 class="font-semibold">{{ t('恢复备份') }}</h2>
  <FormKit type="file" :ignore="true" :label="t('选择 JSON 备份')" :help="t('恢复范围说明')" accept=".json,application/json" :disabled="busy" @input="selectFile" />
  <VLoading v-if="busy" />
  <VAlert v-if="error" type="error" :description="error" />
  <template v-if="preview && !result">
   <VEntityContainer>
    <VEntity v-for="item in preview.items" :key="key(item)">
     <template #start><VEntityField :title="item.title" :description="`${t('号卡')} · ${item.name}`" :max-width="200" /></template>
     <template #end><VButton v-if="item.differences?.length" size="sm" @click="differences = item">{{ t('查看字段差异') }}</VButton></template>
     <template #footer>
      <VSpace direction="column" align="stretch" spacing="sm" class="w-full p-4">
       <FormKit v-model="choices[key(item)]" :ignore="true" type="select" :label="t('恢复方式')" :options="(item.actions ?? []).map(value => ({ value, label: actionLabel(value) }))" :disabled="busy" />
       <VAlert v-if="choices[key(item)] === 'append'" type="info" :description="t('将追加记录', { count: item.appendCount })" />
       <VAlert v-for="warning in item.warnings" :key="warning" type="warning" :description="warning" />
       <VEntityField v-if="!item.differences?.length" :description="t('业务字段一致')" />
      </VSpace>
     </template>
    </VEntity>
   </VEntityContainer>
   <VButton type="secondary" :disabled="busy || !selectedCount" @click="restore">{{ t('按预览恢复') }} ({{ selectedCount }})</VButton>
  </template>
  <template v-if="result">
   <h2 class="font-semibold">{{ t('恢复结果') }}</h2>
   <VEntityContainer>
    <VEntity v-for="item in result.items" :key="`${item.kind}/${item.name}`">
     <template #start><VEntityField :title="item.name" :description="item.message" /></template>
     <template #end><VEntityField :title="statusLabel(item.status || '')" /></template>
    </VEntity>
   </VEntityContainer>
  </template>
  <VButton v-if="content && !busy" @click="inspect">{{ t('重新预览') }}</VButton>
 </VSpace>
 <VModal v-if="differences" :title="`${differences.title} · ${t('查看字段差异')}`" :width="800" mount-to-body @close="differences = undefined">
  <VEntityContainer>
   <VEntity v-for="difference in differences.differences" :key="difference.field">
    <template #start><VEntityField :title="difference.field" /></template>
    <template #footer>
     <VDescription>
      <VDescriptionItem :label="t('当前数据')"><pre class="max-h-48 overflow-auto whitespace-pre-wrap break-all text-xs">{{ difference.before }}</pre></VDescriptionItem>
      <VDescriptionItem :label="t('备份数据')"><pre class="max-h-48 overflow-auto whitespace-pre-wrap break-all text-xs">{{ difference.after }}</pre></VDescriptionItem>
     </VDescription>
    </template>
   </VEntity>
  </VEntityContainer>
 </VModal>
</template>
