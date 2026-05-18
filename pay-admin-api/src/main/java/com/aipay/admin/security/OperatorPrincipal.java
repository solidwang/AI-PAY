package com.aipay.admin.security;

public record OperatorPrincipal(Long operatorId, Long merchantId, String token) {}
