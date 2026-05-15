package com.aipay.core.mapper;

import com.aipay.core.domain.App;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppMapper extends BaseMapper<App> {
    @Select("SELECT * FROM app WHERE app_id = #{appId} AND status = 1")
    App selectByAppId(String appId);
}
