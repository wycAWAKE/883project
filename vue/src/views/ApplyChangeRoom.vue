<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>学生服务</el-breadcrumb-item>
      <el-breadcrumb-item>申请调宿</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-button icon="plus" type="primary" @click="add">申请调宿</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
        <el-table-column label="#" type="index" />
        <el-table-column label="学号" prop="username" />
        <el-table-column label="姓名" prop="name" />
        <el-table-column label="原房间" prop="currentRoomId" />
        <el-table-column label="原床位" prop="currentBedId" />
        <el-table-column label="目标房间" prop="towardsRoomId" />
        <el-table-column label="目标床位" prop="towardsBedId" />
        <el-table-column label="状态" prop="state">
          <template #default="scope"><el-tag :type="scope.row.state === '通过' ? 'success' : scope.row.state === '驳回' ? 'danger' : 'warning'">{{ scope.row.state || '未处理' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="申请时间" prop="applyTime" sortable />
        <el-table-column label="完成时间" prop="finishTime" sortable />
        <el-table-column label="操作" width="90">
          <template #default="scope"><el-button icon="more-filled" @click="showDetail(scope.row)" /></template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination v-model:currentPage="currentPage" :page-size="pageSize" :page-sizes="[10, 20]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
      <el-dialog v-model="dialogVisible" title="申请调宿" width="32%" @close="cancel">
        <el-form ref="form" :model="form" :rules="rules" label-width="120px">
          <el-form-item label="学号" prop="username"><el-input v-model="form.username" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="姓名" prop="name"><el-input v-model="form.name" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="当前房间" prop="currentRoomId"><el-input v-model.number="form.currentRoomId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="当前床位" prop="currentBedId"><el-input v-model.number="form.currentBedId" disabled style="width: 80%" /></el-form-item>
          <el-form-item label="目标房间" prop="towardsRoomId"><el-input v-model.number="form.towardsRoomId" style="width: 80%" /></el-form-item>
          <el-form-item label="目标床位" prop="towardsBedId"><el-input v-model.number="form.towardsBedId" style="width: 80%" /></el-form-item>
          <el-form-item v-if="!judgeOption" label="处理状态" prop="state">
            <el-radio v-model="form.state" label="未处理">未处理</el-radio>
            <el-radio v-model="form.state" label="通过">通过</el-radio>
            <el-radio v-model="form.state" label="驳回">驳回</el-radio>
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="cancel">取 消</el-button><el-button type="primary" @click="save">确 定</el-button></template>
      </el-dialog>
      <el-dialog v-model="detailDialog" title="调宿详情" width="32%" @close="cancel">
        <el-descriptions border :column="1">
          <el-descriptions-item label="学号">{{ form.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="原房间/床位">{{ form.currentRoomId }} / {{ form.currentBedId }}</el-descriptions-item>
          <el-descriptions-item label="目标房间/床位">{{ form.towardsRoomId }} / {{ form.towardsBedId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ form.state || '未处理' }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ form.applyTime }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ form.finishTime }}</el-descriptions-item>
        </el-descriptions>
        <template #footer><el-button type="primary" @click="cancel">确 定</el-button></template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/ApplyChangeRoom.js"></script>
