package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("operator_permission")
public class OperatorPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String module;
    private Integer canView;
    private Integer canOperate;
}
