import { displayMessage } from "@/utils/english";
import request from "@/utils/request";
import { getLoginRedirect, saveSession } from "@/utils/session";

import { ElMessage } from 'element-plus';
export default {
    name: "Login",
    data() {
        return {
            signingIn: false,
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
        sessionMessage() {
            return {
                expired: "Your session has expired. Please sign in again.",
                unavailable: "Unable to verify your session. Check your connection and try signing in again.",
            }[this.$route.query.reason] || "";
        },
        disabled() {
            const {username, password, identity} = this.form;
            return Boolean(username && password && identity);
        },
    },
    methods: {
        login() {
            this.$refs.form.validate((valid) => {
                if (valid && !this.signingIn) {
                    this.signingIn = true;
                    this.identity = this.form.identity;
                    // 答辩链路-登录请求：根据身份拼出 /admin/login、/stu/login 或 /dormManager/login；下一步看对应后端 Controller 的 login(HttpSession session)。
                    request.post("/" + this.identity + "/login", this.form).then((res) => {
                        if (res.code === "0") {
                            ElMessage({
                                message: "Signed in successfully.",
                                type: "success",
                            });
                            // Cache a credential-free account; the route guard verifies the server Session.
                            saveSession(window.sessionStorage, res.data, this.form.identity);
                            this.form.password = "";
                            this.$router.replace(getLoginRedirect(this.$route.query.redirect));
                        } else {
                            ElMessage({
                                message: displayMessage(res.msg),
                                type: "error",
                            });
                        }
                    }).catch(() => {
                        ElMessage.error("Unable to sign in. Check your connection and try again.");
                    }).finally(() => { this.signingIn = false; });
                }
            });
        },
    },
};
