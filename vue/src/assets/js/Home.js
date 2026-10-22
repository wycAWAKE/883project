import weather from "@/components/weather.vue";
import Calender from "@/components/Calendar.vue";
import request from "@/utils/request";
import home_echarts from "@/components/home_echarts.vue";

export default {
    name: "Home",
    components: {
        weather,
        Calender,
        home_echarts,
    },
    data() {
        return {
            studentNum: 0,
            haveRoomStudentNum: 0,
            repairOrderNum: 0,
            noFullRoomNum: 0,
            activities: [],
            retryTimer: null,
            retryCount: 0,
        };
    },
    created() {
        // 答辩链路-首页图表入口：进入首页后加载统计卡片和图表数据；下一步看下面 getStuNum/getHaveRoomNum/getOrderNum/getNoFullRoom。
        this.loadHomeData();
    },
    beforeUnmount() {
        if (this.retryTimer) {
            clearTimeout(this.retryTimer);
        }
    },
    methods: {
        loadHomeData() {
            this.getHomePageNotice();
            this.getStuNum();
            this.getHaveRoomNum();
            this.getOrderNum();
            this.getNoFullRoom();
        },
        scheduleRetry() {
            if (this.retryCount >= 8 || this.retryTimer) {
                return;
            }
            this.retryCount += 1;
            this.retryTimer = setTimeout(() => {
                this.retryTimer = null;
                this.loadHomeData();
            }, 1500);
        },
        resetRetry() {
            this.retryCount = 0;
            if (this.retryTimer) {
                clearTimeout(this.retryTimer);
                this.retryTimer = null;
            }
        },
        async getStuNum() {
            // 答辩链路-首页学生总数：请求 /stu/stuNum；下一步看后端 StudentController.stuNum()，最终统计 student 表。
            request.get("/stu/stuNum").then((res) => {
                if (res.code === "0") {
                    this.studentNum = res.data;
                    this.resetRetry();
                } else {
                    this.scheduleRetry();
                }
            }).catch(() => this.scheduleRetry());
        },
        async getHaveRoomNum() {
            // 答辩链路-首页住宿人数：请求 /room/selectHaveRoomStuNum；下一步看 DormRoomController.selectHaveRoomStuNum()，最终汇总 dorm_room.current_capacity。
            request.get("/room/selectHaveRoomStuNum").then((res) => {
                if (res.code === "0") {
                    this.haveRoomStudentNum = res.data;
                    this.resetRetry();
                } else {
                    this.scheduleRetry();
                }
            }).catch(() => this.scheduleRetry());
        },
        async getOrderNum() {
            // 答辩链路-首页报修数：请求 /repair/orderNum；下一步看 RepairController.orderNum()，最终统计 repair 表。
            request.get("/repair/orderNum").then((res) => {
                if (res.code === "0") {
                    this.repairOrderNum = res.data;
                    this.resetRetry();
                } else {
                    this.scheduleRetry();
                }
            }).catch(() => this.scheduleRetry());
        },
        async getNoFullRoom() {
            // 答辩链路-首页空房数：请求 /room/noFullRoom；下一步看 DormRoomController.noFullRoom()，最终查询 dorm_room 中 current_capacity < max_capacity 的房间。
            request.get("/room/noFullRoom").then((res) => {
                if (res.code === "0") {
                    this.noFullRoomNum = res.data;
                    this.resetRetry();
                } else {
                    this.scheduleRetry();
                }
            }).catch(() => this.scheduleRetry());
        },
        async getHomePageNotice() {
            // 答辩链路-首页公告：请求 /notice/homePageNotice；下一步看 NoticeController.homePageNotice()，最终读取 notice 表。
            request.get("/notice/homePageNotice").then((res) => {
                if (res.code === "0") {
                    this.activities = res.data;
                    this.resetRetry();
                } else {
                    this.scheduleRetry();
                }
            }).catch(() => this.scheduleRetry());
        },
    },
};
