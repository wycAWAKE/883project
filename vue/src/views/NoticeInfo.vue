<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Information</el-breadcrumb-item>
      <el-breadcrumb-item>Announcements</el-breadcrumb-item>
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
          <el-table-column min-width="140" :show-overflow-tooltip="true" label="Title" prop="title"/>
          <el-table-column label="Author" prop="author" width="150px"/>
          <el-table-column label="Published At" prop="releaseTime" sortable width="400px"/>
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
          <el-dialog v-model="dialogVisible" title="Actions" width="50%" @close="cancel">
            <el-form ref="form" :model="form" :rules="rules" label-width="auto">
              <el-form-item label="Title" prop="title" style="margin-bottom: 27px">
                <el-input v-model="form.title" clearable style="width: 50%"></el-input>
              </el-form-item>
              <el-form-item label="Details" prop="content">
                <div id="div1" style="width: 100%; margin: 4px 0"></div>
              </el-form-item>
              <el-form-item label="Published At" prop="releaseTime" style="margin-top: 27px">
                <el-date-picker
                    v-model="form.releaseTime"
                    clearable
                    placeholder="Select a date and time"
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
          <!--    公告详情-->
          <el-dialog v-model="detailDialog" title="Details" width="50%">
            <el-card>
              <div v-html="detail.content"></div>
            </el-card>
            <template #footer>
              <span class="dialog-footer">
                <el-button type="primary" @click="closeDetailDialog">Confirm</el-button>
              </span>
            </template>
          </el-dialog>
        </div>
      </div>
    </el-card>
  </div>
</template>
<script src="@/assets/js/NoticeInfo.js"></script>
