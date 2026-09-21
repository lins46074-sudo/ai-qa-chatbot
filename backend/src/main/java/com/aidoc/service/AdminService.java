package com.aidoc.service;

import com.aidoc.common.PageResult;
import com.aidoc.dto.RegisterDTO;
import com.aidoc.dto.ResetPasswordDTO;
import com.aidoc.dto.UserStatusDTO;
import com.aidoc.vo.AccessLogVO;
import com.aidoc.vo.AdminUserVO;
import com.aidoc.vo.DocumentVO;
import com.aidoc.vo.StatsOverviewVO;

/**
 * 管理端服务接口：用户管理（增删改 / 启停 / 重置密码）、
 * 文档资源管理（跨用户统计与强删）、访问量看板。
 */
public interface AdminService {

    /**
     * 用户分页（联查每人文档数 / 会话数）。
     *
     * @param current 页码
     * @param size    每页条数
     * @param keyword 用户名/昵称关键字（可空）
     * @param role    角色（可空）
     * @param status  状态（可空）
     * @return 用户分页
     */
    PageResult<AdminUserVO> pageUsers(long current, long size, String keyword,
                                      String role, Integer status);

    /**
     * 新增用户（默认角色 USER）。
     *
     * @param dto 注册信息
     */
    void createUser(RegisterDTO dto);

    /**
     * 启用 / 禁用用户；禁用同时删除其 Redis 登录态实现强制下线。
     *
     * @param userId 用户 ID
     * @param status 0 禁用 / 1 启用
     */
    void updateStatus(Long userId, UserStatusDTO status);

    /**
     * 重置用户密码。
     *
     * @param userId 用户 ID
     * @param dto    新密码
     */
    void resetPassword(Long userId, ResetPasswordDTO dto);

    /**
     * 删除用户：级联清理其文档（含磁盘文件）、会话、消息与 Redis 缓存。
     *
     * @param userId 用户 ID
     */
    void deleteUser(Long userId);

    /**
     * 全库文档分页（含上传人用户名，资源统计用）。
     *
     * @param current 页码
     * @param size    每页条数
     * @param keyword 文件名关键字（可空）
     * @return 文档分页
     */
    PageResult<DocumentVO> pageDocs(long current, long size, String keyword);

    /**
     * 强删任意文档（跨用户，含级联清理）。
     *
     * @param docId 文档 ID
     */
    void deleteDocForce(Long docId);

    /**
     * 数据看板汇总：全局计数 + 今日指标 + 近 7 日趋势 + Top10 文档。
     *
     * @return 看板视图
     */
    StatsOverviewVO overview();

    /**
     * 访问日志分页（时间倒序）。
     *
     * @param current 页码
     * @param size    每页条数
     * @return 访问日志分页
     */
    PageResult<AccessLogVO> pageAccessLogs(long current, long size);
}
