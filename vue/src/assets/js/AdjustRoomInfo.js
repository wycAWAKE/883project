import { displayMessage, namePattern } from "@/utils/english";
import request from "@/utils/request";

import { ElMessage } from 'element-plus';
export default {
    name: "AdjustRoomInfo",
    data() {
        const checkRoomState = (rule, value, callback) => {
            this.dormRoomId = value
            request.get("/room/checkRoomState/" + value).then((res) => {
                if (res.code === "0") {
                    callback();
                } else {
                    callback(new Error(displayMessage(res.msg)));
                }
            });
        };
        const checkBedState = (rule, value, callback) => {
            request.get("/room/checkBedState/" + this.dormRoomId + '/' + value).then((res) => {
                if (res.code === "0") {
                    callback();
                } else {
                    callback(new Error(displayMessage(res.msg)));
                }
            });
        };
        const checkApplyState = (rule, value, callback) => {
            console.log(this.form.finishTime)
            if (value === "通过" && this.form.finishTime !== null) {
                callback();
            } else if (value === "驳回" && this.form.finishTime !== null) {
                callback();
            } else {
                callback(new Error("The completion status and completion time must match."));
            }
        };
        return {
            loading: true,
            dialogVisible: false,
            detailDialog: false,
            search: "",
            currentPage: 1,
            pageSize: 10,
            total: 0,
            tableData: [],
            form: {},
            dormRoomId: 0,
            orderState: false,
            rules: {
                username: [
                    {required: true, message: "Enter a student ID", trigger: "blur"},
                    {pattern: /^[a-zA-Z0-9]{4,9}$/, message: "Use 4 to 9 letters or digits", trigger: "blur",},
                ],
                name: [
                    {required: true, message: "Enter a name", trigger: "blur"},
                    {pattern: namePattern, message: "Use 1 to 50 letters, spaces, hyphens, apostrophes or Chinese characters", trigger: "blur",},
                ],
                currentRoomId: [
                    {required: true, message: "Enter the current room number", trigger: "blur"},
                ],
                currentBedId: [
                    {required: true, message: "Enter the current bed number", trigger: "blur"},
                ],
                state: [{validator: checkApplyState, trigger: "blur"},],
                towardsRoomId: [{validator: checkRoomState, trigger: "blur"}],
                towardsBedId: [{validator: checkBedState, trigger: "blur"}],
            },
        }
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
            request.get("/adjustRoom/find", {
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
            request.get("/adjustRoom/find", {
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
        judgeOrderState(state) {
            if (state === '通过') {
                this.orderState = true
            } else if (state === '驳回') {
                this.orderState = false
            }
        },
        save() {
            this.$refs.form.validate((valid) => {
                if (valid) {
                    this.judgeOrderState(this.form.state)
                    //修改
                    request.put("/adjustRoom/update/" + this.orderState, this.form).then((res) => {
                        console.log(res);
                        if (res.code === "0") {
                            ElMessage({
                                message: "Updated successfully.",
                                type: "success",
                            });
                            this.search = "";
                            this.load();
                            this.dialogVisible = false;
                        } else if (res.msg === "重复操作") {
                            ElMessage({
                                message: displayMessage(res.msg),
                                type: "error",
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
            });
        },
        cancel() {
            this.$refs.form.resetFields();
            this.dialogVisible = false;
            this.detailDialog = false;
        },
        showDetail(row) {
            // 查看详情
            this.detailDialog = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                this.form = JSON.parse(JSON.stringify(row));
            });
        },
        handleEdit(row) {
            //修改
            // 生拷贝
            this.dialogVisible = true;
            this.$nextTick(() => {
                this.$refs.form.resetFields();
                this.form = JSON.parse(JSON.stringify(row));
            });
        },
        async handleDelete(id) {
            //删除
            request.delete("/adjustRoom/delete/" + id).then((res) => {
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
}