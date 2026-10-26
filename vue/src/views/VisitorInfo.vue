<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Visitors</el-breadcrumb-item>
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
                <el-button icon="plus" style="width: 50px" type="primary" @click="add"></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <!--    表格-->
        <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
          <el-table-column label="#" type="index"/>
          <el-table-column label="Name" prop="visitorName" width="150px"/>
          <el-table-column
              :filter-method="filterTag"
              :filters="[
              { text: 'Male', value: '男' },
              { text: 'Female', value: '女' },
            ]"
              filter-placement="bottom-end"
              label="Gender"
              prop="gender"
              width="130px" :formatter="$enCell"
          />
          <el-table-column label="Phone" prop="phoneNum" width="200px"/>
          <el-table-column label="City" prop="originCity" sortable width="140px"/>
          <el-table-column label="Visit Time" prop="visitTime" sortable width="220px"/>
          <el-table-column min-width="140" label="Notes" prop="content" sortable/>
          <!--      操作栏-->
          <el-table-column label="Actions" width="130px">
            <template #default="scope">
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
              <el-form-item label="Name" prop="visitorName">
                <el-input v-model="form.visitorName" clearable style="width: 50%"></el-input>
              </el-form-item>
              <el-form-item label="Gender" prop="gender">
                <el-radio v-model="form.gender" label="男">Male</el-radio>
                <el-radio v-model="form.gender" label="女">Female</el-radio>
              </el-form-item>
              <el-form-item label="Phone" prop="phoneNum">
                <el-input v-model="form.phoneNum" clearable style="width: 50%"></el-input>
              </el-form-item>
              <el-form-item label="City" prop="originCity">
                <el-input v-model="form.originCity" clearable style="width: 50%"></el-input>
              </el-form-item>
              <el-form-item label="Visit Time" prop="visitTime">
                <el-date-picker
                    v-model="form.visitTime"
                    clearable
                    placeholder="Select a date and time"
                    style="width: 48%"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                ></el-date-picker>
              </el-form-item>
              <el-form-item label="Visit Details" prop="content">
                <el-input
                    v-model="form.content"
                    :autosize="{ minRows: 3, maxRows: 10 }"
                    clearable
                    style="width: 80%"
                    type="textarea"
                ></el-input>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="cancel">Cancel</el-button>
                <el-button type="primary" @click="save">Confirm</el-button>
              </span>
            </template>
          </el-dialog>
        </div>
      </div>
    </el-card>
  </div>
</template>
<script src="@/assets/js/VisitorInfo.js"></script>