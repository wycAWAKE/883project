<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Utilities</el-breadcrumb-item>
      <el-breadcrumb-item>Water Charges</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div>
        <!--    功能区-->
        <div style="margin: 10px 0">
          <!--    搜索区-->
          <div style="margin: 10px 0">
            <el-date-picker
              v-model="date"
              type="datetimerange"
              range-separator="to"
              start-placeholder="Start date"
              end-placeholder="End date"
            >
            </el-date-picker>
            <el-button
              icon="Search"
              style="margin-left: 5px"
              type="primary"
              @click="load"
            ></el-button>
            <el-button
              icon="refresh-left"
              style="margin-left: 10px"
              type="default"
              @click="reset"
            ></el-button>
            <div style="float: right">
              <el-tooltip content="Add" placement="top">
                <el-button
                  icon="plus"
                  style="width: 50px"
                  type="primary"
                  @click="add"
                ></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <!--    表格-->
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          max-height="705"
          style="width: 100%"
        >
          <el-table-column label="#" type="index" />
          <el-table-column min-width="140" label="Transaction Type" prop="type" :formatter="$enCell" />
          <el-table-column min-width="140" label="Amount" prop="money" />
          <el-table-column min-width="140" label="Operator" prop="operatorName" sortable />
          <el-table-column min-width="140" label="Date" prop="date" sortable />
          <!--      操作栏-->
          <el-table-column min-width="140" label="Actions">
            <template #default="scope">
              <el-button
                icon="more-filled"
                type="default"
                @click="handleEdit(scope.row)"
              ></el-button>
            </template>
          </el-table-column>
        </el-table>
        <!--分页-->
        <div style="margin: 10px 0">
          <el-pagination
            v-model:currentPage="currentPage"
            :page-size="pageSize"
            :page-sizes="[10, 20]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          >
          </el-pagination>
        </div>
        <!--      弹窗-->
        <div>
          <el-dialog
            v-model="dialogVisible"
            :title="title"
            width="30%"
            @close="cancel"
          >
            <el-form ref="form" :model="form" label-width="auto">
              <el-form-item label="Type" prop="type">
                <el-input
                  :model-value="$en(form.type)"
                  disabled
                  clearable
                  style="width: 80%"
                ></el-input>
              </el-form-item>

              <el-form-item
                label="Room No."
                v-if="role != 'stu' || disabled"
                prop="roomid"
              >
                <el-input
                  v-model="form.roomid"
                  :disabled="disabled"
                  clearable
                  style="width: 80%"
                ></el-input>
              </el-form-item>
              <el-form-item label="Amount" prop="money">
                <el-input-number
                  :disabled="disabled"
                  v-model="form.money"
                  style="width: 80%"
                  :precision="2"
                  :step="0.1"
                  :min="1"
                  :max="1000"
                ></el-input-number>
              </el-form-item>

              <el-form-item label="Operator" v-if="disabled" prop="operatorName">
                <el-input
                  v-model="form.operatorName"
                  disabled
                  clearable
                  style="width: 80%"
                ></el-input>
              </el-form-item>
              <el-form-item v-if="disabled" label="Recorded At">
                <el-date-picker
                  disabled
                  v-model="form.date"
                  type="date"
                  placeholder="Select a date"
                  style="width: 80%"
                >
                </el-date-picker>
              </el-form-item>
            </el-form>
            <span v-if="!disabled" style="color: red"
              >Record payments here after completing payment in person.</span
            >
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="cancel">Cancel</el-button>
                <el-button v-if="!disabled" type="primary" @click="save"
                  >Confirm</el-button
                >
              </span>
            </template>
          </el-dialog>
        </div>
      </div>
    </el-card>
  </div>
</template>
<script src="@/assets/js/WaterInfo.js"></script>