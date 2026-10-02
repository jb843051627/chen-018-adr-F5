package com.fc.v2.service.cycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSysNoticeMapper;
import com.fc.v2.mapper.auto.TSysNoticeUserMapper;
import com.fc.v2.mapper.auto.TSysUserMapper;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.model.auto.TSysNoticeUser;
import com.fc.v2.model.auto.TSysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 头一条道：到值守人的站内消息。
 *
 * <p>落一张公告并挂到值守人名下（未读）；这一笔落不下来，短信息那条不顶数。
 * 通道内部绊住只回未送达回证，不外抛，免得掐断整轮。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Component
public class StationMessageChannel implements CycleNotifyChannel {

    private static final Logger logger = LoggerFactory.getLogger(StationMessageChannel.class);

    /** 公告类型：催送（站内消息） */
    private static final int NOTICE_TYPE_CYCLE = 9;
    /** 未读 */
    private static final int STATE_UNREAD = 0;

    @javax.annotation.Resource
    private TSysUserMapper userMapper;
    @javax.annotation.Resource
    private TSysNoticeMapper noticeMapper;
    @javax.annotation.Resource
    private TSysNoticeUserMapper noticeUserMapper;

    @Override
    public String code() {
        return "station";
    }

    @Override
    public NotifyReceipt send(TAdrCycleTask item, String content, Date at) {
        String watcher = item.getWatcherNo();
        if (watcher == null || watcher.trim().isEmpty()) {
            return NotifyReceipt.fail(null, "值守人那一栏空着，站内消息无处可投");
        }
        try {
            TSysUser user = this.userMapper.selectOne(new QueryWrapper<TSysUser>()
                    .eq("username", watcher).last("limit 1"));
            if (user == null) {
                return NotifyReceipt.fail(watcher, "值守人「" + watcher + "」查无此人");
            }
            TSysNotice notice = new TSysNotice();
            notice.setTitle("定期报告催送：" + item.getItemNo());
            notice.setContent(content);
            notice.setType(NOTICE_TYPE_CYCLE);
            this.noticeMapper.insert(notice);

            TSysNoticeUser link = new TSysNoticeUser();
            link.setNoticeId(notice.getId());
            link.setUserId(user.getId());
            link.setState(STATE_UNREAD);
            this.noticeUserMapper.insert(link);
            return NotifyReceipt.ok(at, watcher);
        } catch (Exception e) {
            logger.warn("催送站内消息没落账 item={} watcher={}", item.getItemNo(), watcher, e);
            return NotifyReceipt.fail(watcher, "站内消息落账绊住：" + e.getMessage());
        }
    }
}
