import { displayMessage, namePattern } from "@/utils/english";
// 答辩链路-Axios封装入口：这里导入统一封装的 request；下一步看 src/utils/request.js，它会给接口加 /api 并携带 Cookie。
import request from "@/utils/request";

import { ElMessage } from 'element-plus';

export default {
    name: "StuInfo",
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
                if (value == "") {
                    callback(new Error("Enter the password again"));
                } else if (value !== this.form.password) {
                    callback(new Error("Passwords do not match."));
                } else {
                    callback();
                }
            } else {
                callback();
            }
        };
        return {
            showpassword: true,
            judgeAddOrEdit: true,
            loading: true,
            editJudge: true,
            disabled: false,
            judge: false,
            dialogVisible: false,
            search: "",
            currentPage: 1,
            pageSize: 10,
            total: 0,
            tableData: [],
            form: {
                username: "",
                name: "",
                age: "",
                gender: "",
                phoneNum: "",
                email: "",
            },
            rules: {
                username: [
                    {required: true, message: "Enter a student ID", trigger: "blur"},
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
                age: [
                    {required: true, message: "Enter an age", trigger: "blur"},
                    {type: "number", message: "Age must be a number", trigger: "blur"},
                    {
                        pattern: /^(1|[1-9]\d?|100)$/,
                        message: "Range: 1–100",
                        trigger: "blur",
                    },
                ],
                gender: [{required: true, message: "Select a gender", trigger: "change"}],
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
            editDisplay: {
                display: "block",
            },
            display: {
                display: "none",
            },
        };
    },
    created() {
        // 答辩链路-页面加载：进入学生信息页面后自动调用 load()；下一步看下面 load() 里的 request.get('/stu/find')。
        this.load();
        this.loading = true;
        setTimeout(() => {
            //设置延迟执行
            this.loading = false;
        }, 1000);
    },
    methods: {
        async load() {
            // 答辩链路-查询学生：前端发起 GET /stu/find；下一步看 src/utils/request.js，实际请求会先变成 /api/stu/find。
            request.get("/stu/find", {
                params: {
                    pageNum: this.currentPage,
                    pageSize: this.pageSize,
                    search: this.search,
                },
            }).then((res) => {
                // 答辩链路-接收JSON：后端 Result.success(page) 返回的数据被 request.js 拦截后交给这里；下一步把 res.data.records 交给 StuInfo.vue 的 el-table 展示。
                console.log(res);
                this.tableData = res.data.records;
                this.total = res.data.total;
                this.loading = false;
            });
        },
        reset() {
            this.search = ''
            request.get("/stu/find", {
                params: {
                    pageNum: 1,
                    pageSize: this.pageSize,
                    search: this.search,
                },
            }).then((res) => {
                console.log(res);
                this.tableData = res.data.records;
                this.total = res.data.total;
                this.loading = false;
            });
        },
        filterTag(value, row) {
            return row.gender === value;
        },
        add() {
            this.dialogVisible = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                this.judgeAddOrEdit = false;
                this.editDisplay = {display: "none"};
                this.disabled = false;
                this.form = {};
                this.judge = false;
            });
        },
        save() {
            this.$refs.form.validate((valid) => {
                if (valid) {
                    if (this.judge === false) {
                        //新增
                        // 答辩链路-新增学生：页面新增保存时发 POST /stu/add；下一步看后端 StudentController.add()。
                        request.post("/stu/add", this.form).then((res) => {
                            console.log(res);
                            if (res.code === "0") {
                                ElMessage({
                                    message: "Added successfully.",
                                    type: "success",
                                });
                                this.search = "";
                                this.loading = true;
                                this.load();
                                this.dialogVisible = false;
                            } else {
                                ElMessage({
                                    message: displayMessage(res.msg),
                                    type: "error",
                                });
                            }
                        });
                    } else {
                        //修改
                        // 答辩链路-修改学生：页面编辑保存时发 PUT /stu/update；下一步看后端 StudentController.update()。
                        request.put("/stu/update", this.form).then((res) => {
                            console.log(res);
                            if (res.code === "0") {
                                ElMessage({
                                    message: "Updated successfully.",
                                    type: "success",
                                });
                                this.search = "";
                                this.load();
                                this.dialogVisible = false;
                            } else {
                                ElMessage({
                                    message: displayMessage(res.msg),
                                    type: "error",
                                });
                            }
                        });
                    }
                }
            });
        },
        cancel() {
            this.$refs.form.resetFields();
            this.display = {display: "none"};
            this.editJudge = true;
            this.disabled = true;
            this.showpassword = true;
            this.dialogVisible = false;
        },
        EditPass() {
            if (this.editJudge) {
                this.showpassword = false;
                this.display = {display: "flex"};
                this.disabled = false;
                this.editJudge = false;
            } else {
                this.showpassword = true;
                this.display = {display: "none"};
                this.editJudge = true;
                this.disabled = true;
            }
        },
        handleEdit(row) {
            //修改
            //判断操作类型
            this.judge = true;
            // 生拷贝
            this.dialogVisible = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                this.form = JSON.parse(JSON.stringify(row));
                this.judgeAddOrEdit = true;
                this.editDisplay = {display: "block"};
                this.disabled = true;
            });
        },
        async handleDelete(username) {
            //删除
            console.log(username);
            // 答辩链路-删除学生：点击删除时发 DELETE /stu/delete/{username}；下一步看后端 StudentController.delete()。
            request.delete("/stu/delete/" + username).then((res) => {
                if (res.code === "0") {
                    ElMessage({
                        message: "Deleted successfully.",
                        type: "success",
                    });
                    this.search = "";
                    this.load();
                } else {
                    ElMessage({
                        message: displayMessage(res.msg),
                        type: "error",
                    });
                }
            });
        },
        handleSizeChange(pageSize) {
            //改变每页个数
            this.pageSize = pageSize;
            this.load();
        },
        handleCurrentChange(pageNum) {
            //改变页码
            this.currentPage = pageNum;
            this.load();
        },
    },
};
