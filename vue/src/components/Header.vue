<template>
  <div class="header-bar">
    <div class="system-title">
      <span class="title-mark">DM</span>
      <span>Dormitory Management System</span>
    </div>
    <!-- <Clock style="font-size: 20px;position: absolute;left: 50%;overflow: hidden;"/> -->
    <div style="flex: 1"></div>
    <div class="right-info">
      <el-dropdown>
        <span class="el-dropdown-link">
          <span class="avatar-dot">
            <el-icon :size="18"><avatar /></el-icon>
          </span>Account<el-icon class="el-icon--right"><arrow-down /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="selfInfoManage"
              >My Profile</el-dropdown-item
            >
            <el-dropdown-item @click="SignOut">Sign Out</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script>
import request from '@/utils/request'
import Clock from '@/components/Clock.vue'

import { ElMessage } from 'element-plus'

export default {
  name: 'Header',
  components: {
    Clock,
  },
  data() {
    return {
      name: '',
    }
  },
  created() {},
  methods: {
    SignOut() {
      sessionStorage.clear()
      request.get('/main/signOut')
      ElMessage({
        message: "Signed out successfully.",
        type: 'success',
      })
      this.$router.replace({ path: '/Login' })
    },
    selfInfoManage() {
      this.$router.push('/selfInfo')
    },
  },
}
</script>

<style scoped>
.right-info {
  min-width: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 18px;
}

.right-info:hover {
  cursor: pointer;
}

.header-bar {
  display: flex;
  align-items: center;
  height: 58px;
  padding: 0 8px 0 0;
}

.system-title {
  width: auto;
  margin-left: 18px;
  font-size: 17px;
  font-weight: 800;
  color: #182233;
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-mark {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  letter-spacing: 0;
  background: linear-gradient(135deg, #2f74e8, #19a88b);
  box-shadow: 0 10px 20px rgba(47, 116, 232, 0.22);
}

.el-dropdown-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  border-radius: 999px;
  color: #273851;
  background: #f3f7fc;
  border: 1px solid #dfe7f3;
}

.avatar-dot {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #2f74e8;
  background: #e8f1ff;
}

@media (max-width: 768px) {
  .header-bar {
    height: 58px;
  }

  .system-title {
    width: auto;
    flex: 1;
    font-size: 16px;
    margin-left: 12px;
  }

  .right-info {
    min-width: 112px;
    margin-right: 8px;
  }

  .el-dropdown-link {
    padding: 0 9px;
    font-size: 13px;
  }
}
</style>
