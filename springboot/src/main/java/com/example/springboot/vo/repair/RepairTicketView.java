package com.example.springboot.vo.repair;

import com.example.springboot.entity.RepairTicket;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import java.time.LocalDateTime;

@Data
public class RepairTicketView {
    private Long id;
    private String requesterUsername;
    private String title;
    private String description;
    private String locationType;
    private String locationText;
    private String assigneeUsername;
    private String status;
    private Integer version;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime resolvedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;

    public static RepairTicketView from(RepairTicket ticket) {
        RepairTicketView view = new RepairTicketView();
        BeanUtils.copyProperties(ticket, view);
        return view;
    }
}
