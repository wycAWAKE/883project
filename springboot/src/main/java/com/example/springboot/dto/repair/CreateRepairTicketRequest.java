package com.example.springboot.dto.repair;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

@Data
public class CreateRepairTicketRequest {
    private String title;
    private String description;
    private String locationType;
    private String locationText;

    // Do not silently accept a forged requester, status, timestamp, or version.
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported ticket field.");
    }
}
