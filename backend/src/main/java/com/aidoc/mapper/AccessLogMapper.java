package com.aidoc.mapper;

import com.aidoc.entity.AccessLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口访问日志 Mapper。
 *
 * <p>继承 MyBatis-Plus BaseMapper，自动获得单表 CRUD / 分页能力；
 * AccessLogRecorder 异步插入日志、管理模块分页查询日志均经由本接口。</p>
 */
@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {
}
