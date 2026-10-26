import { displayMessage } from "@/utils/english";
import request from "@/utils/request";

import { ElMessage } from 'element-plus';
export default {
    name: "Login",
    data() {
        return {
            identity: "",
            form: {
                username: "",
                password: "",
                identity: "",
            },
            rules: {
                username: [
                    {required: true, message: "Enter a username", trigger: "blur"},
                ],
                password: [{required: true, message: "Enter a password", trigger: "blur"}],
                identity: [{required: true, message: "Select a role", trigger: "blur"}],
            },
        };
    },
    computed: {
        disabled() {
            const {username, password, identity} = this.form;
            return Boolean(username && password && identity);
        },
    },
    methods: {
        login() {
            this.$refs.form.validate((valid) => {
                if (valid) {
                    this.identity = this.form.identity;
                    // 答辩链路-登录请求：根据身份拼出 /admin/login、/stu/login 或 /dormManager/login；下一步看对应后端 Controller 的 login(HttpSession session)。
                    request.post("/" + this.identity + "/login", this.form).then((res) => {
                        if (res.code === "0") {
                            ElMessage({
                                message: "Signed in successfully.",
                                type: "success",
                            });
                            // 登陆成功跳转主页
                            // 答辩链路-前端会话：浏览器 sessionStorage 保存用户信息和身份，刷新页面时路由守卫可判断是否登录；下一步看 src/router/index.js 的 beforeEach。
                            window.sessionStorage.setItem("user", JSON.stringify(res.data));
                            window.sessionStorage.setItem("identity", JSON.stringify(this.form.identity));
                            this.$router.replace({path: "/home"});
                        } else {
                            ElMessage({
                                message: displayMessage(res.msg),
                                type: "error",
                            });
                        }
                    });
                }
            });
        },
    },
};
