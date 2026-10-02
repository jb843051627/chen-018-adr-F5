package com.fc.v2.model.custom.adr;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 信号检出求值结论：服务层求值入口一次给回的那一笔。
 * 结论一旦落定，线代号与启用之日跟着压死在这笔结果上，事后换线不改史；
 * 一条都够不着时写明"无线可依"，代号与启用之日两栏留空，不拿邻近那条的数凑。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "SignalVerdictVo", description = "信号检出求值结论")
public class SignalVerdictVo {

    /** 无线可依时的固定说法 */
    public static final String MSG_NO_LINE = "无线可依";

    @ApiModelProperty(value = "档位 1低 2中 3高 4超高界；0=未出档（无线可依/比值越段）")
    private int level;

    @ApiModelProperty(value = "钉死在结论上的线代号（无线可依时留空）")
    private String ruleCode;

    @ApiModelProperty(value = "钉死在结论上的启用之日（无线可依时留空）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    @ApiModelProperty(value = "true=一条都够不着，无线可依")
    private boolean noLine;

    @ApiModelProperty(value = "判定一句话（无线可依/比值越出中心划段等）")
    private String message;

    public SignalVerdictVo() {
    }

    /** 在场那条线说话：钉上代号与启用之日，给出档位 */
    public static SignalVerdictVo of(String ruleCode, Date effStart, int level) {
        SignalVerdictVo v = new SignalVerdictVo();
        v.ruleCode = ruleCode;
        v.effStart = effStart == null ? null : new Date(effStart.getTime());
        v.level = level;
        v.noLine = false;
        v.message = "依线判档";
        return v;
    }

    /** 一条都够不着：两栏留空，不许拿邻近那条的数凑一个 */
    public static SignalVerdictVo noLine() {
        SignalVerdictVo v = new SignalVerdictVo();
        v.level = 0;
        v.noLine = true;
        v.message = MSG_NO_LINE;
        return v;
    }

    /** 求值不成立（如比值越出中心划的那一段）：同样不硬给结论 */
    public static SignalVerdictVo invalid(String message) {
        SignalVerdictVo v = new SignalVerdictVo();
        v.level = 0;
        v.noLine = false;
        v.message = message;
        return v;
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

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public boolean isNoLine() {
        return noLine;
    }

    public void setNoLine(boolean noLine) {
        this.noLine = noLine;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
