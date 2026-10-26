import { displayMessage, namePattern } from "@/utils/english";
import request from "@/utils/request";

import { ElMessage } from 'element-plus';

export default {
    name: "VisitorInfo",
    components: {},
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
        return {
            loading: true,
            disabled: false,
            judge: false,
            dialogVisible: false,
            search: "",
            currentPage: 1,
            pageSize: 10,
            total: 0,
            tableData: [],
            detail: {},
            form: {},
            rules: {
                visitorName: [
                    {required: true, message: "Enter a name", trigger: "blur"},
                    {
                        pattern: namePattern,
                        message: "Use 1 to 50 letters, spaces, hyphens, apostrophes or Chinese characters",
                        trigger: "blur",
                    },
                ],
                gender: [{required: true, message: "Select a gender", trigger: "change"}],
                phoneNum: [{required: true, validator: checkPhone, trigger: "blur"}],
                visitTime: [
                    {required: true, message: "Select a date and time", trigger: "change"},
                ],
                content: [
                    {required: true, message: "Enter visit details", trigger: "change"},
                ],
            },
        };
    },
    created() {
        this.load();
        this.loading = true;
        setTimeout(() => {
            //设置延迟执行
            this.loading = false;
        }, 1000);
    },
    methods: {
        async load() {
            request.get("/visitor/find", {
                params: {
                    pageNum: this.currentPage,
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
        reset() {
            this.search = ''
            request.get("/visitor/find", {
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
        showDetail(row) {
            this.detailDialog = true;
            this.$nextTick(() => {
                this.detail = row;
            });
        },
        closeDetailDialog() {
            this.detailDialog = false;
        },
        add() {
            this.dialogVisible = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                this.disabled = false;
                this.form = {};
                this.judge = false;
            });
        },
        save() {
            this.$refs.form.validate(async (valid) => {
                if (valid) {
                    if (this.judge === false) {
                        //新增
                        await request.post("/visitor/add", this.form).then((res) => {
                            console.log(res);
                            if (res.code === "0") {
                                ElMessage({
                                    message: "Added successfully.",
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
                    } else {
                        //修改
                        await request.put("/visitor/update", this.form).then((res) => {
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
            this.dialogVisible = false;
        },
        handleEdit(row) {
            //修改
            this.judge = true;
            this.dialogVisible = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                // 生拷贝
                this.form = JSON.parse(JSON.stringify(row));
                this.disabled = true;
            });
        },
        async handleDelete(id) {
            console.log(id);
            request.delete("/visitor/delete/" + id).then((res) => {
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