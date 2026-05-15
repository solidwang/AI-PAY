package com.aipay.core.mapper;

import com.aipay.core.domain.Charge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChargeMapper extends BaseMapper<Charge> {
    @Select("SELECT * FROM charge WHERE charge_id = #{chargeId}")
    Charge selectByChargeId(String chargeId);
}
