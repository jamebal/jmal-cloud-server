package com.jmal.clouddisk.office.callbacks;

/**
 * @author jmal
 * @Description 文件状态
 * @date 2022/8/11 16:29
 */
public enum Status {
    /**
     *  正在编辑文件
     */
    EDITING(1),
    /**
     *  文件已准备好保存
     */
    SAVE(2),
    /**
     * 发生文件保存错误
     */
    CORRUPTED(3),
    /**
     * 退出编辑但是什么都没做
     */
    EDIT_NOTHING(4),
    /**
     * 正在编辑文档，但保存了当前文档状态
     */
    MUST_FORCE_SAVE(6),
    /**
     * 强制保存文档时发生错误
     */
    CORRUPTED_FORCE_SAVE(7);
    private final int code;
    Status(int code){
        this.code = code;
    }
    public int getCode(){
        return this.code;
    }
}
