package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("charge")
public class Charge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chargeId;
    private Long appId;
    private Long merchantId;
    private String outTradeNo;
    private String channel;
    private Integer amount;
    private String currency;
    private String subject;
    private String body;
    private String clientIp;
    private String status;
    private Integer paid;
    private LocalDateTime paidAt;
    private LocalDateTime timeExpire;
    private String transactionNo;
    private String channelExtra;
    private String credential;
    private String failureCode;
    private String failureMsg;
    private Integer amountRefunded;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
