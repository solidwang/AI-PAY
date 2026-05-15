package com.aipay.common.exception;

public enum ErrorCode {
    INVALID_API_KEY("invalid_api_key", "Invalid or missing API key"),
    CHANNEL_NOT_CONFIGURED("channel_not_configured", "Payment channel not configured for this app"),
    CHARGE_NOT_FOUND("charge_not_found", "Charge not found"),
    CHARGE_ALREADY_PAID("charge_already_paid", "Charge is already paid"),
    REFUND_AMOUNT_EXCEEDED("refund_amount_exceeded", "Refund amount exceeds remaining refundable amount"),
    CHANNEL_ERROR("channel_error", "Payment channel returned an error"),
    NOTIFY_SIGNATURE_INVALID("notify_signature_invalid", "Notification signature verification failed"),
    MERCHANT_NOT_FOUND("merchant_not_found", "Merchant not found"),
    APP_NOT_FOUND("app_not_found", "App not found");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
