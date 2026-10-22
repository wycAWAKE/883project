package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot.entity.AdjustRoom;
import com.example.springboot.mapper.AdjustRoomMapper;
import com.example.springboot.service.AdjustRoomService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AdjustRoomServiceImpl extends ServiceImpl<AdjustRoomMapper, AdjustRoom> implements AdjustRoomService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AdjustRoomMapper adjustRoomMapper;

    @Override
    public Page find(Integer pageNum, Integer pageSize, String search) {
        Page<AdjustRoom> page = new Page<>(pageNum, pageSize);
        QueryWrapper<AdjustRoom> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("username", search).or().like("name", search).orderByDesc("apply_time");
        return adjustRoomMapper.selectPage(page, queryWrapper);
    }

    @Override
    public int deleteAdjustment(Integer id) {
        return adjustRoomMapper.deleteById(id);
    }

    @Override
    public int updateApply(AdjustRoom adjustRoom) {
        return adjustRoomMapper.updateById(adjustRoom);
    }

    @Override
    public int addApply(AdjustRoom adjustRoom) {
        if (adjustRoom.getState() == null || adjustRoom.getState().trim().isEmpty()) {
            adjustRoom.setState("未处理");
        }
        if (adjustRoom.getApplyTime() == null || adjustRoom.getApplyTime().trim().isEmpty()) {
            adjustRoom.setApplyTime(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        }
        return adjustRoomMapper.insert(adjustRoom);
    }
}
