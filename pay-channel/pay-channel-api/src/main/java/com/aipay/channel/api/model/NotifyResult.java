package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotifyResult {
    private boolean success;
    private String transactionNo;
    private String outTradeNo;
    private int paidAmount;
    private String failureReason;
}
