import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import SettingField from './views/SettingField.vue'

export default definePlugin({
  components: { 'simnook-setting-field': markRaw(SettingField) },
  routes: [{
    parentName: 'ToolsRoot',
    route: {
      path: '/simnook',
      name: 'Simnook',
      component: () => import('./views/HomeView.vue'),
      meta: {
        title: 'SIMnook',
        searchable: true,
        permissions: ['plugin:simnook:manage'],
        menu: { name: 'SIMnook', priority: 0 },
      },
    },
  }],
})
