package com.aidoc.service;

import com.aidoc.dto.LoginDTO;
import com.aidoc.dto.RegisterDTO;
import com.aidoc.dto.UpdatePasswordDTO;
import com.aidoc.dto.UpdateProfileDTO;
import com.aidoc.vo.LoginVO;
import com.aidoc.vo.UserVO;

/**
 * 用户服务接口：注册 / 登录 / 注销 / 个人信息维护。
 *
 * <p>登录成功后签发 JWT 并写入 Redis 登录态（aidoc:login:{userId}），
 * 供认证拦截器做登录态比对，实现注销即失效、被踢下线等能力。</p>
 */
public interface UserService {

    /**
     * 用户注册：用户名唯一校验 + BCrypt 加密落库，默认角色 USER、状态正常。
     *
     * @param dto 注册参数（含校验注解）
     */
    void register(RegisterDTO dto);

    /**
     * 用户登录：校验账号密码 → 更新最近登录时间 → 签发 JWT → 写 Redis 登录态。
     *
     * @param dto 登录参数
     * @return 登录结果（token + 用户信息）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 注销登录：删除当前用户的 Redis 登录态（token 即刻失效）。
     */
    void logout();

    /**
     * 查询当前登录用户信息。
     *
     * @return 用户视图
     */
    UserVO getCurrentUser();

    /**
     * 修改个人资料（昵称 / 邮箱 / 头像，仅更新非空字段）。
     *
     * @param dto 资料参数
     * @return 更新后的用户视图
     */
    UserVO updateProfile(UpdateProfileDTO dto);

    /**
     * 修改密码：须校验旧密码正确。
     *
     * @param dto 旧密码 + 新密码
     */
    void updatePassword(UpdatePasswordDTO dto);
}
