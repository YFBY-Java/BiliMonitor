<template>
  <div class="monitor-wrapper">
    <div class="background-overlay" style="margin-top: 0"></div>
    <a-card class="fan-monitor" :bordered="false" style="margin-top: 0">
<!--    离顶部距离近一点  -->
      <div class="header-content" style="margin-top: 0;">
        <div class="title-section">
          <h2 class="main-title">{{ currentUname }}</h2>
          <div class="subtitle">BiliBili Fan Monitor</div>
        </div>
        <div class="stats-section">
          <a-statistic
              :title="fansChangeTitle"
              :value="latestFansNum"
              :value-from="previousFansNum"
              animation
              show-group-separator
              class="fan-statistic"
          >
            <template #prefix>
              <div class="trend-indicator">
                <icon-arrow-rise v-if="fansChange >= 0" class="trend-up"/>
                <icon-arrow-fall v-else class="trend-down"/>
              </div>
            </template>
            <template #suffix>
              <icon-user class="user-icon" />
            </template>
          </a-statistic>
          <div class="change-value" :class="{ 'positive': fansChange >= 0, 'negative': fansChange < 0 }">
            {{ fansChange >= 0 ? '+' : '' }}{{ fansChange }}
          </div>
        </div>
      </div>

      <div ref="chartRef" class="chart-container"></div>

      <div class="form-section">
        <a-form :model="formState" @submit="handleSubmit" layout="inline">
          <a-form-item field="uid" label="UID">
            <a-input-number
                v-model="formState.uid"
                placeholder="Enter UID"
                :min="1"
                class="uid-input"
                hide-button
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" :loading="isLoading">
              <template #icon>
                <icon-sync />
              </template>
              Update
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </a-card>
  </div>
</template>

<script setup>
// Script section remains unchanged
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, TitleComponent, DataZoomComponent, MarkLineComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import {
  IconUser,
  IconArrowRise,
  IconArrowFall,
  IconSync
} from '@arco-design/web-vue/es/icon';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, TitleComponent, DataZoomComponent, MarkLineComponent]);

const chartRef = ref(null);
const chart = ref(null);
const currentUname = ref('');
const latestFansNum = ref(0);
const previousFansNum = ref(0);
const fansData = ref([]);
const formState = ref({ uid: 27534330 });
const isLoading = ref(false);

const fansChange = computed(() => {
  return latestFansNum.value - previousFansNum.value;
});

const fansChangeTitle = computed(() => {
  return fansChange.value >= 0 ? 'Total Fans' : 'Total Fans';
});

const yAxisMin = computed(() => {
  if (fansData.value.length < 2) return 0;
  const values = fansData.value.map(item => item[1]);
  const min = Math.min(...values);
  return Math.max(min - 5, 0);
});

const yAxisMax = computed(() => {
  if (fansData.value.length < 2) return 100;
  const values = fansData.value.map(item => item[1]);
  const max = Math.max(...values);
  return max + 5;
});

const fetchFansData = async () => {
  try {
    isLoading.value = true;
    const response = await fetch(`http://localhost:8080/BiliMonitor/fans/${formState.value.uid}`);
    const data = await response.json();
    if (data.code === 200) {
      currentUname.value = data.data.uname;
      previousFansNum.value = latestFansNum.value;
      latestFansNum.value = data.data.fansNum;

      const now = new Date();
      fansData.value.push([now.getTime(), data.data.fansNum]);

      if (fansData.value.length > 1440) {
        fansData.value.shift();
      }
      updateChart();
    }
  } catch (error) {
    console.error('Error fetching fan data:', error);
  } finally {
    isLoading.value = false;
  }
};

