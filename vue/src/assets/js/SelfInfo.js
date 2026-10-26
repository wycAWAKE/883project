import { displayMessage, namePattern } from "@/utils/english";
import request from "@/utils/request";

import { ElMessage } from 'element-plus';
export default {
    name: "selfInfo",
    data() {
        // 手机号验证
        const checkPhone = (rule, value, callback) => {
            const phoneReg = /^1[3|4|5|6|7|8][0-9]{9}$/;
            if (!value) {
                return callback(new Error("Enter a phone number"));
            }
            setTimeout(() => {
                if (!Number.isInteger(+value)) {
                    callback(new Error("Enter a number"));
                } else {
                    if (phoneReg.test(value)) {
                        callback();
                    } else {
                        callback(new Error("Enter a valid phone number"));
                    }
                }
            }, 100);
        };
        const checkPass = (rule, value, callback) => {
            if (!this.editJudge) {
                console.log("Validate");
                if (value == "") {
                    callback(new Error("Enter the password again"));
                } else if (value !== this.form.password) {
                    callback(new Error("Passwords do not match."));
                } else {
                    callback();
                }
            } else {
                console.log("Skip Validation");
                callback();
            }
        };
        return {
            showpassword: true,
            image: "",
            editJudge: true,
            disabled: true,
            dialogVisible: false,
            identity: "",
            username: "",
            name: "",
            gender: "",
            age: "",
            phoneNum: "",
            email: "",
            targetURL: "",
            avatar: "",
            form: {
                username: "",
                name: "",
                gender: "",
                age: "",
                phoneNum: "",
                email: "",
            },
            rules: {
                username: [
                    {required: true, message: "Enter a username", trigger: "blur"},
                    {
                        pattern: /^[a-zA-Z0-9]{4,9}$/,
                        message: "Use 4 to 9 letters or digits",
                        trigger: "blur",
                    },
                ],
                name: [
                    {required: true, message: "Enter a name", trigger: "blur"},
                    {
                        pattern: namePattern,
                        message: "Use 1 to 50 letters, spaces, hyphens, apostrophes or Chinese characters",
                        trigger: "blur",
                    },
                ],
                gender: [{required: true, message: "Select a gender", trigger: "change"}],
                age: [
                    {required: true, message: "Enter an age", trigger: "blur"},
                    {type: "number", message: "Age must be a number", trigger: "blur"},
                    {
                        pattern: /^(1|[1-9]\d?|100)$/,
                        message: "Range: 1–100",
                        trigger: "blur",
                    },
                ],
                phoneNum: [{required: true, validator: checkPhone, trigger: "blur"}],
                email: [
                    {type: "email", message: "Enter a valid email address", trigger: "blur"},
                ],
                password: [
                    {required: true, message: "Enter a password", trigger: "blur"},
                    {
                        min: 6,
                        max: 32,
                        message: "Use 6 to 32 characters",
                        trigger: "blur",
                    },
                ],
                checkPass: [{validator: checkPass, trigger: "blur"}],
            },
            display: {
                display: "none",
            },
            imgDisplay: {
                display: "none",
            },
        };
    },
    created() {
        this.load();
        this.find();
        this.init(this.avatar);
    },
    methods: {
        //获取个人信息页面信息
        load() {
            this.form = JSON.parse(sessionStorage.getItem("user"));
            this.identity = JSON.parse(sessionStorage.getItem("identity"));
            this.username = this.form.username;
            this.name = this.form.name;
            this.gender = this.form.gender;
            this.age = this.form.age;
            this.phoneNum = this.form.phoneNum;
            this.email = this.form.email;
            this.avatar = this.form.avatar;
        },
        //查询数据，更新session
        find() {
            this.form = JSON.parse(sessionStorage.getItem("user"));
            request.post("/" + this.identity + "/login", this.form).then((res) => {
                //更新sessionStorage
                window.sessionStorage.setItem("user", JSON.stringify(res.data));
                //更新页面数据
                this.load();
            });
        },
        Edit() {
            this.dialogVisible = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                this.form = JSON.parse(sessionStorage.getItem("user"));
            });
        },
        cancel() {
            this.$refs.form.resetFields();
            this.display = {display: "none"};
            this.showpassword = true;
            this.editJudge = true;
            this.disabled = true;
            this.dialogVisible = false;
        },
        async save() {
            this.$refs.form.validate(async (valid) => {
                if (valid) {
                    //修改
                    await request.put("/" + this.identity + "/update", this.form).then((res) => {
                        if (res.code === "0") {
                            ElMessage({
                                message: "Updated successfully.",
                                type: "success",
                            });
                            //更新sessionStorage
                            window.sessionStorage.setItem(
                                "user",
                                JSON.stringify(this.form)
                            );
                            this.find();
                            this.dialogVisible = false;
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
        EditPass() {
            if (this.editJudge) {
                this.display = {display: "flex"};
                this.showpassword = false;
                this.disabled = false;
                this.editJudge = false;
            } else {
                this.display = {display: "none"};
                this.showpassword = true;
                this.editJudge = true;
                this.disabled = true;
            }
        },
        //发送请求，获取头像
        async init(data) {
            if (data === null || data === "") {
                console.log("No avatar uploaded");
                this.imgDisplay = {display: "none"};
            } else {
                this.imgDisplay = {display: "block"};
                console.log("Avatar file:" + data);
                await request.get("/files/initAvatar/" + data).then((res) => {
                    if (res.code === "0") {
                        this.image = res.data.data;
                    } else {
                        ElMessage({
                            message: displayMessage(res.msg),
                            type: "error",
                        });
                    }
                });
            }
        },
        async uploadSuccess() {
            this.form = JSON.parse(sessionStorage.getItem("user"));
            await request.post("/files/uploadAvatar/" + this.identity, this.form).then((res) => {
                if (res.code === "0") {
                    ElMessage({
                        message: "Updated successfully.",
                        type: "success",
                    });
                    //获取头像文件名
                    this.avatar = res.data;
                    console.log("Uploaded:" + this.avatar);
                    this.find();
                    this.init(this.avatar);
                } else {
                    ElMessage({
                        message: displayMessage(res.msg),
                        type: "error",
                    });
                }
            });
        },
    },
};
