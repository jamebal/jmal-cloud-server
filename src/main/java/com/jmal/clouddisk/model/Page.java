package com.jmal.clouddisk.model;
import cn.hutool.core.util.PageUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

/**
 * @author jmal
 * @Description 分页数据结果集
 * @Date 2020/11/18 10:32 上午
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class Page<T> extends ArrayList<T> {

    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 页码，0表示第一页
     */
    private int page;
    /**
     * 每页结果数
     */
    private int pageSize;
    /**
     * 总页数
     */
    private int totalPage;
    /**
     * 总数
     */
    private int total;

    private T data;

    //---------------------------------------------------------- Constructor start

    /**
     * 构造
     */
    public Page() {
        this(0, DEFAULT_PAGE_SIZE);
    }

    /**
     * 构造
     *
     * @param page     页码，0表示第一页
     * @param pageSize 每页结果数
     */
    public Page(int page, int pageSize) {
        super(pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize);

        this.page = Math.max(page, 0);
        this.pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
    }

    /**
     * 构造
     *
     * @param page     页码，0表示第一页
     * @param pageSize 每页结果数
     * @param total    结果总数
     */
    public Page(int page, int pageSize, int total) {
        this(page, pageSize);

        this.total = total;
        this.totalPage = PageUtil.totalPage(total, pageSize);
    }

    /**
     * 构造
     *
     * @param total    结果总数
     */
    public Page(int total) {
        this(0, total);

        this.total = total;
        this.totalPage = PageUtil.totalPage(total, pageSize);
    }
    //---------------------------------------------------------- Constructor end

    /**
     * @return 是否第一页
     */
    public boolean isFirst() {
        return this.page == 0;
    }

    /**
     * @return 是否最后一页
     */
    public boolean isLast() {
        return this.page >= (this.totalPage - 1);
    }

    /**
     * @return 当前页码
     */
    public int getCurrentPage() {
        return this.page + 1;
    }

    /**
     * 分页彩虹算法<br>
     * 来自：https://github.com/iceroot/iceroot/blob/master/src/main/java/com/icexxx/util/IceUtil.java<br>
     * 通过传入的信息，生成一个分页列表显示
     * @return 分页条
     */
    public int[] rainbow() {
        return rainbow(5);
    }

    /**
     * 分页彩虹算法<br>
     * 来自：https://github.com/iceroot/iceroot/blob/master/src/main/java/com/icexxx/util/IceUtil.java<br>
     * 通过传入的信息，生成一个分页列表显示
     * @param displayCount 每屏展示的页数
     * @return 分页条
     */
    public int[] rainbow(int displayCount) {
        return PageUtil.rainbow(this.page + 1, this.totalPage, displayCount);
    }

}
