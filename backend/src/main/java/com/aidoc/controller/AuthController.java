package com.aidoc.controller;

import com.aidoc.common.Result;
import com.aidoc.dto.LoginDTO;
import com.aidoc.dto.RegisterDTO;
import com.aidoc.service.UserService;
import com.aidoc.vo.LoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：注册 / 登录 / 注销。
 *
 * <p>登录与注册位于拦截器白名单（见 WebMvcConfig），无需携带 token；
 * 注销需要登录态（拦截器校验），删除 Redis 登录态使 token 即刻失效。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册。
     *
     * @param dto 注册参数（用户名/密码/昵称/邮箱）
     * @return 注册成功提示
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok("注册成功", null);
    }

    /**
     * 用户登录。
     *
     * @param dto 登录参数（用户名/密码）
     * @return token + 用户信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    /**
     * 注销登录（需登录态）。
     *
     * @return 注销成功提示
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.ok("已退出登录", null);
    }
}
