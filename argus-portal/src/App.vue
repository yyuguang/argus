<template>
  <RouterView v-if="route.meta.public" />

  <div v-else class="admin-shell">
    <aside class="admin-sidebar">
      <div class="sidebar-top">
        <div class="brand">
          <span class="brand-mark">A</span>
          <div>
            <p class="brand-title">Argus Admin</p>
            <p class="brand-subtitle">企业级代码治理平台</p>
          </div>
        </div>

        <div class="tenant-card">
          <span class="tenant-label">当前空间</span>
          <strong>Company Workspace</strong>
          <p>面向内部团队统一管理仓库接入、Webhook 与治理策略。</p>
        </div>
      </div>

      <nav class="menu">
        <div v-for="group in menuGroups" :key="group.title" class="menu-group">
          <p class="menu-group-title">{{ group.title }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            active-class="active"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <div>
              <span class="menu-text">{{ item.label }}</span>
              <small class="menu-desc">{{ item.description }}</small>
            </div>
          </RouterLink>
        </div>
      </nav>
    </aside>

    <div class="admin-main">
      <header class="admin-header">
        <div>
          <p class="breadcrumb">{{ currentSection }}</p>
          <h1 class="page-title">{{ route.meta.title || 'Argus 管理后台' }}</h1>
        </div>

        <div class="header-actions">
          <div class="header-search">
            <span>检索</span>
            <input type="text" placeholder="搜索菜单、配置或仓库..." disabled />
          </div>
          <div class="user-pill">
            <span class="user-dot"></span>
            {{ session?.displayName || '平台管理员' }}
            <button class="logout-link" type="button" @click="handleLogout">退出</button>
          </div>
        </div>
      </header>

      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import { getSession, logout } from './auth/session'
import { menuGroups } from './config/navigation'

const route = useRoute()
const router = useRouter()

const currentSection = computed(() => route.meta.section || '平台总览')
const session = computed(() => getSession())

function handleLogout() {
  logout()
  router.replace('/login')
}
</script>
