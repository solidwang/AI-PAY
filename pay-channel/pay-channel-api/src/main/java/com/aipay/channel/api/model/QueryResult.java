package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryResult {
    private boolean paid;
    private String transactionNo;
    private int paidAmount;
}
