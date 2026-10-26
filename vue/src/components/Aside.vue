<template>
  <OverlayScrollbarsComponent
    class="aside-scroll"
    :options="scrollOptions"
    defer
  >
    <el-menu
      :default-active="path"
      class="side-menu"
      router
      unique-opened
    >
      <div class="logo-box">
        <img alt="" src="@/assets/logo.png">
      </div>

      <template v-for="menu in menus" :key="menu.id">
        <el-sub-menu v-if="menu.children && menu.children.length" :index="String(menu.id)">
          <template #title>
            <el-icon v-if="menu.icon">
              <component :is="menu.icon" />
            </el-icon>
            <span>{{ $en(menu.menuName) }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children"
            :key="child.id"
            :index="child.menuPath"
          >
            {{ $en(child.menuName) }}
          </el-menu-item>
        </el-sub-menu>

        <el-menu-item v-else :index="menu.menuPath">
          <el-icon v-if="menu.icon">
            <component :is="menu.icon" />
          </el-icon>
          <span>{{ $en(menu.menuName) }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </OverlayScrollbarsComponent>
</template>

<script>
import request from "@/utils/request";
import { ElMessage } from "element-plus";
import { OverlayScrollbarsComponent } from "overlayscrollbars-vue";

const fallbackMenus = {
  admin: [
    { id: 1, menuName: "Home", menuPath: "/home", icon: "House", children: [] },
    { id: 2, menuName: "Users", icon: "User", children: [
      { id: 21, menuName: "Students", menuPath: "/stuInfo" },
      { id: 22, menuName: "Dormitory Staff", menuPath: "/dormManagerInfo" },
    ] },
    { id: 3, menuName: "Dormitories", icon: "Coin", children: [
      { id: 31, menuName: "Buildings", menuPath: "/buildingInfo" },
      { id: 32, menuName: "Rooms", menuPath: "/roomInfo" },
    ] },
    { id: 4, menuName: "Information", icon: "Message", children: [
      { id: 41, menuName: "Announcements", menuPath: "/noticeInfo" },
      { id: 42, menuName: "Maintenance Requests", menuPath: "/repairInfo" },
    ] },
    { id: 5, menuName: "Applications", icon: "PieChart", children: [
      { id: 51, menuName: "Room Transfers", menuPath: "/adjustRoomInfo" },
    ] },
    { id: 6, menuName: "Visitors", menuPath: "/visitorInfo", icon: "View", children: [] },
    { id: 10, menuName: "Utilities", icon: "SetUp", children: [
      { id: 101, menuName: "Water Charges", menuPath: "/WaterInfo" },
      { id: 102, menuName: "Electricity Charges", menuPath: "/ElectricInfo" },
    ] },
    { id: 11, menuName: "Room Inspections", menuPath: "/CheckInfo", icon: "Tickets", children: [] },
    { id: 12, menuName: "Permissions", menuPath: "/permissionInfo", icon: "Key", children: [] },
    { id: 13, menuName: "My Profile", menuPath: "/selfInfo", icon: "Setting", children: [] },
  ],
  dormManager: [
    { id: 1, menuName: "Home", menuPath: "/home", icon: "House", children: [] },
    { id: 2, menuName: "Users", icon: "User", children: [
      { id: 21, menuName: "Students", menuPath: "/stuInfo" },
    ] },
    { id: 3, menuName: "Dormitories", icon: "Coin", children: [
      { id: 31, menuName: "Buildings", menuPath: "/buildingInfo" },
      { id: 32, menuName: "Rooms", menuPath: "/roomInfo" },
    ] },
    { id: 4, menuName: "Information", icon: "Message", children: [
      { id: 42, menuName: "Maintenance Requests", menuPath: "/repairInfo" },
    ] },
    { id: 5, menuName: "Applications", icon: "PieChart", children: [
      { id: 51, menuName: "Room Transfers", menuPath: "/adjustRoomInfo" },
    ] },
    { id: 6, menuName: "Visitors", menuPath: "/visitorInfo", icon: "View", children: [] },
    { id: 11, menuName: "Room Inspections", menuPath: "/CheckInfo", icon: "Tickets", children: [] },
    { id: 13, menuName: "My Profile", menuPath: "/selfInfo", icon: "Setting", children: [] },
  ],
  stu: [
    { id: 1, menuName: "Home", menuPath: "/home", icon: "House", children: [] },
    { id: 7, menuName: "My Room", menuPath: "/myRoomInfo", icon: "School", children: [] },
    { id: 8, menuName: "Request Room Transfer", menuPath: "/applyChangeRoom", icon: "TakeawayBox", children: [] },
    { id: 9, menuName: "Request Maintenance", menuPath: "/applyRepairInfo", icon: "SetUp", children: [] },
    { id: 10, menuName: "Utilities", icon: "SetUp", children: [
      { id: 101, menuName: "Water Charges", menuPath: "/WaterInfo" },
      { id: 102, menuName: "Electricity Charges", menuPath: "/ElectricInfo" },
    ] },
    { id: 11, menuName: "Room Inspections", menuPath: "/CheckInfo", icon: "Tickets", children: [] },
    { id: 13, menuName: "My Profile", menuPath: "/selfInfo", icon: "Setting", children: [] },
  ],
};

export default {
  name: "Aside",
  components: {
    OverlayScrollbarsComponent,
  },
  data() {
    return {
      user: {},
      identity: "",
      menus: [],
      path: this.$route.path,
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
  watch: {
    $route(to) {
      this.path = to.path;
    },
  },
  created() {
    this.init();
  },
  methods: {
    init() {
      const cachedIdentity = this.readSessionJson("identity");
      const cachedUser = this.readSessionJson("user");

      if (cachedIdentity) {
        this.identity = cachedIdentity;
        this.loadMenus();
      }
      if (cachedUser) {
        this.user = cachedUser;
      }

      request.get("/main/loadIdentity").then((res) => {
        if (res.code !== "0") {
          if (!cachedIdentity) {
            this.handleExpired();
          }
          return;
        }
        window.sessionStorage.setItem("identity", JSON.stringify(res.data));
        this.identity = res.data;
        this.loadMenus();
      }).catch(() => {
        if (!cachedIdentity) {
          this.handleExpired();
        }
      });
      request.get("/main/loadUserInfo").then((result) => {
        if (result.code !== "0") {
          if (!cachedUser) {
            this.handleExpired();
          }
          return;
        }
        window.sessionStorage.setItem("user", JSON.stringify(result.data));
        this.user = result.data;
      }).catch(() => {
        if (!cachedUser) {
          this.handleExpired();
        }
      });
    },
    readSessionJson(key) {
      try {
        return JSON.parse(window.sessionStorage.getItem(key));
      } catch (error) {
        return null;
      }
    },
    loadMenus() {
      request.get("/permission/menus").then((res) => {
        if (res.code === "0" && Array.isArray(res.data) && res.data.length) {
          this.menus = res.data;
        } else {
          this.menus = fallbackMenus[this.identity] || fallbackMenus.stu;
        }
      }).catch(() => {
        this.menus = fallbackMenus[this.identity] || fallbackMenus.stu;
      });
    },
    handleExpired() {
      ElMessage({
        message: "Your session has expired. Please sign in again.",
        type: "error",
      });
      sessionStorage.clear();
      request.get("/main/signOut");
      this.$router.replace({ path: "/Login" });
    },
  },
};
</script>

<style scoped>
.aside-scroll {
  width: 100%;
  height: 100vh;
}

.side-menu {
  width: 100%;
  min-height: 100%;
  border-right: 0;
  background: transparent;
  padding: 10px 8px;
}

.logo-box {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 0 22px;
}

.logo-box img {
  width: 68px;
  filter: drop-shadow(0 10px 18px rgba(47, 116, 232, 0.16));
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 46px;
  line-height: 46px;
  margin: 4px 0;
  border-radius: 7px;
  color: #53657d;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  color: #2f74e8;
  background: #edf5ff;
}

.side-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: linear-gradient(135deg, #3a86ff, #19a88b);
  box-shadow: 0 12px 24px rgba(47, 116, 232, 0.20);
}

.side-menu :deep(.el-icon) {
  font-size: 17px;
}

.el-sub-menu .el-menu-item {
  height: 42px;
  line-height: 42px;
  padding: 0 38px;
  min-width: 0;
}

@media (max-width: 768px) {
  .aside-scroll {
    width: 100%;
    height: 58px;
  }

  .side-menu {
    width: max-content;
    min-width: 100%;
    min-height: auto;
    display: flex;
    overflow: visible;
    border-right: 0;
    padding: 7px 8px;
  }

  .logo-box {
    display: none;
  }

  .side-menu :deep(.el-menu-item),
  .side-menu :deep(.el-sub-menu__title) {
    margin: 0 4px;
    min-width: 108px;
    height: 42px;
    line-height: 42px;
    padding: 0 14px;
    white-space: nowrap;
  }
}
</style>
