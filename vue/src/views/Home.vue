<template>
  <el-card style="margin: 15px; min-height: calc(100vh - 80px)">
    <!--    头部数据-->
    <div>
      <el-row :gutter="20" class="topInfo">
        <el-col :span="6">
          <div id="stuNumDiv" class="el-colDiv">
            <div id="ssv1-main-text" class="nowDiv">实时</div>
            <span class="title">学生统计</span><br/>
            <span class="digital">{{ this.studentNum }}</span><br/>
            <span class="last-span">当前分类总记录数</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div id="haveRoomDiv" class="el-colDiv">
            <div id="ssv2-main-text" class="nowDiv">实时</div>
            <span class="title">住宿人数</span><br/>
            <span class="digital">{{ this.haveRoomStudentNum }}</span><br/>
            <span class="last-span">当前分类总记录数</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div id="repairNum" class="el-colDiv">
            <div id="ssv3-main-text" class="nowDiv">实时</div>
            <span class="title">报修统计</span><br/>
            <span class="digital">{{ this.repairOrderNum }}</span><br/>
            <span class="last-span">当前分类总记录数</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div id="emptyRoom" class="el-colDiv">
            <div id="ssv4-main-text" class="nowDiv">实时</div>
            <span class="title">空宿舍统计</span><br/>
            <span class="digital">{{ this.noFullRoomNum }}</span><br/>
            <span class="last-span">当前分类总记录数</span>
          </div>
        </el-col>
      </el-row>
    </div>
    <!-- 下部-->
    <div class="home-grid">
      <!--   左侧 宿舍通告-->
      <div class="home-panel">
        <span class="section-title">宿舍通告</span>
        <el-timeline>
          <el-timeline-item v-for="(activity, index) in activities.slice(0, 8)" :key="index"
                            :timestamp="activity.releaseTime">
            <span style="font-size: 15px">{{ activity.title }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
      <!--   中部-->
      <div class="home-panel chart-panel">
        <span class="section-title">宿舍学生人数分布图</span>
        <home_echarts/>
      </div>
      <!--  右侧-->
      <div class="home-side">
        <!--   天气组件-->
        <weather/>
        <!--    日历组件-->
        <el-card class="calendar-card">
          <Calender/>
        </el-card>
      </div>
    </div>
  </el-card>
</template>
<script src="@/assets/js/Home.js"></script>
<style scoped>
@import '../assets/css/Home.css';

.home-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.85fr) minmax(0, 1.35fr) minmax(280px, 0.9fr);
  gap: 24px;
  width: 100%;
  margin-top: 28px;
  align-items: start;
}

.home-panel {
  min-width: 0;
  min-height: 460px;
  padding: 20px;
  border: 1px solid #dfe7f3;
  border-radius: 8px;
  background: #fff;
}

.chart-panel {
  min-width: 0;
  min-height: 500px;
  overflow: hidden;
}

.section-title {
  display: block;
  margin-bottom: 22px;
  color: #182233;
  font-size: 19px;
  font-weight: 800;
}

.home-side {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.calendar-card {
  width: 100%;
  max-height: 440px;
}

@media (max-width: 1100px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-panel,
  .chart-panel {
    min-height: auto;
  }
}
</style>
