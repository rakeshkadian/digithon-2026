package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfo {
    private String javaName;
    private String columnName;
    private String javaType;
    private boolean id;
    private boolean nullable;
    private Integer length;
}
