package com.fc.v2.service.cycle;

import com.fc.v2.model.auto.TAdrCycleTask;

import java.util.Date;

/**
 * 催送通知通道：一条催出走两条道（站内消息、短信息），两条各记各的送达。
 *
 * <p>换网关只换本口实现，催送轮算法不跟着动；通道内部绊住只回「未送达＋缘故」，
 * 不许把整轮掐断。
 *
 * @author fuce
 * @date 2026-10-01
 */
public interface CycleNotifyChannel {

    /** 通道代号：station 站内消息 / sms 短信息 */
    String code();

    /** 发一条催送通知；发不出去也照实回，不抛外 */
    NotifyReceipt send(TAdrCycleTask item, String content, Date at);

    /** 送达回证 */
    class NotifyReceipt {
        private final boolean delivered;
        private final Date deliveredAt;
        private final String target;
        private final String failNote;

        private NotifyReceipt(boolean delivered, Date deliveredAt, String target, String failNote) {
            this.delivered = delivered;
            this.deliveredAt = deliveredAt;
            this.target = target;
            this.failNote = failNote;
        }

        public static NotifyReceipt ok(Date at, String target) {
            return new NotifyReceipt(true, at, target, null);
        }

        public static NotifyReceipt fail(String target, String failNote) {
            return new NotifyReceipt(false, null, target, failNote);
        }

        public boolean isDelivered() {
            return delivered;
        }

        public Date getDeliveredAt() {
            return deliveredAt;
        }

        public String getTarget() {
            return target;
        }

        public String getFailNote() {
            return failNote;
        }
    }
}
