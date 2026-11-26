package com.example.springboot.dto.repair;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignRepairTicketRequest extends RepairWriteRequest {
    private String assigneeUsername;
}
