package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reconcile_record")
public class ReconcileRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private Long merchantId;
    private String channel;
    private LocalDate reconcileDate;
    private String status;
    private Integer totalCount;
    private Long totalAmount;
    private Integer matchedCount;
    private Integer unmatchedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
