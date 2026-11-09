import { definePlugin } from '@halo-dev/ui-shared'
import IconSimCard from '~icons/ri/sim-card-2-line'
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
        menu: { name: 'SIMnook', icon: markRaw(IconSimCard), priority: 0 },
      },
    },
  }],
})
