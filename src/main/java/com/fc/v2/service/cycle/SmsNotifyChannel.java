package com.fc.v2.service.cycle;

import com.fc.v2.model.auto.TAdrCycleTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 第二条道：发短信息到条目联系人那一栏的短信号。
 *
 * <p>两条道各记各的送达：站内消息头一条没落，不许拿短信顶数。网关走 {@link CycleSmsGateway}，
 * 缺省只落日志的占位网关（系统里还没有真网关），接入时另挂同名实现替换。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Component
public class SmsNotifyChannel implements CycleNotifyChannel {

    private static final Logger logger = LoggerFactory.getLogger(SmsNotifyChannel.class);

    @javax.annotation.Resource
    private CycleSmsGateway smsGateway;

    @Override
    public String code() {
        return "sms";
    }

    @Override
    public NotifyReceipt send(TAdrCycleTask item, String content, Date at) {
        String mobile = item.getContactMobile();
        if (mobile == null || mobile.trim().isEmpty()) {
            return NotifyReceipt.fail(null, "联系人短信号那一栏整季空着，短信无处可发");
        }
        try {
            boolean ok = this.smsGateway.send(mobile, content);
            if (ok) {
                return NotifyReceipt.ok(at, mobile);
            }
            return NotifyReceipt.fail(mobile, "短信网关回了未送达");
        } catch (Exception e) {
            logger.warn("催送短信没发出去 item={} mobile={}", item.getItemNo(), mobile, e);
            return NotifyReceipt.fail(mobile, "短信发送绊住：" + e.getMessage());
        }
    }
}
