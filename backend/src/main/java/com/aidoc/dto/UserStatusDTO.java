package com.aidoc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用 / 禁用用户请求 DTO（PUT /api/admin/user/{id}/status）。
 *
 * <p>status 取值：1 正常、0 禁用（禁用时管理模块需将用户踢下线）。</p>
 */
@Data
public class UserStatusDTO {

    /** 目标状态（0/1） */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
