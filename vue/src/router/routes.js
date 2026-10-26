import { campusModules } from '../config/modules.js'

export const routes = [
  { path: '/', redirect: '/home' },
  { path: '/Login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/', component: () => import('../layout/Layout.vue'), children: [
      { path: 'home', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: 'Home' } },
      ...campusModules.map(module => ({
        path: module.path.slice(1), name: module.path.slice(1), component: module.component,
        props: { module }, meta: { title: module.name, moduleId: module.id },
      })),
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' },
]
