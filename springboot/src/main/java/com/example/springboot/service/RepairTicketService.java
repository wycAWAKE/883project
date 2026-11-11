package com.example.springboot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springboot.dto.repair.*;
import com.example.springboot.vo.repair.RepairStaffOption;
import com.example.springboot.repair.M4Actor;
import com.example.springboot.vo.repair.RepairHistoryView;
import com.example.springboot.vo.repair.RepairTicketView;
import java.util.List;

public interface RepairTicketService {
    List<RepairStaffOption> staffOptions(M4Actor actor);
    RepairTicketView assign(M4Actor actor, Long id, AssignRepairTicketRequest request);
    RepairTicketView changeStatus(M4Actor actor, Long id, ChangeRepairStatusRequest request);
    RepairTicketView addProgress(M4Actor actor, Long id, AddRepairProgressRequest request);
    RepairTicketView create(M4Actor actor, CreateRepairTicketRequest request);
    Page<RepairTicketView> list(M4Actor actor, Integer pageNum, Integer pageSize,
                               String keyword, String status, Boolean mine);
    RepairTicketView detail(M4Actor actor, Long id);
    List<RepairHistoryView> history(M4Actor actor, Long id);
}
