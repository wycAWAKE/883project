package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot.entity.Electric;
import com.example.springboot.mapper.ElectricMapper;
import com.example.springboot.service.ElectricService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Service
public class ElectricServiceImpl extends ServiceImpl<ElectricMapper, Electric> implements ElectricService {
    @Resource
    private ElectricMapper electricMapper;

    @Override
    public int addNewOrder(Electric electric) {
        electric.setId(null);
        return electricMapper.insert(electric);
    }

    @Override
    public Page find(Integer pageNum, Integer pageSize, String search, String date1, String date2, Integer dormRoomId) {
        Page<Electric> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Electric> queryWrapper = new QueryWrapper<>();
        if (dormRoomId != null) {
            queryWrapper.eq("roomid", dormRoomId);
        }
        if (StringUtils.isNotEmpty(date1) && StringUtils.isNotEmpty(date2)) {
            try {
                queryWrapper.ge("date", convertDate(date1)).le("date", convertDate(date2));
            } catch (ParseException e) {
                throw new IllegalArgumentException("日期格式错误", e);
            }
        }
        queryWrapper.orderByDesc("date");
        return electricMapper.selectPage(page, queryWrapper);
    }

    @Override
    public Page individualFind(Integer pageNum, Integer pageSize, String search, String name) {
        Page<Electric> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Electric> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("operatorName", name).orderByDesc("date");
        return electricMapper.selectPage(page, queryWrapper);
    }

    @Override
    public int deleteOrder(Integer id) {
        return electricMapper.deleteById(id);
    }

    private static String convertDate(String strDate) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        Date myDate = dateFormat.parse(strDate.replace("Z", "+0000"));
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(myDate);
    }
}
