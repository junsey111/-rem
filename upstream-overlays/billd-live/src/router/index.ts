import { createRouter, createWebHistory } from 'vue-router';

import Layout from '@/layout/index.vue';

import type { RouteRecordRaw } from 'vue-router';

// 默认路由
export const defaultRoutes: RouteRecordRaw[] = [
  { path: '/', component: () => import('@/views/admin/index.vue') },
  { path: '/admin', component: () => import('@/views/admin/index.vue') },
  { path: '/console', component: () => import('@/views/admin/index.vue') },
  { path: '/admin/users', component: () => import('@/views/admin/index.vue') },
  {
    path: '/admin/finance',
    component: () => import('@/views/admin/index.vue'),
  },
  {
    path: '/admin/creators',
    component: () => import('@/views/admin/index.vue'),
  },
  {
    path: '/admin/reports',
    component: () => import('@/views/admin/index.vue'),
  },
  { path: '/admin/chat', component: () => import('@/views/admin/index.vue') },
  {
    path: '/legacy',
    component: Layout,
    children: [
      {
        name: 'home',
        path: '/legacy',
        component: () => import('@/views/home/index.vue'),
      },
      {
        name: 'push',
        path: '/push',
        component: () => import('@/views/push/index.vue'),
      },
      {
        name: 'pull',
        path: '/:roomId',
        component: () => import('@/views/pull/index.vue'),
      },
      {
        name: 'bilibiliPush',
        path: '/bilibiliPush',
        component: () => import('@/views/bilibiliPush/index.vue'),
      },
      {
        name: 'test1',
        path: '/test1',
        component: () => import('@/views/test1/index.vue'),
      },
      {
        name: 'srsDemoOne',
        path: '/srs-demo1',
        component: () => import('@/views/srs-demo1/index.vue'),
      },
    ],
  },
];
const router = createRouter({
  routes: defaultRoutes,
  history: createWebHistory(),
});

export default router;
