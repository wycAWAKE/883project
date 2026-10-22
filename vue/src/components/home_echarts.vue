<template>
  <div class="home-chart">
    <div v-if="chartRows.length" class="bar-list">
      <div v-for="row in chartRows" :key="row.name" class="bar-row">
        <div class="bar-label">{{ row.name }}号楼</div>
        <div class="bar-track">
          <div class="bar-fill" :style="{ width: row.percent + '%' }"></div>
        </div>
        <div class="bar-value">{{ row.value }}人</div>
      </div>
    </div>
    <el-empty v-else description="暂无宿舍人数数据" :image-size="96" />
  </div>
</template>

<script>
import request from "@/utils/request";

export default {
  name: "home_echarts",
  data() {
    return {
      buildings: [],
      counts: [],
      retryTimer: null,
      retryCount: 0,
    };
  },
  computed: {
    chartRows() {
      const maxCount = Math.max(...this.counts, 1);
      return this.buildings.map((name, index) => {
        const value = Number(this.counts[index] || 0);
        return {
          name,
          value,
          percent: Math.max((value / maxCount) * 100, value > 0 ? 8 : 0),
        };
      });
    },
  },
  created() {
    this.getBuildingNum();
  },
  beforeUnmount() {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
    }
  },
  methods: {
    scheduleRetry() {
      if (this.retryCount >= 8 || this.retryTimer) {
        return;
      }
      this.retryCount += 1;
      this.retryTimer = setTimeout(() => {
        this.retryTimer = null;
        this.getBuildingNum();
      }, 1500);
    },
    resetRetry() {
      this.retryCount = 0;
      if (this.retryTimer) {
        clearTimeout(this.retryTimer);
        this.retryTimer = null;
      }
    },
    getBuildingNum() {
      // 答辩链路-图表楼栋来源：先请求 /building/getBuildingName 获取楼栋编号；下一步看 DormBuildController.getBuildingName()，最终读取 dorm_build 表。
      request.get("/building/getBuildingName").then((res) => {
        if (res.code !== "0") {
          this.scheduleRetry();
          return;
        }
        this.buildings = res.data || [];
        // 答辩链路-图表人数来源：再按楼栋数量请求 /room/getEachBuildingStuNum/{num}；下一步看 DormRoomController.getEachBuildingStuNum()，最终统计 dorm_room 表。
        request.get("/room/getEachBuildingStuNum/" + this.buildings.length).then((result) => {
          if (result.code === "0") {
            this.counts = result.data || [];
            this.resetRetry();
          } else {
            this.scheduleRetry();
          }
        }).catch(() => this.scheduleRetry());
      }).catch(() => this.scheduleRetry());
    },
  },
};
</script>

<style scoped>
.home-chart {
  width: 100%;
  min-height: 320px;
  padding: 10px 4px 0;
}

.bar-list {
  display: grid;
  gap: 20px;
}

.bar-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 56px;
  gap: 12px;
  align-items: center;
}

.bar-label,
.bar-value {
  color: #273851;
  font-size: 14px;
  font-weight: 700;
}

.bar-value {
  text-align: right;
}

.bar-track {
  height: 18px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf3fb;
  box-shadow: inset 0 0 0 1px rgba(210, 223, 240, 0.75);
}

.bar-fill {
  height: 100%;
  min-width: 4px;
  border-radius: 999px;
  background: linear-gradient(90deg, #2f74e8, #19a88b);
  box-shadow: 0 8px 18px rgba(47, 116, 232, 0.2);
  transition: width 0.25s ease;
}

@media (max-width: 768px) {
  .bar-row {
    grid-template-columns: 52px minmax(0, 1fr) 48px;
    gap: 8px;
  }
}
</style>
