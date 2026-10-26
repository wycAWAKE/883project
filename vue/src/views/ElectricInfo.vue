<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Utilities</el-breadcrumb-item>
      <el-breadcrumb-item>Electricity Charges</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="margin: 10px 0">
        <el-date-picker v-model="date" type="datetimerange" range-separator="to" start-placeholder="Start date" end-placeholder="End date" />
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
        <el-table-column min-width="140" label="Room No." prop="roomid" sortable />
        <el-table-column min-width="140" label="Transaction Type" prop="type" :formatter="$enCell" />
        <el-table-column min-width="140" label="Amount" prop="money" />
        <el-table-column min-width="140" label="Operator" prop="operatorName" sortable />
        <el-table-column min-width="140" label="Date" prop="date" sortable />
        <el-table-column label="Actions" width="90">
          <template #default="scope">
            <el-button icon="more-filled" @click="handleEdit(scope.row)" />
          </template>
        </el-table-column>
      </el-table>
      <div style="margin: 10px 0">
        <el-pagination v-model:currentPage="currentPage" :page-size="pageSize" :page-sizes="[10, 20]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
      <el-dialog v-model="dialogVisible" :title="title" width="30%" @close="cancel">
        <el-form ref="form" :model="form" label-width="auto">
          <el-form-item label="Type" prop="type">
            <el-input :model-value="$en(form.type)" disabled style="width: 80%" />
          </el-form-item>
          <el-form-item v-if="role !== 'stu' || disabled" label="Room No." prop="roomid">
            <el-input v-model.number="form.roomid" :disabled="disabled" style="width: 80%" />
          </el-form-item>
          <el-form-item label="Amount" prop="money">
            <el-input-number v-model="form.money" :disabled="disabled" :precision="2" :step="0.1" :min="1" :max="1000" style="width: 80%" />
          </el-form-item>
          <el-form-item v-if="disabled" label="Operator" prop="operatorName">
            <el-input v-model="form.operatorName" disabled style="width: 80%" />
          </el-form-item>
          <el-form-item v-if="disabled" label="Recorded At">
            <el-input v-model="form.date" disabled style="width: 80%" />
          </el-form-item>
        </el-form>
        <span v-if="!disabled" style="color: red">Record payments here after completing payment in person.</span>
        <template #footer>
          <el-button @click="cancel">Cancel</el-button>
          <el-button v-if="!disabled" type="primary" @click="save">Confirm</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
<script src="@/assets/js/ElectricInfo.js"></script>
