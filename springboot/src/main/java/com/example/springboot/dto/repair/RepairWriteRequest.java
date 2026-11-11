package com.example.springboot.dto.repair;

import com.example.springboot.repair.M4VersionDeserializer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
public class RepairWriteRequest {
    @JsonDeserialize(using = M4VersionDeserializer.class)
    private Integer version;
    private String note;
    @JsonAnySetter public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported operation field.");
    }
}
