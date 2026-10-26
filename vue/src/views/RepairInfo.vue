<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Information</el-breadcrumb-item>
      <el-breadcrumb-item>Maintenance Requests</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div>
        <!--    功能区-->
        <div style="margin: 10px 0">
          <!--    搜索区-->
          <div style="margin: 10px 0">
            <el-input v-model="search" clearable placeholder="Enter a title" prefix-icon="Search" style="width: 20%"/>
            <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load"></el-button>
            <el-button icon="refresh-left" style="margin-left: 10px" type="default" @click="reset"></el-button>
            <div style="float: right">
              <el-tooltip content="Add" placement="top">
                <el-button icon="plus" style="width: 50px" type="primary" @click="add"
                ></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <!--    表格-->
        <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
          <el-table-column label="#" type="index"/>
          <el-table-column min-width="140" :show-overflow-tooltip="true" label="Title" prop="title"/>
          <el-table-column label="Room No." prop="dormBuildId" sortable width="150px"/>
          <el-table-column label="Room No." prop="dormRoomId" sortable width="150px"/>
          <el-table-column label="Applicant" prop="repairer" width="150px"/>
          <el-table-column min-width="140"
              :filter-method="filterTag"
              :filters="[
              { text: 'Completed', value: '完成' },
              { text: 'Incomplete', value: '未完成' },
            ]"
              filter-placement="bottom-end"
              label="Request Status"
              prop="state"
              sortable
          >
            <template #default="scope">
              <el-tag
                  :type="scope.row.state === '完成' ? 'success' : 'info'"
                  disable-transitions
              >{{ $en(scope.row.state) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column min-width="140" label="Created At" prop="orderBuildTime" sortable/>
          <el-table-column min-width="140" label="Completed At" prop="orderFinishTime" sortable/>
          <!--      操作栏-->
          <el-table-column label="Actions" width="190px">
            <template #default="scope">
              <el-button icon="more-filled" type="default" @click="showDetail(scope.row)"></el-button>
              <el-button icon="Edit" type="primary" @click="handleEdit(scope.row)"></el-button>
              <el-popconfirm title="Delete this record?" @confirm="handleDelete(scope.row.id)">
                <template #reference>
                  <el-button icon="Delete" type="danger"></el-button>
                </template>
              </el-popconfirm>
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
          <el-dialog v-model="dialogVisible" title="Actions" width="30%" @close="cancel">
            <el-form ref="form" :model="form" :rules="rules" label-width="auto">
              <el-form-item label="Title" prop="title" style="margin-bottom: 27px">
                <el-input v-model="form.title" clearable style="width: 80%"></el-input>
              </el-form-item>
              <el-form-item label="Building No." prop="dormBuildId" style="margin-bottom: 27px">
                <el-input v-model="form.dormBuildId" clearable style="width: 50%"></el-input>
              </el-form-item>
              <el-form-item label="Room No." prop="dormRoomId" style="margin-bottom: 27px">
                <el-input v-model="form.dormRoomId" clearable style="width: 50%"
                ></el-input>
              </el-form-item>
              <el-form-item label="Applicant" prop="repairer">
                <el-input v-model="form.repairer" clearable style="width: 50%"></el-input>
              </el-form-item>
              <el-form-item label="Details" prop="content">
                <el-input
                    v-model="form.content"
                    :autosize="{ minRows: 3, maxRows: 10 }"
                    clearable
                    style="width: 80%"
                    type="textarea"
                ></el-input>
              </el-form-item>
              <el-form-item :style="finishTime" label="Request Status" prop="state">
                <el-radio v-model="form.state" label="完成">Completed</el-radio>
                <el-radio v-model="form.state" label="未完成">Incomplete</el-radio>
              </el-form-item>
              <el-form-item label="Created At" prop="orderBuildTime" style="margin-top: 27px">
                <el-date-picker
                    v-model="form.orderBuildTime"
                    :disabled="buildTimeDisabled"
                    clearable
                    placeholder="Select a date and time"
                    style="width: 48%"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                ></el-date-picker>
              </el-form-item>
              <el-form-item :style="finishTime" label="Completed At" prop="orderFinishTime" style="margin-top: 27px">
                <el-date-picker
                    v-model="form.orderFinishTime"
                    clearable
                    placeholder="Select a date and time"
                    style="width: 48%"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                ></el-date-picker>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="cancel">Cancel</el-button>
                <el-button type="primary" @click="save">Confirm</el-button>
              </span>
            </template>
          </el-dialog>
          <!--   内容详情弹窗-->
          <el-dialog v-model="detailDialog" title="Details" width="30%">
            <el-card>
              <div v-html="detail.content"></div>
            </el-card>
            <template #footer>
              <span class="dialog-footer">
                <el-button type="primary" @click="closeDetails">Confirm</el-button>
              </span>
            </template>
          </el-dialog>
        </div>
      </div>
    </el-card>
  </div>
</template>
<script src="@/assets/js/RepairInfo.js"></script>