<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Dormitories</el-breadcrumb-item>
      <el-breadcrumb-item>Building Management</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="Enter a building name" prefix-icon="Search" style="width: 20%" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <el-button icon="refresh-left" style="margin-left: 10px" @click="reset" />
        <div style="float: right">
          <el-tooltip content="Add" placement="top">
            <el-button icon="plus" style="width: 50px" type="primary" @click="add" />
          </el-tooltip>
        </div>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column min-width="140" label="Building ID" prop="dormBuildId" sortable />
        <el-table-column min-width="140" label="Building Name" prop="dormBuildName" />
        <el-table-column min-width="140" label="Building Notes" prop="dormBuildDetail" show-overflow-tooltip />
        <el-table-column label="Actions" width="130">
          <template #default="scope">
            <el-button icon="Edit" type="primary" @click="handleEdit(scope.row)" />
            <el-popconfirm title="Delete this record?" @confirm="handleDelete(scope.row.id)">
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
      <el-dialog v-model="dialogVisible" title="Buildings" width="30%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="auto">
          <el-form-item label="Building ID" prop="dormBuildId">
            <el-input v-model.number="form.dormBuildId" :disabled="disabled" style="width: 80%" />
          </el-form-item>
          <el-form-item label="Building Name" prop="dormBuildName">
            <el-input v-model="form.dormBuildName" style="width: 80%" />
          </el-form-item>
          <el-form-item label="Building Notes" prop="dormBuildDetail">
            <el-input v-model="form.dormBuildDetail" type="textarea" :rows="3" style="width: 80%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="cancel">Cancel</el-button>
          <el-button type="primary" @click="save">Confirm</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/BuildingInfo.js"></script>
