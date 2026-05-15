package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("channel_config")
public class ChannelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String channel;
    private String configJson;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
