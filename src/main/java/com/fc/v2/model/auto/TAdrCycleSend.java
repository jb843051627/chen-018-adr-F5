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
 * 催送轮逐桩台账对象 t_adr_cycle_send
 *
 * <p>服务器每回执行算定哪些该动，一桩落一行；屏上逐条点出来的与候催/已催出/催不成
 * 三条数同源于此，不许一头数一头估。
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_adr_cycle_send")
@ApiModel(value = "TAdrCycleSend", description = "催送轮逐桩台账")
public class TAdrCycleSend extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 轮次号（同一回执行一个号，同时刻并进的也同号） */
    @TableField("round_no")
    @ApiModelProperty(value = "轮次号")
    private String roundNo;

    /** 催送单条目id */
    @TableField("item_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "催送单条目id")
    private Long itemId;

    /** 催送单号（冗余留痕） */
    @TableField("item_no")
    @ApiModelProperty(value = "催送单号")
    private String itemNo;

    /** 被催单位底册代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "被催单位底册代号")
    private String siteNo;

    /** 本桩落点 sent已催出 withheld压住 failed催不成 skipped逾期撤回转催不成 */
    @TableField("action")
    @ApiModelProperty(value = "本桩落点")
    private String action;

    /** 该桩落定后条目情形 0候催 1已催出 2催不成 */
    @TableField("status_after")
    @ApiModelProperty(value = "该桩落定后条目情形")
    private Integer statusAfter;

    /** 本桩动作那一刻（已催出即催出时刻） */
    @TableField("acted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本桩动作那一刻")
    private Date actedAt;

    /** 压住缘故/催不成缘故等 */
    @TableField("note")
    @ApiModelProperty(value = "压住缘故/催不成缘故等")
    private String note;

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

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getStatusAfter() {
        return statusAfter;
    }

    public void setStatusAfter(Integer statusAfter) {
        this.statusAfter = statusAfter;
    }

    public Date getActedAt() {
        return actedAt;
    }

    public void setActedAt(Date actedAt) {
        this.actedAt = actedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
