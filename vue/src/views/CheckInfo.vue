<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>宿舍管理</el-breadcrumb-item>
      <el-breadcrumb-item>卫生检查</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="请输入宿舍号或评分" prefix-icon="Search" style="width: 20%" />
        <el-date-picker v-model="date" type="datetimerange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="margin-left: 10px" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <el-button icon="refresh-left" style="margin-left: 10px" @click="reset" />
        <div v-if="role !== 'stu'" style="float: right">
          <el-tooltip content="添加" placement="top">
            <el-button icon="plus" style="width: 50px" type="primary" @click="add" />
          </el-tooltip>
        </div>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column label="宿舍号" prop="roomid" sortable />
        <el-table-column label="检查结果" prop="grade" />
        <el-table-column label="说明" prop="reason" show-overflow-tooltip />
        <el-table-column label="操作人" prop="operatorName" />
        <el-table-column label="日期" prop="date" sortable />
        <el-table-column v-if="role !== 'stu'" label="操作" width="132" align="center">
          <template #default="scope">
            <div class="table-actions">
              <el-tooltip content="查看详情" placement="top">
                <el-button class="action-button action-button--view" icon="more-filled" @click="showDetail(scope.row)" />
              </el-tooltip>
              <el-tooltip content="编辑记录" placement="top">
                <el-button class="action-button action-button--edit" icon="Edit" type="primary" @click="handleEdit(scope.row)" />
              </el-tooltip>
              <el-popconfirm title="确认删除？" @confirm="handleDelete(scope.row.id)">
                <template #reference>
                  <el-button class="action-button action-button--delete" icon="Delete" type="danger" />
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination v-model:currentPage="currentPage" :page-size="pageSize" :page-sizes="[10, 20]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
      <el-dialog v-model="dialogVisible" title="卫生检查" width="30%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="120px">
          <el-form-item label="宿舍号" prop="roomid">
            <el-input v-model.number="form.roomid" :disabled="disabled" style="width: 80%" />
          </el-form-item>
          <el-form-item label="检查结果" prop="grade">
            <el-select v-model="form.grade" :disabled="disabled" placeholder="请选择" style="width: 80%">
              <el-option label="优秀" value="优秀" />
              <el-option label="良好" value="良好" />
              <el-option label="合格" value="合格" />
              <el-option label="不合格" value="不合格" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明" prop="reason">
            <el-input v-model="form.reason" :disabled="disabled" type="textarea" :rows="3" style="width: 80%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="save">确 定</el-button>
        </template>
      </el-dialog>
      <el-dialog v-model="detailDialog" title="检查详情" width="30%">
        <el-descriptions border :column="1">
          <el-descriptions-item label="宿舍号">{{ detail.roomid }}</el-descriptions-item>
          <el-descriptions-item label="检查结果">{{ detail.grade }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ detail.date }}</el-descriptions-item>
        </el-descriptions>
        <template #footer><el-button type="primary" @click="closeDetailDialog">确 定</el-button></template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/CheckInfo.js"></script>
<style scoped>
.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 2px 0;
  white-space: nowrap;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.action-button {
  width: 32px;
  height: 32px;
  padding: 0 !important;
  border-radius: 8px !important;
  box-shadow: none !important;
}

.action-button--view {
  color: #53657d;
  border-color: #dce6f3;
  background: #fff;
}

.action-button--view:hover {
  color: #2f74e8;
  border-color: #b9cff5;
  background: #f4f8ff;
}

.action-button--edit {
  border-color: #2f74e8 !important;
  background: #2f74e8 !important;
}

.action-button--delete {
  border-color: #e85d5d !important;
  background: #e85d5d !important;
}
</style>