const updateChart = () => {
  if (chart.value) {
    const option = {
      series: [{
        data: fansData.value
      }],
      yAxis: {
        min: yAxisMin.value,
        max: yAxisMax.value,
        axisLabel: {
          color: 'rgba(255, 255, 255, 0.8)',
          formatter: (value) => {
            return value.toLocaleString();
          },
          fontSize: 14 // Increased font size
        }
      }
    };

    if (fansData.value.length > 1) {
      const lastTwoPoints = fansData.value.slice(-2);
      const slope = (lastTwoPoints[1][1] - lastTwoPoints[0][1]) / (lastTwoPoints[1][0] - lastTwoPoints[0][0]);
      const intercept = lastTwoPoints[1][1] - slope * lastTwoPoints[1][0];

      const trendLineData = [
        [lastTwoPoints[0][0], slope * lastTwoPoints[0][0] + intercept],
        [lastTwoPoints[1][0] + 3600000, slope * (lastTwoPoints[1][0] + 3600000) + intercept]
      ];

      option.series.push({
        name: 'Trend',
        type: 'line',
        showSymbol: false,
        data: trendLineData,
        lineStyle: {
          type: 'dashed',
          width: 2,
          color: 'rgba(231, 76, 60, 0.8)'
        }
      });
    }

    chart.value.setOption(option);
  }
};

const initChart = () => {
  chart.value = echarts.init(chartRef.value);
  const option = {
    grid: {
      top: 50,
      right: 30,
      bottom: 60,
      left: 50,
      containLabel: true
    },
    title: {
      text: 'Fan Count Trend',
      left: 'center',
      top: 20,
      textStyle: {
        color: 'rgba(255, 255, 255, 0.9)',
        fontSize: 18,
        fontWeight: 'bold'
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        const date = new Date(params[0].value[0]);
        return `
          <div style="padding: 3px 6px;">
            <div style="color: #666; font-size: 12px;">${date.toLocaleString()}</div>
            <div style="margin-top: 2px;">
              <span style="font-weight: bold;">Fans:</span>
              ${params[0].value[1].toLocaleString()}
            </div>
          </div>
        `;
      },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: 'rgba(255, 255, 255, 0.2)',
      borderWidth: 1,
      textStyle: {
        color: '#333'
      }
    },
    xAxis: {
      type: 'time',
      splitLine: {
        show: false
      },
      axisLabel: {
        formatter: (value) => {
          const date = new Date(value);
          return date.toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit'
          });
        },
        color: 'rgba(255, 255, 255, 0.8)'
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.2)'
        }
      }
    },
    yAxis: {
      type: 'value',
      splitLine: {
        show: true,
        lineStyle: {
          type: 'dashed',
          color: 'rgba(255, 255, 255, 0.1)'
        }
      },
      axisLabel: {
        color: 'rgba(255, 255, 255, 0.8)',
        formatter: (value) => {
          return value.toLocaleString();
        },
        fontSize: 14 // Increased font size
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.2)'
        }
      },
      min: yAxisMin.value,
      max: yAxisMax.value
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        start: 0,
        end: 100,
        textStyle: {
          color: 'rgba(255, 255, 255, 0.8)'
        },
        borderColor: 'rgba(255, 255, 255, 0.2)',
        backgroundColor: 'rgba(47, 69, 84, 0.3)',
        fillerColor: 'rgba(167, 183, 204, 0.2)',
        handleStyle: {
          color: '#fff',
          opacity: 0.8
        }
      }
    ],
    series: [{
      name: 'Fan Count',
      type: 'line',
      showSymbol: false,
      smooth: true,
      lineStyle: {
        width: 3,
        color: 'rgba(147, 112, 219, 0.8)'
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(147, 112, 219, 0.4)' },
          { offset: 1, color: 'rgba(147, 112, 219, 0.1)' }
        ])
      },
      data: fansData.value
    }]
  };
  chart.value.setOption(option);
};

const handleSubmit = () => {
  fansData.value = [];
  fetchFansData();
};

let timer;

onMounted(() => {
  initChart();
  fetchFansData();
  timer = setInterval(fetchFansData, 15000);

  window.addEventListener('resize', () => {
    chart.value?.resize();
  });
});

onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
  }
  if (chart.value) {
    chart.value.dispose();
  }
  window.removeEventListener('resize', () => {
    chart.value?.resize();
  });
});
</script>

<style scoped>
.monitor-wrapper {
  padding: 3px 16px 16px 16px;  /* 将 padding-top 改为 3px */
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background-image: url('../../image/爱莉希雅.png');
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  background-attachment: fixed;
  overflow: hidden;
}

