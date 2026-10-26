<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Applications</el-breadcrumb-item>
      <el-breadcrumb-item>Review Room Transfer</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="Search by student ID or name" prefix-icon="Search" style="width: 20%" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <el-button icon="refresh-left" style="margin-left: 10px" @click="reset" />
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column min-width="140" label="Student ID" prop="username" sortable />
        <el-table-column min-width="140" label="Name" prop="name" />
        <el-table-column min-width="140" label="Current Room" prop="currentRoomId" />
        <el-table-column min-width="140" label="Current Bed" prop="currentBedId" />
        <el-table-column min-width="140" label="Target Room" prop="towardsRoomId" />
        <el-table-column min-width="140" label="Target Bed" prop="towardsBedId" />
        <el-table-column min-width="140" label="Status" prop="state">
          <template #default="scope">
            <el-tag :type="scope.row.state === '通过' ? 'success' : scope.row.state === '驳回' ? 'danger' : 'warning'">{{ $en(scope.row.state || '未处理') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column min-width="140" label="Requested At" prop="applyTime" sortable />
        <el-table-column min-width="140" label="Completed At" prop="finishTime" sortable />
        <el-table-column label="Actions" width="132" align="center">
          <template #default="scope">
            <div class="table-actions">
              <el-tooltip content="View Details" placement="top">
                <el-button class="action-button action-button--view" icon="more-filled" @click="showDetail(scope.row)" />
              </el-tooltip>
              <el-tooltip content="Review" placement="top">
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
      <el-dialog v-model="dialogVisible" title="Review Room Transfer" width="32%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="auto">
          <el-form-item label="Student ID" prop="username"><el-input v-model="form.username" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Name" prop="name"><el-input v-model="form.name" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Current Room" prop="currentRoomId"><el-input v-model.number="form.currentRoomId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Current Bed" prop="currentBedId"><el-input v-model.number="form.currentBedId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Target Room" prop="towardsRoomId"><el-input v-model.number="form.towardsRoomId" style="width: 80%" /></el-form-item>
          <el-form-item label="Target Bed" prop="towardsBedId"><el-input v-model.number="form.towardsBedId" style="width: 80%" /></el-form-item>
          <el-form-item label="Decision" prop="state">
            <el-radio v-model="form.state" label="通过">Approved</el-radio>
            <el-radio v-model="form.state" label="驳回">Rejected</el-radio>
          </el-form-item>
          <el-form-item label="Completed At" prop="finishTime">
            <el-date-picker v-model="form.finishTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="Select the completion time" style="width: 80%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="cancel">Cancel</el-button>
          <el-button type="primary" @click="save">Confirm</el-button>
        </template>
      </el-dialog>
      <el-dialog v-model="detailDialog" title="Room Transfer Details" width="32%" @close="cancel">
        <el-descriptions border :column="1">
          <el-descriptions-item label="Student ID">{{ form.username }}</el-descriptions-item>
          <el-descriptions-item label="Name">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="Current Room / Bed">{{ form.currentRoomId }} / {{ form.currentBedId }}</el-descriptions-item>
          <el-descriptions-item label="Target Room / Bed">{{ form.towardsRoomId }} / {{ form.towardsBedId }}</el-descriptions-item>
          <el-descriptions-item label="Status">{{ $en(form.state || '未处理') }}</el-descriptions-item>
          <el-descriptions-item label="Requested At">{{ form.applyTime }}</el-descriptions-item>
          <el-descriptions-item label="Completed At">{{ form.finishTime }}</el-descriptions-item>
        </el-descriptions>
        <template #footer><el-button type="primary" @click="cancel">Confirm</el-button></template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/AdjustRoomInfo.js"></script>
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
