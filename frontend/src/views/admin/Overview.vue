<template>
  <div class="overview">
    <!-- 顶部指标卡片 -->
    <div class="stat-cards">
      <div v-for="c in cards" :key="c.label" class="stat-card aidoc-card">
        <div class="stat-icon" :style="{ background: c.bg, color: c.color }">
          <el-icon :size="22"><component :is="c.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="charts-row">
      <div class="aidoc-card chart-card">
        <h3 class="chart-title">近 7 日访问量</h3>
        <div ref="visitChartRef" class="chart" />
      </div>
      <div class="aidoc-card chart-card">
        <h3 class="chart-title">近 7 日问答消息量</h3>
        <div ref="msgChartRef" class="chart" />
      </div>
    </div>

    <!-- Top10 文档 + 最近访问日志 -->
    <div class="bottom-row">
      <div class="aidoc-card bottom-card">
        <h3 class="chart-title">Top10 大文档（按内容字符数）</h3>
        <el-table :data="overview?.topDocuments || []" size="small" max-height="300">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="userName" label="上传人" width="110" />
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="字符数" width="100">
            <template #default="{ row }">{{ row.charCount }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="aidoc-card bottom-card">
        <h3 class="chart-title">最近访问日志</h3>
        <el-table v-loading="logLoading" :data="logs" size="small" max-height="300">
          <el-table-column prop="createTime" label="时间" width="150" />
          <el-table-column prop="username" label="用户" width="100">
            <template #default="{ row }">{{ row.username || '-' }}</template>
          </el-table-column>
          <el-table-column label="方法" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="methodType(row.method)" effect="plain">{{ row.method }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="160" show-overflow-tooltip />
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ row.costMs }}ms</template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="logPage"
            :page-size="10"
            :total="logTotal"
            layout="total, prev, pager, next"
            small
            background
            @current-change="loadLogs"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ChatLineRound, Document, Folder, Message, User, View } from '@element-plus/icons-vue'
import { overview as fetchOverview, pageAccessLogs } from '@/api/admin'
import type { AccessLogVO, StatsOverviewVO } from '@/api/types'

const overview = ref<StatsOverviewVO | null>(null)
const visitChartRef = ref<HTMLDivElement>()
const msgChartRef = ref<HTMLDivElement>()
let visitChart: echarts.ECharts | null = null
let msgChart: echarts.ECharts | null = null

const logs = ref<AccessLogVO[]>([])
const logPage = ref(1)
const logTotal = ref(0)
const logLoading = ref(false)

const cards = computed(() => {
  const o = overview.value
  return [
    { label: '用户总数', value: o?.userCount ?? 0, icon: User, color: '#1e4e8c', bg: '#e7eef8' },
    { label: '文档总数', value: o?.documentCount ?? 0, icon: Document, color: '#d4380d', bg: '#fdebe7' },
    { label: '会话总数', value: o?.sessionCount ?? 0, icon: ChatLineRound, color: '#237804', bg: '#e8f5e0' },
    { label: '消息总数', value: o?.messageCount ?? 0, icon: Message, color: '#d48806', bg: '#fdf4e3' },
    { label: '今日访问', value: o?.todayVisitCount ?? 0, icon: View, color: '#7a2e8c', bg: '#f3e8f6' },
    { label: '今日问答', value: o?.todayMessageCount ?? 0, icon: Folder, color: '#0f766e', bg: '#e0f5f2' }
  ]
})

async function loadOverview() {
  overview.value = await fetchOverview()
  renderCharts()
}

function renderCharts() {
  const trend = overview.value?.trend || []
  if (!trend.length) return
  const dates = trend.map((t) => t.date.slice(5))
  const visits = trend.map((t) => t.visitCount)
  const messages = trend.map((t) => t.messageCount)

  if (visitChartRef.value) {
    visitChart = echarts.init(visitChartRef.value)
    visitChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 30, bottom: 24 },
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '访问量',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: visits,
          lineStyle: { width: 2.5, color: '#1e4e8c' },
          itemStyle: { color: '#1e4e8c' },
          areaStyle: { color: 'rgba(30,78,140,0.08)' }
        }
      ]
    })
  }
  if (msgChartRef.value) {
    msgChart = echarts.init(msgChartRef.value)
    msgChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 30, bottom: 24 },
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '消息量',
          type: 'bar',
          barWidth: '45%',
          data: messages,
          itemStyle: { color: '#5b7fae', borderRadius: [4, 4, 0, 0] }
        }
      ]
    })
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await pageAccessLogs({ current: logPage.value, size: 10 })
    logs.value = data.records
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

function methodType(m: string): 'success' | 'warning' | 'danger' | 'primary' {
  if (m === 'GET') return 'success'
  if (m === 'POST') return 'warning'
  if (m === 'DELETE') return 'danger'
  return 'primary'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function onResize() {
  visitChart?.resize()
  msgChart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await loadOverview()
  await nextTick(loadLogs)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  visitChart?.dispose()
  msgChart?.dispose()
})
</script>

<style scoped lang="scss">
.overview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  .stat-icon {
    width: 46px;
    height: 46px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .stat-value {
    font-size: 22px;
    font-weight: 700;
    color: var(--aidoc-text-main);
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: var(--aidoc-text-sub);
  }
}
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.chart-card {
  .chart {
    height: 260px;
  }
}
.chart-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--aidoc-text-main);
  display: flex;
  align-items: center;
  gap: 8px;
  &::before {
    content: '';
    width: 4px;
    height: 14px;
    border-radius: 2px;
    background: var(--el-color-primary);
  }
}
.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.pager {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
