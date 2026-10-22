<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>宿舍管理</el-breadcrumb-item>
      <el-breadcrumb-item>楼宇管理</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="请输入楼宇名称" prefix-icon="Search" style="width: 20%" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <el-button icon="refresh-left" style="margin-left: 10px" @click="reset" />
        <div style="float: right">
          <el-tooltip content="添加" placement="top">
            <el-button icon="plus" style="width: 50px" type="primary" @click="add" />
          </el-tooltip>
        </div>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column label="楼宇编号" prop="dormBuildId" sortable />
        <el-table-column label="楼宇名称" prop="dormBuildName" />
        <el-table-column label="楼宇备注" prop="dormBuildDetail" show-overflow-tooltip />
        <el-table-column label="操作" width="130">
          <template #default="scope">
            <el-button icon="Edit" type="primary" @click="handleEdit(scope.row)" />
            <el-popconfirm title="确认删除？" @confirm="handleDelete(scope.row.id)">
              <template #reference>
                <el-button icon="Delete" type="danger" />
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination
          v-model:currentPage="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
      <el-dialog v-model="dialogVisible" title="楼宇信息" width="30%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="120px">
          <el-form-item label="楼宇编号" prop="dormBuildId">
            <el-input v-model.number="form.dormBuildId" :disabled="disabled" style="width: 80%" />
          </el-form-item>
          <el-form-item label="楼宇名称" prop="dormBuildName">
            <el-input v-model="form.dormBuildName" style="width: 80%" />
          </el-form-item>
          <el-form-item label="楼宇备注" prop="dormBuildDetail">
            <el-input v-model="form.dormBuildDetail" type="textarea" :rows="3" style="width: 80%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="save">确 定</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/BuildingInfo.js"></script>
