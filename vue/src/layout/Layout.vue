<template>
  <el-container class="app-shell">
    <!--    侧边功能栏-->
    <el-aside class="app-aside" width="200px">
      <Aside/>
    </el-aside>
    <el-container class="app-main">
      <!--      头部-->
      <el-header class="app-header">
        <Header/>
      </el-header>
      <OverlayScrollbarsComponent
        element="main"
        class="app-content"
        :options="scrollOptions"
        defer
      >
        <!--        内容区域-->
        <router-view/>
      </OverlayScrollbarsComponent>
    </el-container>
  </el-container>
</template>

<script>
import Header from "@/components/Header.vue";
import Aside from "@/components/Aside.vue";
import { OverlayScrollbarsComponent } from "overlayscrollbars-vue";

export default {
  name: "layout",
  components: {
    Header,
    Aside,
    OverlayScrollbarsComponent,
  },
  data() {
    return {
      scrollOptions: {
        scrollbars: {
          theme: "os-theme-zijin",
          autoHide: "leave",
          autoHideDelay: 450,
          clickScroll: true,
        },
      },
    };
  },
}
</script>

<style scoped>
.app-shell {
  height: 100vh;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(244, 248, 255, 0.82)),
    #f3f7ff;
}

.app-aside {
  height: 100vh;
  background: #ffffff;
  border-right: 1px solid rgba(222, 232, 246, 0.95);
  box-shadow: 10px 0 30px rgba(35, 67, 114, 0.06);
}

.app-main {
  min-width: 0;
  height: 100vh;
}

.app-header {
  height: 58px;
  border-bottom: 1px solid rgba(222, 232, 246, 0.95);
  padding: 0;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(14px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.app-content {
  flex: 1;
  min-height: 0;
  height: calc(100vh - 58px);
  padding: 0 8px 18px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .app-shell {
    display: grid;
    grid-template-rows: 58px 58px minmax(0, 1fr);
    height: 100vh;
    overflow: hidden;
  }

  .app-aside {
    grid-row: 2;
    width: 100% !important;
    min-height: auto;
    height: 58px;
    position: static;
    top: auto;
    z-index: 20;
    border-right: 0;
    border-bottom: 1px solid rgba(222, 232, 246, 0.95);
    box-shadow: 0 10px 30px rgba(35, 67, 114, 0.08);
  }

  .app-main {
    display: contents;
    height: auto;
  }

  .app-header {
    grid-row: 1;
    height: 58px;
    min-height: 58px;
    position: static;
  }

  .app-content {
    grid-row: 3;
    height: 100%;
    padding: 0 0 14px;
  }
}
</style>
