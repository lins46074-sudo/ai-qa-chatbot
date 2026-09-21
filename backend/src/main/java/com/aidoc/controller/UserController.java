package com.aidoc.controller;

import com.aidoc.common.Result;
import com.aidoc.dto.UpdatePasswordDTO;
import com.aidoc.dto.UpdateProfileDTO;
import com.aidoc.service.UserService;
import com.aidoc.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器：当前用户信息查询与资料维护（均基于 JWT 中的用户身份，无需前端传 userId）。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 当前登录用户信息。
     *
     * @return 用户视图
     */
    @GetMapping("/info")
    public Result<UserVO> info() {
        return Result.ok(userService.getCurrentUser());
    }

    /**
     * 修改个人资料。
     *
     * @param dto 昵称/邮箱/头像
     * @return 更新后的用户视图
     */
    @PutMapping("/profile")
    public Result<UserVO> profile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.ok(userService.updateProfile(dto));
    }

    /**
     * 修改密码。
     *
     * @param dto 旧密码 + 新密码
     * @return 成功提示
     */
    @PutMapping("/password")
    public Result<Void> password(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(dto);
        return Result.ok("密码修改成功", null);
    }
}
