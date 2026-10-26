<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Student Services</el-breadcrumb-item>
      <el-breadcrumb-item>Request Room Transfer</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-button icon="plus" type="primary" @click="add">Request Room Transfer</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column min-width="140" label="Student ID" prop="username" />
        <el-table-column min-width="140" label="Name" prop="name" />
        <el-table-column min-width="140" label="Current Room" prop="currentRoomId" />
        <el-table-column min-width="140" label="Current Bed" prop="currentBedId" />
        <el-table-column min-width="140" label="Target Room" prop="towardsRoomId" />
        <el-table-column min-width="140" label="Target Bed" prop="towardsBedId" />
        <el-table-column min-width="140" label="Status" prop="state">
          <template #default="scope"><el-tag :type="scope.row.state === '通过' ? 'success' : scope.row.state === '驳回' ? 'danger' : 'warning'">{{ $en(scope.row.state || '未处理') }}</el-tag></template>
        </el-table-column>
        <el-table-column min-width="140" label="Requested At" prop="applyTime" sortable />
        <el-table-column min-width="140" label="Completed At" prop="finishTime" sortable />
        <el-table-column label="Actions" width="90">
          <template #default="scope"><el-button icon="more-filled" @click="showDetail(scope.row)" /></template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination v-model:currentPage="currentPage" :page-size="pageSize" :page-sizes="[10, 20]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
      <el-dialog v-model="dialogVisible" title="Request Room Transfer" width="32%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="auto">
          <el-form-item label="Student ID" prop="username"><el-input v-model="form.username" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Name" prop="name"><el-input v-model="form.name" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Current Room" prop="currentRoomId"><el-input v-model.number="form.currentRoomId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Current Bed" prop="currentBedId"><el-input v-model.number="form.currentBedId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Target Room" prop="towardsRoomId"><el-input v-model.number="form.towardsRoomId" style="width: 80%" /></el-form-item>
          <el-form-item label="Target Bed" prop="towardsBedId"><el-input v-model.number="form.towardsBedId" style="width: 80%" /></el-form-item>
          <el-form-item v-if="!judgeOption" label="Decision" prop="state">
            <el-radio v-model="form.state" label="未处理">Pending</el-radio>
            <el-radio v-model="form.state" label="通过">Approved</el-radio>
            <el-radio v-model="form.state" label="驳回">Rejected</el-radio>
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="cancel">Cancel</el-button><el-button type="primary" @click="save">Confirm</el-button></template>
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
<script src="@/assets/js/ApplyChangeRoom.js"></script>
