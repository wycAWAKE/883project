import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 8080,
    // 答辩链路-前后端通信：浏览器访问前端 8080，Axios 请求 /api；这里把 /api 转发到 Spring Boot 的 9090，下一步看后端 application.properties 和对应 Controller。
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        // 答辩链路-去掉/api：/api/stu/find 转发后变成 http://localhost:9090/stu/find；下一步进入 StudentController 的 @RequestMapping('/stu') + @GetMapping('/find')。
        rewrite: path => path.replace(/^\/api/, '')
      }
    }
  }
})
