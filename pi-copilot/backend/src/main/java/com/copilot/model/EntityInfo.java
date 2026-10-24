package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityInfo {
    private String className;
    private String filePath;
    private String tableName;
    @Builder.Default private List<FieldInfo> fields = new ArrayList<>();
}
