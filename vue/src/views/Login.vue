<template>
  <div class="login-container">
    <div class="login-visual">
      <img alt="" src="../../public/login_Team.png"/>
    </div>
    <div class="login-panel">
      <div class="login-title">Sign In</div>
      <el-form ref="form" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="Username" autocomplete="username" clearable prefix-icon="avatar"></el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="Password" autocomplete="current-password" prefix-icon="lock" show-password></el-input>
        </el-form-item>
        <el-form-item :model="form" prop="identity">
          <el-radio v-model="form.identity" label="stu" style="color: grey">Student</el-radio>
          <el-radio v-model="form.identity" label="dormManager" style="color: grey">Dormitory Staff</el-radio>
          <el-radio v-model="form.identity" label="admin" style="color: grey">Administrator</el-radio>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!disabled" class="login-button" type="primary" @click="login">Sign In</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script src="@/assets/js/Login.js"></script>
<style scoped>@import '../assets/css/Login.css';</style>
