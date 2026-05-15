package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notify_record")
public class NotifyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String notifyKey;
    private String chargeId;
    private String channel;
    private String rawBody;
    private String status;
    private Integer processCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
