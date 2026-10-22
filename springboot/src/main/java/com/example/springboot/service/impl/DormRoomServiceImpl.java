package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot.entity.AdjustRoom;
import com.example.springboot.entity.DormRoom;
import com.example.springboot.mapper.DormRoomMapper;
import com.example.springboot.service.DormRoomService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class DormRoomServiceImpl extends ServiceImpl<DormRoomMapper, DormRoom> implements DormRoomService {
    @Resource
    private DormRoomMapper dormRoomMapper;

    @Override
    public int notFullRoom() {
        // 答辩链路-空房统计查库：查询 dorm_room 表 current_capacity < max_capacity 的记录数；下一步返回 DormRoomController.noFullRoom()。
        QueryWrapper<DormRoom> queryWrapper = new QueryWrapper<>();
        queryWrapper.apply("current_capacity < max_capacity");
        return Math.toIntExact(dormRoomMapper.selectCount(queryWrapper));
    }

    @Override
    public int addNewRoom(DormRoom dormRoom) {
        return dormRoomMapper.insert(dormRoom);
    }

    @Override
    public Page find(Integer pageNum, Integer pageSize, String search) {
        Page<DormRoom> page = new Page<>(pageNum, pageSize);
        QueryWrapper<DormRoom> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("dormroom_id", search).or().like("dormbuild_id", search);
        return dormRoomMapper.selectPage(page, queryWrapper);
    }

    @Override
    public int updateNewRoom(DormRoom dormRoom) {
        return dormRoomMapper.updateById(dormRoom);
    }

    @Override
    public int deleteRoom(Integer dormRoomId) {
        return dormRoomMapper.deleteById(dormRoomId);
    }

    @Override
    public int deleteBedInfo(String bedName, Integer dormRoomId, int calCurrentNum) {
        UpdateWrapper<DormRoom> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("dormroom_id", dormRoomId).set(bedColumn(bedName), null).set("current_capacity", calCurrentNum);
        return dormRoomMapper.update(null, updateWrapper);
    }

    @Override
    public DormRoom judgeHadBed(String username) {
        QueryWrapper<DormRoom> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("first_bed", username).or().eq("second_bed", username).or().eq("third_bed", username).or().eq("fourth_bed", username);
        return dormRoomMapper.selectOne(queryWrapper);
    }

    @Override
    public Long selectHaveRoomStuNum() {
        // 答辩链路-住宿人数查库：汇总 dorm_room.current_capacity；下一步返回 DormRoomController.selectHaveRoomStuNum()，再返回首页卡片。
        QueryWrapper<DormRoom> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(SUM(current_capacity),0) AS current_capacity");
        DormRoom room = dormRoomMapper.selectOne(queryWrapper);
        return room == null ? 0L : Long.valueOf(room.getCurrentCapacity());
    }

    @Override
    public Long getEachBuildingStuNum(int dormBuildId) {
        // 答辩链路-楼栋人数查库：按 dormbuild_id 分组思路统计 dorm_room.current_capacity；下一步返回 home_echarts.vue 生成柱状条。
        QueryWrapper<DormRoom> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dormbuild_id", dormBuildId).select("IFNULL(SUM(current_capacity),0) AS current_capacity");
        DormRoom room = dormRoomMapper.selectOne(queryWrapper);
        return room == null ? 0L : Long.valueOf(room.getCurrentCapacity());
    }

    @Override
    public int adjustRoomUpdate(AdjustRoom adjustRoom) {
        // 答辩链路-调宿通过后改床位：审核通过时更新旧房间和目标房间的床位字段；下一步 dormRoomMapper.update() 写回 dorm_room 表。
        DormRoom oldRoom = dormRoomMapper.selectById(adjustRoom.getCurrentRoomId());
        DormRoom newRoom = dormRoomMapper.selectById(adjustRoom.getTowardsRoomId());
        if (oldRoom == null || newRoom == null) {
            return 0;
        }
        if (getBedValue(newRoom, adjustRoom.getTowardsBedId()) != null) {
            return -2;
        }
        UpdateWrapper<DormRoom> oldWrapper = new UpdateWrapper<>();
        oldWrapper.eq("dormroom_id", adjustRoom.getCurrentRoomId())
            .set(bedColumn(adjustRoom.getCurrentBedId()), null)
            .set("current_capacity", Math.max(oldRoom.getCurrentCapacity() - 1, 0));
        int oldUpdated = dormRoomMapper.update(null, oldWrapper);

        UpdateWrapper<DormRoom> newWrapper = new UpdateWrapper<>();
        newWrapper.eq("dormroom_id", adjustRoom.getTowardsRoomId())
            .set(bedColumn(adjustRoom.getTowardsBedId()), adjustRoom.getUsername())
            .set("current_capacity", newRoom.getCurrentCapacity() + 1);
        int newUpdated = dormRoomMapper.update(null, newWrapper);
        return oldUpdated == 1 && newUpdated == 1 ? 1 : 0;
    }

    @Override
    public DormRoom checkRoomState(Integer dormRoomId) {
        DormRoom dormRoom = dormRoomMapper.selectById(dormRoomId);
        return dormRoom != null && dormRoom.getCurrentCapacity() < dormRoom.getMaxCapacity() ? dormRoom : null;
    }

    @Override
    public DormRoom checkRoomExist(Integer dormRoomId) {
        return dormRoomMapper.selectById(dormRoomId);
    }

    @Override
    public DormRoom checkBedState(Integer dormRoomId, int bedNum) {
        DormRoom dormRoom = dormRoomMapper.selectById(dormRoomId);
        return dormRoom != null && getBedValue(dormRoom, bedNum) == null ? dormRoom : null;
    }

    private static String bedColumn(String bedName) {
        if ("firstBed".equals(bedName) || "1".equals(bedName)) {
            return "first_bed";
        }
        if ("secondBed".equals(bedName) || "2".equals(bedName)) {
            return "second_bed";
        }
        if ("thirdBed".equals(bedName) || "3".equals(bedName)) {
            return "third_bed";
        }
        return "fourth_bed";
    }

    private static String bedColumn(int bedNum) {
        return bedColumn(String.valueOf(bedNum));
    }

    private static String getBedValue(DormRoom room, int bedNum) {
        switch (bedNum) {
            case 1:
                return room.getFirstBed();
            case 2:
                return room.getSecondBed();
            case 3:
                return room.getThirdBed();
            default:
                return room.getFourthBed();
        }
    }
}
