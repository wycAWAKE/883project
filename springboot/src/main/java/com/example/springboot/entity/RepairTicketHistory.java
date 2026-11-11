package com.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("m4_repair_history")
public class RepairTicketHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String actorRole;
    private String actorUsername;
    private String note;
    private LocalDateTime createdAt;
    private Integer ticketVersion;
}
