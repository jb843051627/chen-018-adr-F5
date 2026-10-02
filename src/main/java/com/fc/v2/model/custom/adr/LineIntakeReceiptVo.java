package com.fc.v2.model.custom.adr;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TAdrSignalLine;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 限值线进门回执：三条分界数非写不可，空着起不来；比值报成负数、
 * 报成中心划的那一段以外的数，这条线不进账。回执逐栏指名——
 * 空在哪一栏、越在哪一栏，光回一句"数不像话"不算交代。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "LineIntakeReceiptVo", description = "限值线进门回执")
public class LineIntakeReceiptVo {

    @ApiModelProperty(value = "true=这条线进账了；false=拦在门外，库里没它的账")
    private boolean accepted;

    @ApiModelProperty(value = "逐栏指名的问题清单（空在哪一栏、越在哪一栏）")
    private List<String> faults = new ArrayList<>();

    @ApiModelProperty(value = "进账的那条线（被拦时为 null）")
    private TAdrSignalLine line;

    @ApiModelProperty(value = "回执一句话")
    private String message;

    public LineIntakeReceiptVo() {
    }

    /** 进账 */
    public static LineIntakeReceiptVo accepted(TAdrSignalLine line) {
        LineIntakeReceiptVo r = new LineIntakeReceiptVo();
        r.accepted = true;
        r.line = line;
        r.message = "进账";
        return r;
    }

    /** 拦在门外：faults 里逐栏指名 */
    public static LineIntakeReceiptVo rejected(List<String> faults) {
        LineIntakeReceiptVo r = new LineIntakeReceiptVo();
        r.accepted = false;
        r.faults = faults == null ? new ArrayList<String>() : faults;
        r.message = "这条线不进账：" + String.join("；", r.faults);
        return r;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public List<String> getFaults() {
        return faults;
    }

    public void setFaults(List<String> faults) {
        this.faults = faults;
    }

    public TAdrSignalLine getLine() {
        return line;
    }

    public void setLine(TAdrSignalLine line) {
        this.line = line;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
