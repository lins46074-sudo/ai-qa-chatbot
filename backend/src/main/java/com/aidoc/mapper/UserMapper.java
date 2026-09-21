package com.aidoc.mapper;

import com.aidoc.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>继承 MyBatis-Plus BaseMapper，自动获得单表 CRUD / 分页能力；
 * 业务模块直接注入本接口按需查询，无需手写基础 SQL。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
