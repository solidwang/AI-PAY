package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("app")
public class App {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private Long merchantId;
    private String name;
    private String liveKey;
    private String testKey;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
