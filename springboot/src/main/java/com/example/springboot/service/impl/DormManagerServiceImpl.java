package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot.entity.DormManager;
import com.example.springboot.mapper.DormManagerMapper;
import com.example.springboot.service.DormManagerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class DormManagerServiceImpl extends ServiceImpl<DormManagerMapper, DormManager> implements DormManagerService {
    @Resource
    private DormManagerMapper dormManagerMapper;

    @Override
    public DormManager dormManagerLogin(String username, String password) {
        QueryWrapper<DormManager> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).eq("password", password);
        return dormManagerMapper.selectOne(queryWrapper);
    }

    @Override
    public int addNewDormManager(DormManager dormManager) {
        return dormManagerMapper.insert(dormManager);
    }

    @Override
    public Page find(Integer pageNum, Integer pageSize, String search) {
        Page<DormManager> page = new Page<>(pageNum, pageSize);
        QueryWrapper<DormManager> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", search);
        return dormManagerMapper.selectPage(page, queryWrapper);
    }

    @Override
    public int updateNewDormManager(DormManager dormManager) {
        return dormManagerMapper.updateById(dormManager);
    }

    @Override
    public int deleteDormManager(String username) {
        return dormManagerMapper.deleteById(username);
    }
}
