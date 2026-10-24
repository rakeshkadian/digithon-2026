package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** One PI column candidate shown in the findings table. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Finding {
    private String table;
    private String column;
    private String javaField;
    private String piType;      // EMAIL, PHONE, FULL_NAME, ... or NOT_PI
    private int confidence;     // 0-100
    private String reason;
    private String recommendation; // ENCRYPT | ENCRYPT_WITH_BLIND_INDEX | REVIEW | NONE
    @Builder.Default private List<CodeRef> codeRefs = new ArrayList<>();
    @Builder.Default private String status = "NEW";   // NEW | APPROVED | REJECTED
}
