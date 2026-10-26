<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>Users</el-breadcrumb-item>
      <el-breadcrumb-item>Students</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div>
        <!--    功能区-->
        <div style="margin: 10px 0">
          <!--    搜索区-->
          <div style="margin: 10px 0">
            <el-input v-model="search" clearable placeholder="Enter a name" prefix-icon="Search"
                      style="width: 20%"/>
            <el-button icon="Search" style="margin-left: 5px" type="primary" @click="load"></el-button>
            <el-button icon="refresh-left" style="margin-left: 10px" type="default" @click="reset"></el-button>
            <div style="float: right">
              <el-tooltip content="Add" placement="top">
                <el-button icon="plus" style="width: 50px" type="primary" @click="add"></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <!-- 答辩链路-页面展示：Element Plus 的 el-table 负责展示学生数据；数据来源是 tableData，下一步看 src/assets/js/StuInfo.js 的 created() 和 load()。 -->
        <el-table v-loading="loading" :data="tableData" border max-height="705" style="width: 100%">
          <el-table-column label="#" type="index"/>
          <el-table-column min-width="140" label="Student ID" prop="username" sortable/>
          <el-table-column min-width="140" label="Name" prop="name"/>
          <el-table-column min-width="140"
              :filter-method="filterTag"
              :filters="[
              { text: 'Male', value: '男' },
              { text: 'Female', value: '女' },
            ]"
              filter-placement="bottom-end"
              label="Gender"
              prop="gender" :formatter="$enCell"
          />
          <el-table-column min-width="140" label="Age" prop="age" sortable/>
          <el-table-column min-width="140" label="Phone" prop="phoneNum"/>
          <el-table-column min-width="140" :show-overflow-tooltip="true" label="Email" prop="email"/>
          <!--      操作栏-->
          <el-table-column label="Actions" width="130px">
            <template #default="scope">
              <el-button icon="Edit" type="primary" @click="handleEdit(scope.row)"></el-button>
              <el-popconfirm title="Delete this record?" @confirm="handleDelete(scope.row.username)">
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
        <div>
          <!--      弹窗-->
          <el-dialog v-model="dialogVisible" title="Actions" width="30%" @close="cancel">
            <el-form ref="form" :model="form" :rules="rules" label-width="auto">
              <el-form-item label="Student ID" prop="username">
                <el-input v-model="form.username" :disabled="judgeAddOrEdit" style="width: 80%"></el-input>
              </el-form-item>
              <el-form-item label="Password" prop="password">
                <el-input v-model="form.password" :disabled="disabled" :show-password="showpassword"
                          style="width: 80%"></el-input>
                <el-tooltip content="Change Password" placement="right">
                  <el-icon :style="editDisplay" size="large" style="margin-left: 5px; cursor: pointer"
                           @click="EditPass">
                    <edit/>
                  </el-icon>
                </el-tooltip>
              </el-form-item>
              <el-form-item :style="display" label="Confirm Password" prop="checkPass">
                <el-input v-model="form.checkPass" :show-password="showpassword" style="width: 80%"
                ></el-input>
              </el-form-item>
              <el-form-item label="Name" prop="name">
                <el-input v-model="form.name" style="width: 80%"></el-input>
              </el-form-item>
              <el-form-item label="Age" prop="age">
                <el-input v-model.number="form.age" style="width: 80%"
                ></el-input>
              </el-form-item>
              <el-form-item label="Gender" prop="gender">
                <el-radio v-model="form.gender" label="男">Male</el-radio>
                <el-radio v-model="form.gender" label="女">Female</el-radio>
              </el-form-item>
              <el-form-item label="Phone" prop="phoneNum">
                <el-input v-model.number="form.phoneNum" style="width: 80%"
                ></el-input>
              </el-form-item>
              <el-form-item label="Email Address" prop="email">
                <el-input v-model="form.email" style="width: 80%"></el-input>
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
<!-- 答辩链路-页面逻辑：本页面的查询、新增、修改、删除逻辑都写在 src/assets/js/StuInfo.js；下一步看 StuInfo.js 中的 request.get('/stu/find')。 -->
<script src="@/assets/js/StuInfo.js"></script>
