<template>
  <div class="login-container">
    <div class="login-visual">
      <img alt="" src="../../public/login_Team.png"/>
    </div>
    <div class="login-panel">
      <div class="login-title">SmartCampus</div>
      <p class="login-subtitle">Sign in to campus services</p>
      <el-alert v-if="sessionMessage" :title="sessionMessage" type="warning" :closable="false" show-icon class="session-message" />
      <el-form ref="form" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="Username" autocomplete="username" clearable prefix-icon="avatar"></el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="Password" autocomplete="current-password" prefix-icon="lock" show-password></el-input>
        </el-form-item>
        <el-form-item :model="form" prop="identity">
          <el-radio v-model="form.identity" value="stu" style="color: grey">Student</el-radio>
          <el-radio v-model="form.identity" value="dormManager" style="color: grey">Staff</el-radio>
          <el-radio v-model="form.identity" value="admin" style="color: grey">Administrator</el-radio>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!disabled || signingIn" :loading="signingIn" class="login-button" type="primary" @click="login">Sign In</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script src="@/assets/js/Login.js"></script>
<style scoped>
@import '../assets/css/Login.css';
.login-container { position: fixed; inset: 0; transform: none; height: 100dvh; overflow-y: auto; }
.login-subtitle { margin: -10px 0 24px; color: #69788f; font-size: 14px; }
.session-message { margin-bottom: 22px; }
@media (max-width: 768px) {
  .login-container { min-height: 0; justify-content: flex-start; }
  .login-visual, .login-panel { flex-shrink: 0; }
}
</style>