.fan-monitor {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto 0 auto;  /* 将 margin-top 设置为 0，减少与顶部的距离 */
  background: rgba(23, 23, 43, 0.85) !important;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(6px);
  position: relative;
  z-index: 1;
}



body {
  padding: 0;
  height: 100vh;  /* 确保 body 高度填满整个视口 */
  overflow: hidden;  /* 禁止滚动 */
  /* 将 body 元素下移，调整离顶部的距离 */
  margin: 5px 0 0;
}



.background-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.1);
}



.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;  /* Reduced margin */
}

.title-section {
  flex: 1;
}

.main-title {
  font-size: 26px;  /* Slightly smaller font size */
  font-weight: 700;
  color: rgba(255, 255, 255, 0.95);
  margin: 0;
  line-height: 1.4;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.subtitle {
  color: rgba(255, 255, 255, 0.7);
  font-size: 15px;  /* Slightly smaller font size */
  margin-top: 6px;  /* Reduced margin */
}

.stats-section {
  text-align: right;
}

.fan-statistic {
  margin-bottom: 10px;  /* Slightly smaller margin */
}

.fan-statistic :deep(.arco-statistic-value) {
  font-size: 32px;  /* Slightly smaller font size */
  font-weight: 700;
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.fan-statistic :deep(.arco-statistic-title) {
  font-size: 15px;  /* Slightly smaller font size */
  color: rgba(255, 255, 255, 0.7);
}

.trend-indicator {
  margin-right: 10px;  /* Slightly reduced margin */
  display: inline-flex;
  align-items: center;
}

.trend-up {
  color: #a8e6cf;
}

.trend-down {
  color: #ff8b94;
}

.user-icon {
  margin-left: 10px;  /* Slightly reduced margin */
  color: rgba(255, 255, 255, 0.8);
}

.change-value {
  font-size: 15px;  /* Slightly smaller font size */
  font-weight: 600;
}

.change-value.positive {
  color: #a8e6cf;
}

.change-value.negative {
  color: #ff8b94;
}

.chart-container {
  height: 500px;  /* Slightly smaller chart height */
  width: 100%;
  margin: 16px 0;  /* Slightly smaller margin */
}

.form-section {
  margin-top: 24px;  /* Slightly smaller margin */
  padding-top: 24px;  /* Slightly smaller padding */
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.form-section :deep(.arco-form-item-label) {
  color: rgba(255, 255, 255, 0.8);
}

.uid-input {
  width: 180px;  /* Slightly smaller width */
}

:deep(.arco-input-number-inner) {
  border-radius: 8px;
  height: 34px;  /* Slightly smaller height */
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

:deep(.arco-input-number-inner:hover),
:deep(.arco-input-number-inner:focus) {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.3);
}

:deep(.arco-btn-primary) {
  border-radius: 8px;
  background: rgba(147, 112, 219, 0.8);
  border-color: rgba(147, 112, 219, 0.8);
  height: 34px;  /* Slightly smaller height */
  padding: 0 18px;  /* Slightly smaller padding */
  font-size: 14px;  /* Slightly smaller font size */
  backdrop-filter: blur(4px);
}

:deep(.arco-btn-primary:hover) {
  background: rgba(147, 112, 219, 0.9);
  border-color: rgba(147, 112, 219, 0.9);
}

@media (max-width: 768px) {
  .monitor-wrapper {
    padding: 12px;  /* Slightly smaller padding */
  }

  .header-content {
    flex-direction: column;
  }

  .stats-section {
    text-align: left;
    margin-top: 16px;  /* Slightly reduced margin */
  }

  .chart-container {
    height: 350px;  /* Slightly smaller chart height */
  }

  .form-section :deep(.arco-form-inline) {
    flex-direction: column;
  }

  .form-section :deep(.arco-form-item) {
    margin-right: 0;
    margin-bottom: 12px;  /* Slightly smaller margin */
  }

  .uid-input {
    width: 100%;
  }
}
</style>

