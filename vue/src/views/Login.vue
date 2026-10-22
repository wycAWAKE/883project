<template>
  <div class="login-container">
    <div class="login-visual">
      <img alt="" src="../../public/login_Team.png"/>
    </div>
    <div class="login-panel">
      <div class="login-title">登陆</div>
      <el-form ref="form" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" clearable prefix-icon="avatar"></el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" prefix-icon="lock" show-password></el-input>
        </el-form-item>
        <el-form-item :model="form" prop="identity">
          <el-radio v-model="form.identity" label="stu" style="color: grey">学生</el-radio>
          <el-radio v-model="form.identity" label="dormManager" style="color: grey">宿舍管理员</el-radio>
          <el-radio v-model="form.identity" label="admin" style="color: grey">系统管理员</el-radio>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!disabled" class="login-button" type="primary" @click="login">登 录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script src="@/assets/js/Login.js"></script>
<style scoped>@import '../assets/css/Login.css';</style>
