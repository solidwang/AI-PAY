package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operator")
public class Operator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String username;
    private String passwordHash;
    private String realName;
    private Integer isAdmin;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
