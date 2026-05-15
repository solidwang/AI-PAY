package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("refund")
public class Refund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundId;
    private String chargeId;
    private Long appId;
    private Long merchantId;
    private String outRefundNo;
    private Integer amount;
    private String description;
    private String status;
    private String transactionNo;
    private String failureCode;
    private String failureMsg;
    private LocalDateTime succeedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
