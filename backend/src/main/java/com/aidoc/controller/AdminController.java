package com.aidoc.controller;

import com.aidoc.common.PageResult;
import com.aidoc.common.Result;
import com.aidoc.dto.RegisterDTO;
import com.aidoc.dto.ResetPasswordDTO;
import com.aidoc.dto.UserStatusDTO;
import com.aidoc.service.AdminService;
import com.aidoc.vo.AccessLogVO;
import com.aidoc.vo.AdminUserVO;
import com.aidoc.vo.DocumentVO;
import com.aidoc.vo.StatsOverviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端控制器（/api/admin/** 由 AuthInterceptor 校验 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 用户分页（含文档数 / 会话数）。
     */
    @GetMapping("/user/page")
    public Result<PageResult<AdminUserVO>> userPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.pageUsers(current, size, keyword, role, status));
    }

    /**
     * 新增用户。
     */
    @PostMapping("/user")
    public Result<Void> createUser(@Valid @RequestBody RegisterDTO dto) {
        adminService.createUser(dto);
        return Result.ok("用户创建成功", null);
    }

    /**
     * 启用 / 禁用用户（禁用即踢下线）。
     */
    @PutMapping("/user/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusDTO dto) {
        adminService.updateStatus(id, dto);
        return Result.ok("状态更新成功", null);
    }

    /**
     * 重置用户密码。
     */
    @PutMapping("/user/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        adminService.resetPassword(id, dto);
        return Result.ok("密码已重置", null);
    }

    /**
     * 删除用户（级联清理全部数据）。
     */
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Result.ok("用户已删除", null);
    }

    /**
     * 全库文档分页（文档资源统计）。
     */
    @GetMapping("/doc/page")
    public Result<PageResult<DocumentVO>> docPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.pageDocs(current, size, keyword));
    }

    /**
     * 强删任意文档。
     */
    @DeleteMapping("/doc/{id}")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        adminService.deleteDocForce(id);
        return Result.ok("文档已删除", null);
    }

    /**
     * 数据看板汇总。
     */
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(adminService.overview());
    }

    /**
     * 访问日志分页。
     */
    @GetMapping("/access/page")
    public Result<PageResult<AccessLogVO>> accessPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.pageAccessLogs(current, size));
    }
}
