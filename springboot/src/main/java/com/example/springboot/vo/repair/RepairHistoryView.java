package com.example.springboot.vo.repair;

import com.example.springboot.entity.RepairTicketHistory;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import java.time.LocalDateTime;

@Data
public class RepairHistoryView {
    private Long id;
    private Long ticketId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String actorRole;
    private String actorUsername;
    private String note;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    private Integer ticketVersion;

    public static RepairHistoryView from(RepairTicketHistory history) {
        RepairHistoryView view = new RepairHistoryView();
        BeanUtils.copyProperties(history, view);
        return view;
    }
}
