<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Dormitories</el-breadcrumb-item>
      <el-breadcrumb-item>Room Inspections</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="Search by room number or grade" prefix-icon="Search" style="width: 20%" />
        <el-date-picker v-model="date" type="datetimerange" range-separator="to" start-placeholder="Start date" end-placeholder="End date" style="margin-left: 10px" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <el-button icon="refresh-left" style="margin-left: 10px" @click="reset" />
        <div v-if="role !== 'stu'" style="float: right">
          <el-tooltip content="Add" placement="top">
            <el-button icon="plus" style="width: 50px" type="primary" @click="add" />
          </el-tooltip>
        </div>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column min-width="140" label="Room No." prop="roomid" sortable />
        <el-table-column min-width="140" label="Grade" prop="grade" :formatter="$enCell" />
        <el-table-column min-width="140" label="Notes" prop="reason" show-overflow-tooltip />
        <el-table-column min-width="140" label="Operator" prop="operatorName" />
        <el-table-column min-width="140" label="Date" prop="date" sortable />
        <el-table-column v-if="role !== 'stu'" label="Actions" width="132" align="center">
          <template #default="scope">
            <div class="table-actions">
              <el-tooltip content="View Details" placement="top">
                <el-button class="action-button action-button--view" icon="more-filled" @click="showDetail(scope.row)" />
              </el-tooltip>
              <el-tooltip content="Edit Record" placement="top">
                <el-button class="action-button action-button--edit" icon="Edit" type="primary" @click="handleEdit(scope.row)" />
              </el-tooltip>
              <el-popconfirm title="Delete this record?" @confirm="handleDelete(scope.row.id)">
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
      <el-dialog v-model="dialogVisible" title="Room Inspections" width="30%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="auto">
          <el-form-item label="Room No." prop="roomid">
            <el-input v-model.number="form.roomid" :disabled="disabled" style="width: 80%" />
          </el-form-item>
          <el-form-item label="Grade" prop="grade">
            <el-select v-model="form.grade" :disabled="disabled" placeholder="Select an option" style="width: 80%">
              <el-option label="Excellent" value="优秀" />
              <el-option label="Good" value="良好" />
              <el-option label="Needs Improvement" value="待改进" />
              <el-option label="Pass" value="合格" />
              <el-option label="Fail" value="不合格" />
            </el-select>
          </el-form-item>
          <el-form-item label="Notes" prop="reason">
            <el-input v-model="form.reason" :disabled="disabled" type="textarea" :rows="3" style="width: 80%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="cancel">Cancel</el-button>
          <el-button type="primary" @click="save">Confirm</el-button>
        </template>
      </el-dialog>
      <el-dialog v-model="detailDialog" title="Inspection Details" width="30%">
        <el-descriptions border :column="1">
          <el-descriptions-item label="Room No.">{{ detail.roomid }}</el-descriptions-item>
          <el-descriptions-item label="Grade">{{ $en(detail.grade) }}</el-descriptions-item>
          <el-descriptions-item label="Notes">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item label="Operator">{{ detail.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="Date">{{ detail.date }}</el-descriptions-item>
        </el-descriptions>
        <template #footer><el-button type="primary" @click="closeDetailDialog">Confirm</el-button></template>
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
