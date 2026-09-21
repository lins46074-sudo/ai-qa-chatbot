package com.aidoc.runner;

import com.aidoc.entity.User;
import com.aidoc.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始数据初始化器。
 *
 * <p>首次启动（sys_user 为空）时自动创建内置账号，避免把 BCrypt 密文写死在 SQL 中：
 * <ul>
 *   <li>管理员：admin / {aidoc.init.admin-password}（角色 ADMIN）；</li>
 *   <li>演示用户：demo / {aidoc.init.demo-password}（角色 USER）。</li>
 * </ul>
 * 幂等：逐用户名判断存在性，重复启动不会重复创建。</p>
 */
@Slf4j
@Component
public class InitDataRunner implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${aidoc.init.admin-username}")
    private String adminUsername;
    @Value("${aidoc.init.admin-password}")
    private String adminPassword;
    @Value("${aidoc.init.demo-username}")
    private String demoUsername;
    @Value("${aidoc.init.demo-password}")
    private String demoPassword;

    public InitDataRunner(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 启动后执行：若账号不存在则创建管理员与演示用户。
     *
     * @param args 命令行参数（未使用）
     */
    @Override
    public void run(String... args) {
        createIfAbsent(adminUsername, adminPassword, "系统管理员", "ADMIN");
        createIfAbsent(demoUsername, demoPassword, "演示用户", "USER");
    }

    /**
     * 按用户名判断是否存在，不存在则插入内置账号。
     *
     * @param username 登录名
     * @param rawPassword 明文密码（入库前 BCrypt 加密）
     * @param nickname  昵称
     * @param role      角色（USER / ADMIN）
     */
    private void createIfAbsent(String username, String rawPassword, String nickname, String role) {
        Long count = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("[初始化] 已创建内置{}账号: {} / {}", "ADMIN".equals(role) ? "管理员" : "用户", username, rawPassword);
    }
}
