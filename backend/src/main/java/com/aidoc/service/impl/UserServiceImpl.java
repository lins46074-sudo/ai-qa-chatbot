package com.aidoc.service.impl;

import com.aidoc.common.BusinessException;
import com.aidoc.common.ResultCode;
import com.aidoc.dto.LoginDTO;
import com.aidoc.dto.RegisterDTO;
import com.aidoc.dto.UpdatePasswordDTO;
import com.aidoc.dto.UpdateProfileDTO;
import com.aidoc.entity.User;
import com.aidoc.mapper.UserMapper;
import com.aidoc.service.UserService;
import com.aidoc.util.JwtUtil;
import com.aidoc.util.RedisKeys;
import com.aidoc.util.RedisUtil;
import com.aidoc.util.UserContext;
import com.aidoc.vo.LoginVO;
import com.aidoc.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现：注册、登录、注销与个人资料维护。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    /** token 有效期（小时），用于计算 LoginVO.expiresIn 秒数 */
    @Value("${aidoc.jwt.expire-hours}")
    private long expireHours;

    /** Redis 登录态过期时长（小时） */
    @Value("${aidoc.redis-ttl.login-hours}")
    private long loginTtlHours;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, RedisUtil redisUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    /**
     * 注册：用户名查重 → BCrypt 加密 → 落库。
     *
     * @param dto 注册参数
     */
    @Override
    public void register(RegisterDTO dto) {
        // 1. 用户名唯一性校验（数据库唯一索引作为兜底）
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "用户名已存在，请更换后重试");
        }
        // 2. 组装新用户（角色 USER、状态正常）
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail() == null ? "" : dto.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        log.info("[注册] 新用户注册成功: {}", dto.getUsername());
    }

    /**
     * 登录：账号密码校验 → 更新最近登录时间 → 签发 JWT → 写 Redis 登录态。
     *
     * @param dto 登录参数
     * @return token + 用户信息
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 按用户名查询（数据库 username 唯一）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            // 统一提示，避免暴露"用户不存在"信息
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "用户名或密码错误");
        }
        // 2. 账号状态校验
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "账号已被禁用，请联系管理员");
        }
        // 3. 登录入口与角色匹配校验：管理端入口仅允许 ADMIN 登录
        if ("ADMIN".equalsIgnoreCase(dto.getLoginType()) && !"ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "该账号不是管理员，请从用户入口登录");
        }
        // 4. 更新最近登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 5. 签发 JWT（sub=userId，声明 username/role）
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

        // 6. 写 Redis 登录态：aidoc:login:{userId} -> {token, loginAt}
        //    TTL 与 token 有效期一致；认证拦截器每请求比对，实现注销/踢人/顶号下线
        Map<String, Object> loginCache = new HashMap<>();
        loginCache.put("token", token);
        loginCache.put("loginAt", System.currentTimeMillis());
        redisUtil.set(RedisKeys.LOGIN + user.getId(), loginCache, loginTtlHours, TimeUnit.HOURS);

        // 7. 组装返回（token 不落日志）
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(expireHours * 3600L);
        vo.setUser(toUserVO(user));
        return vo;
    }

    /**
     * 注销：删除当前用户 Redis 登录态，token 立即失效。
     */
    @Override
    public void logout() {
        redisUtil.delete(RedisKeys.LOGIN + UserContext.currentUserId());
    }

    /**
     * 查询当前登录用户信息。
     *
     * @return 用户视图
     */
    @Override
    public UserVO getCurrentUser() {
        return toUserVO(requireCurrentUser());
    }

    /**
     * 修改个人资料：仅更新非空字段。
     *
     * @param dto 资料参数
     * @return 更新后的用户视图
     */
    @Override
    public UserVO updateProfile(UpdateProfileDTO dto) {
        User user = requireCurrentUser();
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(user);
        return toUserVO(user);
    }

    /**
     * 修改密码：校验旧密码 → 加密新密码落库。
     *
     * @param dto 旧密码 + 新密码
     */
    @Override
    public void updatePassword(UpdatePasswordDTO dto) {
        User user = requireCurrentUser();
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        // 密码变更后强制其它端重新登录：删除旧登录态
        redisUtil.delete(RedisKeys.LOGIN + user.getId());
    }

    /**
     * 取当前登录用户实体；异常时抛 401（理论上拦截器已保证存在）。
     *
     * @return 当前用户
     */
    private User requireCurrentUser() {
        User user = userMapper.selectById(UserContext.currentUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 实体 → 视图转换（不携带密码等敏感字段）。
     *
     * @param user 用户实体
     * @return 用户视图
     */
    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
