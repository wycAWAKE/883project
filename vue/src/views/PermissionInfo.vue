<template>
  <div class="permission-page">
    <el-card class="permission-card">
      <template #header>
        <div class="card-header">
          <span>角色权限说明</span>
          <el-button type="primary" size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table :data="roleRows" border stripe>
        <el-table-column label="角色编码" prop="roleCode" width="140" />
        <el-table-column label="角色名称" prop="roleName" width="140" />
        <el-table-column label="角色说明" prop="description" />
      </el-table>
    </el-card>

    <el-card class="permission-card">
      <template #header>菜单权限矩阵</template>
      <el-table :data="permissionRows" border stripe>
        <el-table-column label="角色" prop="roleName" width="130" />
        <el-table-column label="菜单" prop="menuName" width="150" />
        <el-table-column label="路径" prop="menuPath" width="170" />
        <el-table-column label="权限标识" prop="permissionCode" />
      </el-table>
    </el-card>
  </div>
</template>

<script>
import request from "@/utils/request";
import { ElMessage } from "element-plus";

export default {
  name: "PermissionInfo",
  data() {
    return {
      roleRows: [],
      permissionRows: [],
    };
  },
  created() {
    this.load();
  },
  methods: {
    load() {
      request.get("/permission/roles").then((res) => {
        if (res.code === "0") {
          this.roleRows = res.data;
        } else {
          ElMessage.error(res.msg || "角色信息加载失败");
        }
      });
      request.get("/permission/roleMenus").then((res) => {
        if (res.code === "0") {
          this.permissionRows = res.data;
        } else {
          ElMessage.error(res.msg || "菜单权限加载失败");
        }
      });
    },
  },
};
</script>

<style scoped>
.permission-page {
  padding: 15px;
  min-height: calc(100vh - 50px);
  background: #f5f7fb;
}

.permission-card {
  margin-bottom: 15px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 768px) {
  .permission-page {
    padding: 10px;
  }

  .permission-card {
    overflow-x: auto;
  }
}
</style>
