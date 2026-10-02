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
import java.util.Date;

/**
 * 评价结论逐级复核签认单·签迹对象 t_adr_review_sign
 *
 * 一笔签迹就是一道口里落下的一个名。屏上摆着的「已落名数」不作数，
 * 本口实际落了几名，由服务顺着这张签迹表一笔一笔点出来。
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_adr_review_sign")
@ApiModel(value = "TAdrReviewSign", description = "评价结论签认单签迹")
public class TAdrReviewSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 签认单号（跟着单头 bill_no 走） */
    @TableField("bill_no")
    @ApiModelProperty(value = "签认单号")
    private String billNo;

    /** 第几趟 1起（省审定退档整批复签，趟次加一；退档那批与新补那批各归各堆） */
    @TableField("round_no")
    @ApiModelProperty(value = "趟次 1起")
    private Integer roundNo;

    /** 道次 0县初核 1市复核 2省审定 */
    @TableField("node_no")
    @ApiModelProperty(value = "道次 0县初核 1市复核 2省审定")
    private Integer nodeNo;

    /** 本口并署办法 0任一人即过 1两名点齐（落笔定死，不许事后改口） */
    @TableField("sign_mode")
    @ApiModelProperty(value = "本口并署办法 0任一人 1两名点齐")
    private Integer signMode;

    /** 落名的人（跟这一趟走，办一次续一名；与立单经手人各归各栏） */
    @TableField("signer")
    @ApiModelProperty(value = "落名的人")
    private String signer;

    /** 这一笔是哪一口落下的 0签认 1打回 */
    @TableField("action_kind")
    @ApiModelProperty(value = "落名口 0签认 1打回")
    private Integer actionKind;

    /** 签认附言（打回缘由也落在这里） */
    @TableField("comment")
    @ApiModelProperty(value = "签认附言")
    private String comment;

    /** 删除标记 0在册 1退档沉底（整批退档一句不毁，只沉到底层，不与新补的并列） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0在册 1退档沉底")
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

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public Integer getActionKind() {
        return actionKind;
    }

    public void setActionKind(Integer actionKind) {
        this.actionKind = actionKind;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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
