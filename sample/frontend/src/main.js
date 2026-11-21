import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(`i-ep-${key.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()}`, component)
}

app.use(ElementPlus)
app.mount('#app')
