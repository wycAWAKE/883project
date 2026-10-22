<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>学生服务</el-breadcrumb-item>
      <el-breadcrumb-item>报修申请</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-input v-model="search" clearable placeholder="请输入标题" prefix-icon="Search" style="width: 20%" />
        <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load" />
        <div style="float: right"><el-button icon="plus" type="primary" @click="add">申请报修</el-button></div>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column label="标题" prop="title" show-overflow-tooltip />
        <el-table-column label="楼宇号" prop="dormBuildId" />
        <el-table-column label="房间号" prop="dormRoomId" />
        <el-table-column label="状态" prop="state">
          <template #default="scope"><el-tag :type="scope.row.state === '完成' ? 'success' : 'warning'">{{ scope.row.state }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" prop="orderBuildTime" sortable />
        <el-table-column label="完成时间" prop="orderFinishTime" sortable />
        <el-table-column label="操作" width="90">
          <template #default="scope"><el-button icon="more-filled" @click="showDetail(scope.row)" /></template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination v-model:currentPage="currentPage" :page-size="pageSize" :page-sizes="[10, 20]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
      <el-dialog v-model="dialogVisible" title="报修申请" width="30%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="120px">
          <el-form-item label="标题" prop="title"><el-input v-model="form.title" style="width: 80%" /></el-form-item>
          <el-form-item label="报修人"><el-input v-model="form.repairer" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="楼宇号"><el-input v-model="form.dormBuildId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="房间号"><el-input v-model="form.dormRoomId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="4" style="width: 80%" /></el-form-item>
          <el-form-item label="申请时间" prop="orderBuildTime"><el-date-picker v-model="form.orderBuildTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 80%" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="cancel">取 消</el-button><el-button type="primary" @click="save">确 定</el-button></template>
      </el-dialog>
      <el-dialog v-model="detailDialog" title="报修详情" width="30%">
        <el-descriptions border :column="1">
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ detail.content }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.state }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.orderBuildTime }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ detail.orderFinishTime }}</el-descriptions-item>
        </el-descriptions>
        <template #footer><el-button type="primary" @click="closeDetails">确 定</el-button></template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/ApplyRepairInfo.js"></script>
