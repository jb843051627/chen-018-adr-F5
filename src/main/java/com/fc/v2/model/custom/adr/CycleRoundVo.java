package com.fc.v2.model.custom.adr;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 催送一轮跑完的回话：本轮挑了哪几桩、催出去几条、压住几条、催不成几条，
 * 三数与逐条台账由同一回计算一并交出，不许一头数一头估。
 *
 * @author fuce
 * @date 2026-10-01
 */
public class CycleRoundVo {

    /** 轮次号（同一回执行一个号，同时刻并进的也同号） */
    private String roundNo;
    /** 这一回执行那一刻 */
    private Date at;
    /** 本轮逐条落点（与 t_adr_cycle_send 同序同源，屏上逐条点的就是这些） */
    private List<Item> items = new ArrayList<>();
    /** 压住几条（到期但界外/岁末被同单位头一条挤下，仍候催） */
    private int waitCount;
    /** 催出去几条 */
    private int doneCount;
    /** 催不成几条（绊住当场转的 + 逾期撤回的） */
    private int failCount;

    public static class Item {
        private Long itemId;
        private String itemNo;
        private String siteNo;
        /** sent 已催出 / withheld 压住 / failed 催不成 / skipped 逾期撤回转催不成 */
        private String action;
        private Integer statusAfter;
        private Date actedAt;
        private String note;
        /** 站内消息送达 0/1 */
        private Integer stationDelivered;
        /** 短信息送达 0/1 */
        private Integer smsDelivered;

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

        public Integer getStationDelivered() {
            return stationDelivered;
        }

        public void setStationDelivered(Integer stationDelivered) {
            this.stationDelivered = stationDelivered;
        }

        public Integer getSmsDelivered() {
            return smsDelivered;
        }

        public void setSmsDelivered(Integer smsDelivered) {
            this.smsDelivered = smsDelivered;
        }
    }

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public int getWaitCount() {
        return waitCount;
    }

    public void setWaitCount(int waitCount) {
        this.waitCount = waitCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(int doneCount) {
        this.doneCount = doneCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    /** 本轮一个该动的也没挑着（空轮不等于跑砸了） */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** 三数由同一回逐条落点数出来——只在这里点一遍 */
    public void recount() {
        int w = 0;
        int d = 0;
        int f = 0;
        for (Item it : items) {
            if ("withheld".equals(it.getAction())) {
                w++;
            } else if ("sent".equals(it.getAction())) {
                d++;
            } else {
                f++;
            }
        }
        this.waitCount = w;
        this.doneCount = d;
        this.failCount = f;
    }

    /** 屏上那句回话 */
    public String message() {
        if (isEmpty()) {
            return "本轮无到期条目";
        }
        return "本轮：候催（压住）" + waitCount + "条，已催出" + doneCount + "条，催不成" + failCount + "条";
    }
}
