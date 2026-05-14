<template>
  <section class="login-shell">
    <div class="login-panel">
      <div class="login-intro">
        <p class="eyebrow">Argus Admin</p>
        <h1>企业级代码治理后台</h1>
        <p>
          登录后进入仓库接入、评审任务、Webhook 指引与系统设置模块。
          当前为初始化管理账号入口，后续可以接公司 SSO 或统一认证。
        </p>

        <div class="login-tips">
          <div>
            <span>默认账号</span>
            <strong>admin</strong>
          </div>
          <div>
            <span>默认密码</span>
            <strong>Argus@123</strong>
          </div>
        </div>
      </div>

      <div class="login-card">
        <div class="login-card-header">
          <h2>管理员登录</h2>
          <p>请输入后台账号信息</p>
        </div>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
          class="task-alert"
        />

        <el-form label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="账号">
            <el-input v-model="form.username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button type="primary" class="login-button" :loading="submitting" @click="handleLogin">
            登录系统
          </el-button>
        </el-form>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../auth/session'

const router = useRouter()
const route = useRoute()

const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: 'admin',
  password: 'Argus@123',
})

async function handleLogin() {
  submitting.value = true
  errorMessage.value = ''
  try {
    login(form.username.trim(), form.password)
    await router.replace(route.query.redirect || '/dashboard')
  } catch (error) {
    errorMessage.value = error.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>
