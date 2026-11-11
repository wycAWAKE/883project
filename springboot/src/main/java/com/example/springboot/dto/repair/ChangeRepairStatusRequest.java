package com.example.springboot.dto.repair;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRepairStatusRequest extends RepairWriteRequest {
    private String targetStatus;
}
