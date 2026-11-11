package com.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("m4_repair_ticket")
public class RepairTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requesterUsername;
    private String title;
    private String description;
    private String locationType;
    private String locationText;
    private String assigneeUsername;
    private String status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
