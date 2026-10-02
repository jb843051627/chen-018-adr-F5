package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 定期报告催送单对象 t_adr_cycle_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_adr_cycle_task")
@ApiModel(value = "TAdrCycleTask", description = "定期报告催送单")
public class TAdrCycleTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 定期报告催送单号 */
    @TableField("item_no")
    @ApiModelProperty(value = "定期报告催送单号")
    private String itemNo;

    /** 被催的是哪家报告单位（底册代号；岁末半个月同一单位一轮只认头一次落地） */
    @TableField("site_no")
    @ApiModelProperty(value = "被催的报告单位底册代号")
    private String siteNo;

    /** 事由属于哪一类 */
    @TableField("biz_type")
    @ApiModelProperty(value = "事由类别")
    private String bizType;

    /** 应当出手那一日的止点时刻（含当日最后一刻） */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "应当出手那一日的止点时刻")
    private Date dueAt;

    /** 可提前几日开口催送（该出手那一日不许为凑数往后推，能调的只有这一栏） */
    @TableField("amount")
    @ApiModelProperty(value = "可提前几日开口催送")
    private BigDecimal amount;

    /** 事由与办法照哪一份写（文书代号） */
    @TableField("ref_doc")
    @ApiModelProperty(value = "事由与办法照哪一份写")
    private String refDoc;

    /** 联系人短信号（整栏空着则就地转催不成） */
    @TableField("contact_mobile")
    @ApiModelProperty(value = "联系人短信号")
    private String contactMobile;

    /** 值守人代号（站内消息收信人） */
    @TableField("watcher_no")
    @ApiModelProperty(value = "值守人代号")
    private String watcherNo;

    /** 真正催出去那一刻（与情形改成已催出同一笔钉上；撤回不许盖掉） */
    @TableField("sent_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "真正催出去那一刻")
    private Date sentAt;

    /** 催不成缘故（退出底册/联系人空着/逾期撤回等，跟着落在本条） */
    @TableField("fail_reason")
    @ApiModelProperty(value = "催不成缘故")
    private String failReason;

    /** 撤回那一刻（逾期未催出撤回转催不成的留痕；不盖 dueAt/sentAt） */
    @TableField("withdraw_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "撤回那一刻")
    private Date withdrawAt;

    /** 事由与被催单位记要 */
    @TableField("content")
    @ApiModelProperty(value = "事由与被催单位记要")
    private String content;

    /** 条目情形 0候催 1已催出 2催不成 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0候催 1已催出 2催不成")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRefDoc() {
        return refDoc;
    }

    public void setRefDoc(String refDoc) {
        this.refDoc = refDoc;
    }

    public String getContactMobile() {
        return contactMobile;
    }

    public void setContactMobile(String contactMobile) {
        this.contactMobile = contactMobile;
    }

    public String getWatcherNo() {
        return watcherNo;
    }

    public void setWatcherNo(String watcherNo) {
        this.watcherNo = watcherNo;
    }

    public Date getSentAt() {
        return sentAt;
    }

    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Date getWithdrawAt() {
        return withdrawAt;
    }

    public void setWithdrawAt(Date withdrawAt) {
        this.withdrawAt = withdrawAt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
