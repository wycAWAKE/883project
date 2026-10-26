import {createApp} from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import 'overlayscrollbars/overlayscrollbars.css'
import './assets/css/global.css'
// element-plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ELIcons from '@element-plus/icons-vue'
import en from 'element-plus/es/locale/lang/en'
import { displayText } from './utils/english'

const app = createApp(App)
    .use(ElementPlus, {
        locale: en
    })
for (let iconName in ELIcons) {
    app.component(iconName, ELIcons[iconName])
}
app.use(router)
app.use(store)
app.config.globalProperties.$en = displayText
app.config.globalProperties.$enCell = (row, column, value) => displayText(value)
app.mount('#app')
