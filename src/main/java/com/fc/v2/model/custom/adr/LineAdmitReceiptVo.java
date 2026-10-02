package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 新线进门回执。三条分界数非写不可、空着起不来；比值报成负数、报成中心划的那段以外的数，
 * 一样不进账。回执必须指名空在哪一栏、越在哪一栏——光回一句「数不像话」不算交代。
 *
 * <p>拒收时一行不落库、不返回 id；去年那种「照样保存得进去、又照样通过求值」的路今年堵死。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "LineAdmitReceiptVo", description = "新线进门回执")
public class LineAdmitReceiptVo implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "是否准入(true=已落账,给出id)")
    private boolean accepted;

    @ApiModelProperty(value = "落账后的线主键(拒收为null)")
    private Long ruleId;

    @ApiModelProperty(value = "空着的栏(指名道姓,如「低界比值(th1_max)」)")
    private List<String> blankFields = new ArrayList<>();

    @ApiModelProperty(value = "越段的栏(指名道姓,含越到什么数)")
    private List<String> outOfRangeFields = new ArrayList<>();

    @ApiModelProperty(value = "回执全文(逐栏点名,拒收时给说法)")
    private String message;

    public static LineAdmitReceiptVo ok(Long ruleId) {
        LineAdmitReceiptVo vo = new LineAdmitReceiptVo();
        vo.accepted = true;
        vo.ruleId = ruleId;
        vo.message = "准入";
        return vo;
    }

    public static LineAdmitReceiptVo reject(List<String> blankFields, List<String> outOfRangeFields) {
        LineAdmitReceiptVo vo = new LineAdmitReceiptVo();
        vo.accepted = false;
        vo.blankFields.addAll(blankFields);
        vo.outOfRangeFields.addAll(outOfRangeFields);
        StringBuilder sb = new StringBuilder("不进账：");
        List<String> all = new ArrayList<>();
        all.addAll(blankFields);
        all.addAll(outOfRangeFields);
        sb.append(String.join("；", all));
        vo.message = sb.toString();
        return vo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public List<String> getBlankFields() {
        return blankFields;
    }

    public void setBlankFields(List<String> blankFields) {
        this.blankFields = blankFields;
    }

    public List<String> getOutOfRangeFields() {
        return outOfRangeFields;
    }

    public void setOutOfRangeFields(List<String> outOfRangeFields) {
        this.outOfRangeFields = outOfRangeFields;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
