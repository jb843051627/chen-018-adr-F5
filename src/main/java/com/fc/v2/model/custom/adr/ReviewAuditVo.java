package com.fc.v2.model.custom.adr;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 签认单倒读核对结果：最终定稿那一版，当日留在签认档里的记载要从省审定一路往回捋到
 * 县初核，各级签过的姓名逐条清点，点出来的总数与当天屏上那一份对得拢；
 * 单拿出任意一道，也经得起复核。
 *
 * @author fuce
 * @date 2026-10-01
 */
@ApiModel(value = "ReviewAuditVo", description = "签认单倒读核对结果")
public class ReviewAuditVo {

    @ApiModelProperty(value = "倒读是否对得拢（true=这一单走成了）")
    private boolean passed;

    @ApiModelProperty(value = "判定一句话（对不上时点出伤在哪一道）")
    private String message;

    @ApiModelProperty(value = "各道在册名数，下标即道次 0县 1市 2省")
    private List<Integer> nodeCounts = new ArrayList<>();

    @ApiModelProperty(value = "三道口在册姓名总数")
    private int totalCount;

    public ReviewAuditVo() {
    }

    public ReviewAuditVo(boolean passed, String message, List<Integer> nodeCounts) {
        this.passed = passed;
        this.message = message;
        this.nodeCounts = nodeCounts == null ? new ArrayList<>() : nodeCounts;
        int sum = 0;
        for (Integer c : this.nodeCounts) {
            sum += c == null ? 0 : c;
        }
        this.totalCount = sum;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Integer> getNodeCounts() {
        return nodeCounts;
    }

    public void setNodeCounts(List<Integer> nodeCounts) {
        this.nodeCounts = nodeCounts;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
