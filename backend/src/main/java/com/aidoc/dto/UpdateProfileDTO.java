package com.aidoc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求 DTO（PUT /api/user/profile）。
 *
 * <p>三个字段均可空（仅更新非空字段；空串视为清空昵称 / 邮箱 / 头像）。</p>
 */
@Data
public class UpdateProfileDTO {

    /** 昵称（最长 20 字） */
    @Size(max = 20)
    private String nickname;

    /** 邮箱（格式须合法） */
    @Email
    private String email;

    /** 头像 URL（最长 255） */
    @Size(max = 255)
    private String avatar;
}
