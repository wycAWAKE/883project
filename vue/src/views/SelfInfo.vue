<template>
  <div>
    <el-breadcrumb separator-icon="ArrowRight" style="margin: 16px">
      <el-breadcrumb-item :to="{ path: '/home' }">Home</el-breadcrumb-item>
      <el-breadcrumb-item>My Profile</el-breadcrumb-item>
    </el-breadcrumb>
    <el-card style="margin: 15px; min-height: calc(100vh - 111px)">
      <div style="display: flex">
        <div style="width: 600px; margin-left: 30px; position: relative">
          <div>
            <el-upload :on-success="uploadSuccess" :show-file-list="false"
                       action="http://localhost:9090/files/upload/"
                       class="upload-demo"
            >
              <div class="AvatarDiv">
                <el-avatar icon="UserFilled" style="width: 80px; height: 80px"></el-avatar>
                <img :src="'data:image;base64,' + image" :style="imgDisplay"
                     style="width: 80px; height: 80px; border-radius: 40px"/>
                <div class="editImg"> Change Avatar <el-icon color="color">
                    <edit></edit>
                  </el-icon>
                </div>
              </div>
            </el-upload>
          </div>
          <el-descriptions :column="1" :size="large" border style="min-width: 500px" title="">
            <el-descriptions-item>
              <template #label>
                <div>
                  <el-icon>
                    <user/>
                  </el-icon> Username </div>
              </template>
              {{ username }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <div>
                  <el-icon>
                    <location/>
                  </el-icon> Name </div>
              </template>
              {{ name }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <div>
                  <el-icon>
                    <tickets/>
                  </el-icon> Gender </div>
              </template>
              {{ $en(gender) }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <div>
                  <el-icon>
                    <office-building/>
                  </el-icon> Age </div>
              </template>
              {{ age }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <div>
                  <el-icon>
                    <iphone/>
                  </el-icon> Phone </div>
              </template>
              {{ phoneNum }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <div>
                  <el-icon>
                    <office-building/>
                  </el-icon> Email </div>
              </template>
              {{ email }}
            </el-descriptions-item>
          </el-descriptions>
          <el-tooltip content="Edit Profile" placement="bottom">
            <el-button icon="Edit" size="large" style="margin-top: 30px; width: 80px" type="primary" @click="Edit">
            </el-button>
          </el-tooltip>
        </div>
        <div style="margin-left: 130px">
          <img alt="" src="../../public/self_Space.png"/>
        </div>
      </div>
      <div>
        <!--      弹窗-->
        <el-dialog v-model="dialogVisible" title="Actions" width="30%" @close="cancel">
          <el-form ref="form" :model="form" :rules="rules" label-width="auto">
            <el-form-item label="Username" prop="username">
              <el-input v-model="form.username" disabled style="width: 80%"></el-input>
            </el-form-item>
            <el-form-item label="Password" prop="password">
              <el-input v-model="form.password" :disabled="disabled" :show-password="showpassword" show-password
                        style="width: 80%"></el-input>
              <el-tooltip content="Change Password" placement="right">
                <el-icon size="large" style="margin-left: 5px; cursor: pointer" @click="EditPass">
                  <edit/>
                </el-icon>
              </el-tooltip>
            </el-form-item>
            <el-form-item :style="display" label="Confirm Password" prop="checkPass">
              <el-input v-model="form.checkPass" show-password style="width: 80%"></el-input>
            </el-form-item>
            <el-form-item label="Name" prop="name">
              <el-input v-model="form.name" style="width: 80%"></el-input>
            </el-form-item>
            <el-form-item label="Gender" prop="gender">
              <el-radio v-model="form.gender" label="男">Male</el-radio>
              <el-radio v-model="form.gender" label="女">Female</el-radio>
            </el-form-item>
            <el-form-item label="Age" prop="age">
              <el-input v-model.number="form.age" style="width: 80%"></el-input>
            </el-form-item>
            <el-form-item label="Phone" prop="phoneNum">
              <el-input v-model.number="form.phoneNum" style="width: 80%"></el-input>
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
    </el-card>
  </div>
</template>
<script src="@/assets/js/SelfInfo.js"></script>
<style scoped>@import '../assets/css/SelfInfo.css';</style>