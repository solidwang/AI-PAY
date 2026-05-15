package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class CreateOrderResult {
    private boolean success;
    private String transactionNo;
    private Map<String, Object> credential;
    private String failureCode;
    private String failureMsg;
}
