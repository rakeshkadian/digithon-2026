package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeRef {
    private String filePath;
    private String className;
    private String methodName;
    private int line;
    private String kind;   // ENTITY_FIELD | REPO_METHOD | QUERY | DTO_FIELD | METHOD_CALL
}
