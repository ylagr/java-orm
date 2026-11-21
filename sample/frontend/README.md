# 商城管理系统前端 (Vue 3 + Element Plus)

本前端模块专用于配合 JavaORM (MyBatis-Plus 兼容模式) 演示商城管理后台。

## 运行方式

### 方式 1：开箱即用（推荐）
后端 Spring Boot 模块已经将打包好的单页应用内置在 `src/main/resources/static/index.html`。
直接启动后端 Spring Boot 应用：
```bash
mvn spring-boot:run -pl sample
```
然后在浏览器中打开：
[http://localhost:8080/](http://localhost:8080/)
即可直接体验完整的动态商城后台交互！

### 方式 2：独立 Vite 前端工程化开发
如需进行前端代码修改或独立开发：
```bash
cd sample/frontend
npm install
npm run dev
```
本地 Vite 开发服务器将启动在 `http://localhost:3000`，并通过代理将 `/api` 请求转发给后端的 `http://localhost:8080`。
