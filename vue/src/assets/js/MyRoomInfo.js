import { displayMessage } from "@/utils/english";
import request from "@/utils/request";

import { ElMessage } from 'element-plus';

export default {
    name: "MyRoomInfo",
    data() {
        return {
            name: "",
            form: {
                username: "",
            },
            room: {
                dormRoomId: "",
                dormBuildId: "",
                floorNum: "",
                maxCapacity: "",
                currentCapacity: "",
                firstBed: "",
                secondBed: "",
                thirdBed: "",
                fourthBed: "",
            },
        };
    },
    created() {
        this.init();
        this.getInfo();
    },
    methods: {
        init() {
            this.form = JSON.parse(sessionStorage.getItem("user"));
            this.name = this.form.username;
        },
        getInfo() {
            request.get("/room/getMyRoom/" + this.name).then((res) => {
                if (res.code === "0") {
                    this.room = res.data;
                    console.log(this.room);
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