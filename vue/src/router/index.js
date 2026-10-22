import Layout from '../layout/Layout.vue'
import {createRouter, createWebHistory} from "vue-router";


export const constantRoutes = [
    {path: '/', redirect: '/home'},
    {path: '/Login', name: 'Login', component: () => import("@/views/Login.vue")},
    {path: '/login', redirect: '/Login'},
    {
        path: '/Layout', name: 'Layout', component: Layout, children: [
            //
            {path: '/home', name: 'Home', component: () => import("@/views/Home.vue")},
            // 答辩链路-学生信息入口：用户点击“学生信息”菜单后进入 /stuInfo；下一步看 src/views/StuInfo.vue 的表格和 script 引入。
            {path: '/stuInfo', name: 'StuInfo', component: () => import("@/views/StuInfo.vue")},
            {path: '/dormManagerInfo', name: 'DormManagerInfo', component: () => import("@/views/DormManagerInfo.vue")},
            {path: '/buildingInfo', name: 'BuildingInfo', component: () => import("@/views/BuildingInfo.vue")},
            {path: '/roomInfo', name: 'RoomInfo', component: () => import("@/views/RoomInfo.vue")},
            {path: '/noticeInfo', name: 'NoticeInfo', component: () => import("@/views/NoticeInfo.vue")},
            {path: '/adjustRoomInfo', name: 'AdjustRoomInfo', component: () => import("@/views/AdjustRoomInfo.vue")},
            {path: '/repairInfo', name: 'RepairInfo', component: () => import("@/views/RepairInfo.vue")},
            {path: '/visitorInfo', name: 'VisitorInfo', component: () => import("@/views/VisitorInfo.vue")},
            {path: '/WaterInfo', name: 'WaterInfo', component: () => import("@/views/WaterInfo.vue")},
            {path: '/ElectricInfo', name: 'ElectricInfo', component: () => import("@/views/ElectricInfo.vue")},
            {path: '/CheckInfo', name: 'CheckInfo', component: () => import("@/views/CheckInfo.vue")},
            {path: '/permissionInfo', name: 'PermissionInfo', component: () => import("@/views/PermissionInfo.vue")},
            //
            {path: '/myRoomInfo', name: 'MyRoomInfo', component: () => import("@/views/MyRoomInfo.vue")},
            {path: '/applyRepairInfo', name: 'ApplyRepairInfo', component: () => import("@/views/ApplyRepairInfo.vue")},
            {path: '/applyChangeRoom', name: 'ApplyChangeRoom', component: () => import("@/views/ApplyChangeRoom.vue")},

            {path: '/selfInfo', name: 'SelfInfo', component: () => import("@/views/SelfInfo.vue")},
        ]
    },

]
const router = createRouter({
    routes: constantRoutes,
    history: createWebHistory(import.meta.env.BASE_URL)
})
//路由守卫
router.beforeEach((to, from, next) => {
    //to 要访问的路径
    //from 代表从哪个路径跳转而来
    // next 是函数，表示放行
    // next() 放行
    // next('/*') 强制跳转
    const user = window.sessionStorage.getItem('user')
    if (to.path === '/Login') {
        return next();
    }
    if (!user) {
        return next('/Login')
    }
    if (to.path === '/' && user) {
        return next('/home')
    }
    next()
})

export default router
