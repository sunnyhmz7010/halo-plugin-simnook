<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

defineOptions({ inheritAttrs: false })
const props = defineProps<{
 inputType: string
 label?: string
 help?: string
 options?: { label: string; value: string }[]
 english: { label: string; help?: string; options?: Record<string, string> }
}>()
const { locale } = useI18n({ useScope: 'global' })
const useEnglish = computed(() => !locale.value.startsWith('zh'))
const options = computed(() => props.options?.map(option => ({ ...option,
 label: useEnglish.value ? props.english.options?.[option.value] ?? option.label : option.label,
})))
const optionProps = computed(() => props.options ? { options: options.value } : {})
</script>

<template>
 <FormKit v-bind="{ ...$attrs, ...optionProps }" :type="inputType" :label="useEnglish ? english.label : label"
  :help="useEnglish ? english.help ?? help : help" />
</template>
