package com.fc.v2.model.custom.adr;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 信号求值结论：指定的那一刻在场的是哪一条线、该取哪一册的界，全在这一纸结论上。
 *
 * <p>钉死的规矩：结论一旦落定，线代号与启用之日跟着压死在结果上，
 * 事后换线不改史——页面不许自己再请一条线贴一个答案，代号/启用之日只认本结论给回的。
 * 一条都够不着时不许兜底：{@link #available} 为 false、文案写「无线可依」，
 * 代号与启用之日两栏留空，不拿邻近那条的数凑。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "SignalVerdictVo", description = "信号求值结论(当场钉死)")
public class SignalVerdictVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 一条都够不着时的统一文案，全辖一处口径 */
    public static final String NO_LINE_LABEL = "无线可依";

    @ApiModelProperty(value = "是否有线可依(false=无线可依,代号与启用之日留空)")
    private boolean available;

    @ApiModelProperty(value = "文案:档位 / 无线可依")
    private String label;

    @ApiModelProperty(value = "落定档位 1低界内 2中界内 3高界内 4超高界;无线可依/入算越界为0")
    private int level;

    @ApiModelProperty(value = "钉死的线代号(无线可依留空)")
    private String ruleCode;

    @ApiModelProperty(value = "钉死的线名")
    private String ruleName;

    @ApiModelProperty(value = "钉死的启用之日(无线可依留空)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    @ApiModelProperty(value = "当场该取的低界(取自钉死的那条,页面不得另请一条)")
    private BigDecimal th1Max;

    @ApiModelProperty(value = "当场该取的中界")
    private BigDecimal th2Max;

    @ApiModelProperty(value = "当场该取的高界")
    private BigDecimal th3Max;

    /** 无线可依结论：代号与启用之日两栏留空，不兜底、不凑数 */
    public static SignalVerdictVo noLine() {
        SignalVerdictVo vo = new SignalVerdictVo();
        vo.available = false;
        vo.level = 0;
        vo.label = NO_LINE_LABEL;
        return vo;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public BigDecimal getTh1Max() {
        return th1Max;
    }

    public void setTh1Max(BigDecimal th1Max) {
        this.th1Max = th1Max;
    }

    public BigDecimal getTh2Max() {
        return th2Max;
    }

    public void setTh2Max(BigDecimal th2Max) {
        this.th2Max = th2Max;
    }

    public BigDecimal getTh3Max() {
        return th3Max;
    }

    public void setTh3Max(BigDecimal th3Max) {
        this.th3Max = th3Max;
    }
}
