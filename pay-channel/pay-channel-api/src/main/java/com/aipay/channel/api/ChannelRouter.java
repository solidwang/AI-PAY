package com.aipay.channel.api;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChannelRouter {
    private final Map<String, PayChannel> channels;

    public ChannelRouter(List<PayChannel> channelList) {
        this.channels = channelList.stream()
            .collect(Collectors.toMap(PayChannel::channelCode, Function.identity()));
    }

    public PayChannel route(String channelCode) {
        PayChannel channel = channels.get(channelCode);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown channel: " + channelCode);
        }
        return channel;
    }
}
