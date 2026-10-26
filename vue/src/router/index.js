import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes.js'
import request from '../utils/request.js'
import { createSessionGuard } from '../utils/session.js'

const router = createRouter({ routes, history: createWebHistory(import.meta.env.BASE_URL) })
router.beforeEach(createSessionGuard({ request, storage: window.sessionStorage }))
router.afterEach(to => { document.title = to.meta.title ? `${to.meta.title} | SmartCampus` : 'SmartCampus' })

export default router
