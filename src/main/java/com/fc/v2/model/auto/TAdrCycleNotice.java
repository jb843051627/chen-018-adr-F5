package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fc.v2.common.base.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 催送通知送达账对象 t_adr_cycle_notice
 *
 * <p>一条催出走站内消息、短信息两条道，各记各的送达；头一条没落不许拿第二条顶数。
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_adr_cycle_notice")
@ApiModel(value = "TAdrCycleNotice", description = "催送通知送达账")
public class TAdrCycleNotice extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属逐桩台账行 */
    @TableField("send_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所属逐桩台账行")
    private Long sendId;

    /** 催送单条目id */
    @TableField("item_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "催送单条目id")
    private Long itemId;

    /** 通道 station站内消息 sms短信息 */
    @TableField("channel")
    @ApiModelProperty(value = "通道 station站内消息 sms短信息")
    private String channel;

    /** 送达情形 0未送达 1已送达 */
    @TableField("delivered")
    @ApiModelProperty(value = "送达情形 0未送达 1已送达")
    private Integer delivered;

    /** 送达那一刻 */
    @TableField("delivered_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "送达那一刻")
    private Date deliveredAt;

    /** 收信目标（值守人代号/短信号） */
    @TableField("target")
    @ApiModelProperty(value = "收信目标")
    private String target;

    /** 没送达时的缘故 */
    @TableField("fail_note")
    @ApiModelProperty(value = "没送达时的缘故")
    private String failNote;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSendId() {
        return sendId;
    }

    public void setSendId(Long sendId) {
        this.sendId = sendId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getDelivered() {
        return delivered;
    }

    public void setDelivered(Integer delivered) {
        this.delivered = delivered;
    }

    public Date getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Date deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getFailNote() {
        return failNote;
    }

    public void setFailNote(String failNote) {
        this.failNote = failNote;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
