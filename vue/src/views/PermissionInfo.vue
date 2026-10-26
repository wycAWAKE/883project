<template>
  <div class="permission-page">
    <el-card class="permission-card">
      <template #header>
        <div class="card-header">
          <span>Role Permissions</span>
          <el-button type="primary" size="small" @click="load">Refresh</el-button>
        </div>
      </template>

      <el-table :data="roleRows" border stripe>
        <el-table-column label="Role Code" prop="roleCode" width="140" />
        <el-table-column label="Role Name" prop="roleName" width="140" :formatter="$enCell" />
        <el-table-column min-width="140" label="Description" prop="description" :formatter="$enCell" />
      </el-table>
    </el-card>

    <el-card class="permission-card">
      <template #header>Menu Permissions</template>
      <el-table :data="permissionRows" border stripe>
        <el-table-column label="Role" prop="roleName" width="130" :formatter="$enCell" />
        <el-table-column label="Menu" prop="menuName" width="150" :formatter="$enCell" />
        <el-table-column label="Route" prop="menuPath" width="170" />
        <el-table-column min-width="140" label="Permission Code" prop="permissionCode" />
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { displayMessage } from "@/utils/english";

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
          ElMessage.error(displayMessage(res.msg || "Unable to load roles."));
        }
      });
      request.get("/permission/roleMenus").then((res) => {
        if (res.code === "0") {
          this.permissionRows = res.data;
        } else {
          ElMessage.error(displayMessage(res.msg || "Unable to load menu permissions."));
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
