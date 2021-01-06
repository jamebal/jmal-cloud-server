package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * @Description 评论
 * @Author jmal
 * @Date 2020-04-02 10:25
 */
@Data
@Validated
public class CommentDO {
    @Id
    String id;

    /***
     * 文件Id
     */
    String fileId;

    /***
     * 评论者邮箱 必填
     */
    @Email(message = "邮箱格式不正确")
    String senderEmail;

    /***
     * 评论者
     */
    @NotNull(message = "名称不能为空")
    String sender;

    /***
     * 评论内容
     */
    @NotNull(message = "内容不能为空")
    String content;

    /***
     * 评论时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime date;

    /***
     * 评论更新时间 当该评论有新回复时会更新该时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime updateDate;

    /***
     * 评论楼层
     */
    @NotNull(message = "评论楼层不能为空")
    Integer floor;

    /***
     * 评论父层
     */
    Integer parentFloor;

    /***
     * 评论者Id 如果未登录则为空
     */
    String senderId;

    /***
     * 接收者
     */
    @NotNull(message = "接收者不能为空")
    String receiver;

    /***
     * 接收者Id 如果该接收者评论时未登录则为空
     */
    String receiverId;

    /***
     * 接收者邮箱
     */
    @Email(message = "邮箱格式不正确")
    String receiverEmail;
}
