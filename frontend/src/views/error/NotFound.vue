<template>
  <!-- 404 兜底页 -->
  <div class="not-found">
    <el-result icon="warning" title="404" sub-title="抱歉，您访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { homePathOf } from '@/router'

const router = useRouter()
const store = useUserStore()

function goHome() {
  router.push(homePathOf(store.role || 'USER'))
}
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--aidoc-bg);
}
</style>
