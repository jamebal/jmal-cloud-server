package com.jmal.clouddisk.office.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * @author jmal
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Track implements Reflective {

    /**
     * 定义用户对文档进行操作时接收的对象数组。
     * <p>
     * type 字段的值可以是以下值：
     * <ul>
     *     <li>0 - 用户断开文档协同编辑；</li>
     *     <li>1 - 新用户连接到文档协同编辑；</li>
     *     <li>2 - 用户点击强制保存按钮。</li>
     * </ul>
     * userid 字段的值是用户标识符。
     * 类型：对象数组
     */
    private List<Action> actions;

    /**
     * 定义了包含文档编辑数据的文件链接，用于跟踪和显示文档的更改历史。
     * 仅当状态值等于 2、3、6 或 7 时，此链接才存在。
     * 该文件必须已保存，并且其地址必须通过 {@code setHistoryData} 方法作为 {@code changesUrl} 参数发送，
     * 以便显示与特定文档版本对应的更改。
     */
    private String changesurl;

    /**
     * 定义从{@code url}参数指定链接下载的文档的文件类型。
     * 默认情况下，文件类型为OOXML。但如果服务器设置{@code assemblyFormatAsOrigin}被启用，文件将以其原始格式保存。
     */
    private String filetype;

    /**
     * 强制保存类型。
     * 定义强制保存请求执行时的发起方类型或触发方式。
     * <p>
     * 可选值如下：
     * <ul>
     *     <li>0: 强制保存请求发送至命令服务。</li>
     *     <li>1: 每次执行保存操作时触发（例如点击保存按钮）。此选项仅在 {@code forcesave} 选项为 {@code true} 时可用。</li>
     *     <li>2: 通过定时器触发，设置来源于服务器配置。</li>
     *     <li>3: 每次表单提交时触发（例如点击“完成并提交”按钮）。</li>
     * </ul>
     * <p>
     * <b>注意:</b> 此字段仅在 {@code status} 值为 6 或 7 时有效。
     */
    private Integer forcesavetype;

    /**
     * 定义编辑文档标识符。
     */
    private String key;

    /**
     * 文档的当前状态。
     * 可选值：
     * <ul>
     *     <li>1 - 文档正在编辑中；</li>
     *     <li>2 - 文档已准备好保存；</li>
     *     <li>3 - 文档保存时发生错误；</li>
     *     <li>4 - 文档已关闭且未做任何更改；</li>
     *     <li>6 - 文档正在编辑中，但当前状态已保存；</li>
     *     <li>7 - 强制保存文档时发生错误。</li>
     * </ul>
     */
    private Integer status;

    /**
     * 定义了指向已编辑文档的链接，该链接将随文档一同保存到文档存储服务。
     * 仅当状态值等于2、3、6或7时，此链接才存在。
     * 类型：字符串。
     */
    private String url;

    /**
     * 表示发送给命令服务的自定义信息，用于 forcesave 和 info 命令，当请求中存在时。
     */
    private String userdata;

    /**
     * 定义了打开文档进行编辑的用户标识符列表。
     * 当文档被修改时，此字段将返回最后编辑文档的用户标识符（适用于状态2和状态6的回复）。
     * <p>
     * 类型：字符串数组。
     * 必需参数。
     * <p>
     * 服务器存储所有回调URL，并根据执行操作的用户选择使用哪个回调URL。
     * 自版本5.5起，callbackUrl根据请求状态选择。
     * 从版本4.4到版本5.5，callbackUrl使用最后加入协同编辑的用户。
     * 在版本4.4之前，协同编辑时，callbackUrl使用第一个打开文件进行编辑的用户。
     * <p>
     * 自版本7.0起，callbackUrl使用同一用户的最后一个标签页。
     * 在版本7.0之前，使用第一个用户标签页的callbackUrl。
     */
    private List<String> users;

    private String token;

    private String lastsave;
    private Boolean notmodified;
    private String fileId;
}
