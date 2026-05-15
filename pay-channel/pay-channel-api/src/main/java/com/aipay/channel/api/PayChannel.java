package com.aipay.channel.api;

import com.aipay.channel.api.model.*;

public interface PayChannel {
    /** Returns the channel code this implementation handles, e.g. "wechat_jsapi" */
    String channelCode();

    /** Creates an order with the payment channel and returns a credential for the client. */
    CreateOrderResult createOrder(CreateOrderRequest req);

    /** Parses and validates an inbound async notification from the channel. */
    NotifyResult parseNotify(NotifyRequest req);

    /** Initiates a refund. */
    RefundResult refund(RefundRequest req);

    /** Queries order status from the channel. */
    QueryResult query(String outTradeNo);
}
