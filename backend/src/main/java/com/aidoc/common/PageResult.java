package com.aidoc.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页返回体 PageResult&lt;T&gt;。
 *
 * <p>使用约定：各 Service 先用 BeanUtils.copyProperties 把实体 List 转成 VO List，
 * 再调用 {@link #of(IPage, List)} 组装分页结果返回给前端。</p>
 *
 * @param <T> 记录元素类型（一般为 VO）
 */
@Data
public class PageResult<T> {

    /** 当前页记录列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    /**
     * 从 MyBatis-Plus 分页对象构造分页返回体。
     *
     * @param page    分页查询返回的 IPage 对象（total/current/size/pages 取自它）
     * @param records 已由实体转换好的 VO 记录列表
     * @param <T>     记录元素类型
     * @return 分页返回体
     */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
