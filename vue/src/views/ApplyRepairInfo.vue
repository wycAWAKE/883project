<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Student Services</el-breadcrumb-item>
      <el-breadcrumb-item>Request Maintenance</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="Enter a title" prefix-icon="Search" style="width: 20%" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <div style="float: right"><el-button icon="plus" type="primary" @click="add">Request Maintenance</el-button></div>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column min-width="140" label="Title" prop="title" show-overflow-tooltip />
        <el-table-column min-width="140" label="Building No." prop="dormBuildId" />
        <el-table-column min-width="140" label="Room No." prop="dormRoomId" />
        <el-table-column min-width="140" label="Status" prop="state">
          <template #default="scope"><el-tag :type="scope.row.state === '完成' ? 'success' : 'warning'">{{ $en(scope.row.state) }}</el-tag></template>
        </el-table-column>
        <el-table-column min-width="140" label="Created At" prop="orderBuildTime" sortable />
        <el-table-column min-width="140" label="Completed At" prop="orderFinishTime" sortable />
        <el-table-column label="Actions" width="90">
          <template #default="scope"><el-button icon="more-filled" @click="showDetail(scope.row)" /></template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination v-model:currentPage="currentPage" :page-size="pageSize" :page-sizes="[10, 20]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
      <el-dialog v-model="dialogVisible" title="Request Maintenance" width="30%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="auto">
          <el-form-item label="Title" prop="title"><el-input v-model="form.title" style="width: 80%" /></el-form-item>
          <el-form-item label="Requested By"><el-input v-model="form.repairer" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Building No."><el-input v-model="form.dormBuildId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Room No."><el-input v-model="form.dormRoomId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="Details" prop="content"><el-input v-model="form.content" type="textarea" :rows="4" style="width: 80%" /></el-form-item>
          <el-form-item label="Requested At" prop="orderBuildTime"><el-date-picker v-model="form.orderBuildTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 80%" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="cancel">Cancel</el-button><el-button type="primary" @click="save">Confirm</el-button></template>
      </el-dialog>
      <el-dialog v-model="detailDialog" title="Maintenance Details" width="30%">
        <el-descriptions border :column="1">
          <el-descriptions-item label="Title">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="Details">{{ detail.content }}</el-descriptions-item>
          <el-descriptions-item label="Status">{{ $en(detail.state) }}</el-descriptions-item>
          <el-descriptions-item label="Created At">{{ detail.orderBuildTime }}</el-descriptions-item>
          <el-descriptions-item label="Completed At">{{ detail.orderFinishTime }}</el-descriptions-item>
        </el-descriptions>
        <template #footer><el-button type="primary" @click="closeDetails">Confirm</el-button></template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/ApplyRepairInfo.js"></script>
