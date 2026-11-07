import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory('/simnook/'),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/card/:id',
      name: 'card-detail',
      component: () => import('@/views/CardDetail.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

export default router
