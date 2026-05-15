package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantNo;
    private String name;
    private Integer status;
    private String contactEmail;
    private String contactPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
