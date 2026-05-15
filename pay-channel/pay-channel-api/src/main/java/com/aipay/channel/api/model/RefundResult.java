package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResult {
    private boolean success;
    private String transactionNo;
    private String failureCode;
    private String failureMsg;
}
