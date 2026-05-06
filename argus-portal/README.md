# Argus Portal

`argus-portal` 是 Argus 的独立前端模块，基于 `Vue 3 + Vite + Vue Router`。

## 启动方式

```bash
cd argus-portal
npm install
npm run dev
```

默认开发地址：

- 前端: `http://localhost:5173`
- 后端代理: `/api -> http://localhost:8900`

## 当前页面

- `/scm-config`：SCM 仓库配置页

## 对接接口

- `GET /api/v1/scm/configs`
- `POST /api/v1/scm/configs`
- `PUT /api/v1/scm/configs/{id}`
