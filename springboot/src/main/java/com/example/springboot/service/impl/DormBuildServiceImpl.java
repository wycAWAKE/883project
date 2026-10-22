package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot.entity.DormBuild;
import com.example.springboot.mapper.DormBuildMapper;
import com.example.springboot.service.DormBuildService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class DormBuildServiceImpl extends ServiceImpl<DormBuildMapper, DormBuild> implements DormBuildService {
    @Resource
    private DormBuildMapper dormBuildMapper;

    @Override
    public int addNewBuilding(DormBuild dormBuild) {
        return dormBuildMapper.insert(dormBuild);
    }

    @Override
    public Page find(Integer pageNum, Integer pageSize, String search) {
        Page<DormBuild> page = new Page<>(pageNum, pageSize);
        QueryWrapper<DormBuild> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("dormbuild_name", search);
        return dormBuildMapper.selectPage(page, queryWrapper);
    }

    @Override
    public int updateNewBuilding(DormBuild dormBuild) {
        return dormBuildMapper.updateById(dormBuild);
    }

    @Override
    public int deleteBuilding(Integer id) {
        return dormBuildMapper.deleteById(id);
    }

    @Override
    public List<DormBuild> getBuildingId() {
        // 答辩链路-楼栋表查库：按 dormbuild_id 升序读取 dorm_build 表；下一步 DormBuildController.getBuildingName() 把楼栋编号返回给图表组件。
        QueryWrapper<DormBuild> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("dormbuild_id");
        return dormBuildMapper.selectList(queryWrapper);
    }
}
