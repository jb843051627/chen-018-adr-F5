package com.fc.v2.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TAdrCycleCalendarMapper;
import com.fc.v2.mapper.auto.TAdrCycleNoticeMapper;
import com.fc.v2.mapper.auto.TAdrCycleSendMapper;
import com.fc.v2.mapper.auto.TAdrCycleTaskMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.model.auto.TAdrCycleNotice;
import com.fc.v2.model.auto.TAdrCycleSend;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.custom.adr.CycleRoundVo;
import com.fc.v2.service.ITAdrCycleTaskService;
import com.fc.v2.service.cycle.CycleNotifyChannel;
import com.fc.v2.service.cycle.DutyClock;
import com.fc.v2.service.cycle.StationMessageChannel;
import com.fc.v2.service.cycle.SmsNotifyChannel;
import com.fc.v2.util.StringUtils;

/**
 * 定期报告催送单 Service业务层处理（scheduling-job 形状：周期执行）。
 *
 * <p>前年两张人工日历排出来的两笔旧账立的规矩：排三月的漏了一家，整季挂「待提交」没人动；
 * 排年底的把年初催过的又催一遍，单位来函问为什么同一桩事派下两回。今年起由系统照日子跑。
 *
 * <p>一把尺：出手那一日到没到，只看 <b>止点含当日最后一刻、可提前 amount 日开口</b> 这一条，
 * 翻单（{@link #listDue}）与回写（{@link #runRound(Date)}）共用 {@link #isOpen}，两处不许各算各的；
 * 止点不许为凑数往后推，能调的只有提前几日那一栏。
 *
 * <p>另一层是班段：夜里与法定节假日整场不发声，段外到期也只压着候下一轮；岁末半个月
 * （12/16—12/31）另排两段，且同一单位一轮只认头一次落地，当日第二条不入账。
 * 上一轮已催出的，下一轮不再当候催挑回。
 *
 * <p>三数（候催/已催出/催不成）与逐条落点由同一回执行算定，一趟清点一并落库回话；
 * 页面加不进一条也删不掉一条。空轮回「本轮无到期条目」，那不叫跑砸。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrCycleTaskServiceImpl implements ITAdrCycleTaskService {

    /** 条目情形：候催 */
    private static final int STATUS_WAIT = 0;
    /** 条目情形：已催出 */
    private static final int STATUS_DONE = 1;
    /** 条目情形：催不成 */
    private static final int STATUS_FAIL = 2;

    private static final int DEL_NORMAL = 0;
    /** 底册情形：在册 */
    private static final int ORG_ACTIVE = 0;
    /** 底册情形：已退出 */
    private static final int ORG_EXITED = 1;

    private static final String CHANNEL_STATION = "station";
    private static final String CHANNEL_SMS = "sms";

    /** 逐桩落点 */
    private static final String ACT_SENT = "sent";
    private static final String ACT_WITHHELD = "withheld";
    private static final String ACT_FAILED = "failed";
    private static final String ACT_SKIPPED = "skipped";

    @javax.annotation.Resource
    private TAdrCycleTaskMapper adrCycleTaskMapper;
    @javax.annotation.Resource
    private TAdrCycleSendMapper adrCycleSendMapper;
    @javax.annotation.Resource
    private TAdrCycleNoticeMapper adrCycleNoticeMapper;
    @javax.annotation.Resource
    private TAdrOrgArchMapper adrOrgArchMapper;
    @javax.annotation.Resource
    private TAdrCycleCalendarMapper adrCycleCalendarMapper;
    @javax.annotation.Resource
    private DutyClock dutyClock;
    @javax.annotation.Resource
    private StationMessageChannel stationChannel;
    @javax.annotation.Resource
    private SmsNotifyChannel smsChannel;

    @Override
    public TAdrCycleTask selectTAdrCycleTaskById(Long id) {
        return this.adrCycleTaskMapper.selectById(id);
    }

    /**
     * 时段比较走本地墙钟（与 {@link DutyClock} 同一只钟）：止点存的是「当日最后一刻」，
     * 开口起点＝止点那一日零点再往前推 amount 个整日。边界用闭区间，恰好到期算到。
     */
    private static LocalDateTime wall(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }

    /** 可提前几日（null 当 0） */
    private static long earlyDays(TAdrCycleTask r) {
        return r.getAmount() == null ? 0L : r.getAmount().longValue();
    }

    /** 开口起点：止点那一日的零点往前推 amount 日 */
    private static LocalDateTime openFrom(TAdrCycleTask r) {
        return r.getDueAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                .minusDays(earlyDays(r)).atStartOfDay();
    }

    /**
     * 同一把尺：到没到开口窗口（含开口起点、含止点最后一刻）。
     * 翻单与回写都只问这里，别处不得另算。
     */
    private boolean isOpen(TAdrCycleTask r, LocalDateTime at) {
        if (r.getDueAt() == null) {
            return false;
        }
        return !at.isBefore(openFrom(r)) && !at.isAfter(wall(r.getDueAt()));
    }

    /** 止点都过了仍候催的：日子过了 */
    private boolean isOverdue(TAdrCycleTask r, LocalDateTime at) {
        return r.getDueAt() != null && at.isAfter(wall(r.getDueAt()));
    }

    @Override
    public List<TAdrCycleTask> listDue(Date at) {
        LocalDateTime now = wall(at);
        List<TAdrCycleTask> due = new ArrayList<>();
        // 只挑候催的在册条目；已催出/催不成不再回挑——上一轮催出去的不许下一轮当候催捡回来
        for (TAdrCycleTask r : liveWaiting()) {
            if (isOpen(r, now)) {
                due.add(r);
            }
        }
        return due;
    }

    /** 在册且仍候催的条目，按落库先后排（同一刻并进的也有个准次序） */
    private List<TAdrCycleTask> liveWaiting() {
        return this.adrCycleTaskMapper.selectList(new QueryWrapper<TAdrCycleTask>()
                .eq("del_flag", DEL_NORMAL)
                .eq("status", STATUS_WAIT)
                .orderByAsc("id"));
    }

    @Override
    public int runOnce(Date at) {
        // 旧口回话照旧：只回成功催出条数；跑法已是新跑法，单条绊住跳过继续
        return runRound(at).getDoneCount();
    }

    @Override
    public CycleRoundVo runRound() {
        return runRound(new Date());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CycleRoundVo runRound(Date at) {
        LocalDateTime now = wall(at);
        String roundNo = new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS").format(at);
        boolean yearEnd = this.dutyClock.isYearEnd(now.toLocalDate());
        boolean vocal = this.dutyClock.isVocal(now);

        CycleRoundVo vo = new CycleRoundVo();
        vo.setRoundNo(roundNo);
        vo.setAt(at);

        // 岁末去重：同一单位一轮只认头一次落地，先落下的那条原样不动。
        // 同一天里上一轮已经给这家催出过，这一轮进来的第二条同样不入账。
        Set<String> landedSites = new HashSet<>();
        if (yearEnd) {
            LocalDate today = now.toLocalDate();
            Date dayStart = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            for (TAdrCycleSend prev : this.adrCycleSendMapper.selectList(new QueryWrapper<TAdrCycleSend>()
                    .eq("action", ACT_SENT)
                    .ge("acted_at", dayStart)
                    .lt("acted_at", dayEnd))) {
                if (StringUtils.isNotBlank(prev.getSiteNo())) {
                    landedSites.add(prev.getSiteNo());
                }
            }
        }

        // 翻单与回写共用 listDue 这一把尺：这一轮挑哪些，先照它点一遍，
        // 回写只在它点出来的名单上再过班段/去重/绊住三层，两处不许各算各的
        java.util.Set<Long> openIds = new java.util.LinkedHashSet<>();
        for (TAdrCycleTask open : listDue(at)) {
            openIds.add(open.getId());
        }

        for (TAdrCycleTask r : liveWaiting()) {
            CycleRoundVo.Item item;
            if (isOverdue(r, now)) {
                // 日子过了仍未催出去：撤回并转催不成；头回定的日子（due_at）不盖，
                // 没催出过就没有催出时刻，撤回这一刻另钉在 withdraw_at 上
                item = withdrawOverdue(r, roundNo, at);
            } else if (!openIds.contains(r.getId())) {
                // 没到的原地不动：listDue 没点它，不进本轮，屏上不数
                continue;
            } else if (!vocal) {
                // 时段另一层：夜里/法定节假日（岁末另排段外）整场不发声，压着候下一轮开口
                item = withhold(r, roundNo, at, this.dutyClock.silentReason(now));
            } else if (yearEnd) {
                // 岁末半个月：同一单位一轮只认头一次落地——头一条被挑到先占位，
                // 催成、绊住都算落下；当日第二条进来的不入账：不留台账、不点数，
                // 条目原样候催（下一轮头一条已催出，它自然成了头一条）
                if (StringUtils.isNotBlank(r.getSiteNo()) && landedSites.contains(r.getSiteNo())) {
                    continue;
                }
                if (StringUtils.isNotBlank(r.getSiteNo())) {
                    landedSites.add(r.getSiteNo());
                }
                String blocked = blocker(r);
                item = blocked != null ? fail(r, roundNo, at, blocked) : urge(r, roundNo, at);
            } else {
                String blocked = blocker(r);
                if (blocked != null) {
                    // 开单前绊住：就地转催不成，缘故跟着落在这条上；屏上照旧翻得着
                    item = fail(r, roundNo, at, blocked);
                } else {
                    item = urge(r, roundNo, at);
                }
            }
            vo.getItems().add(item);
        }

        // 三数与逐条同一回计算：只在这一处点，屏上数与档里逐条点的对的是同一份
        vo.recount();
        return vo;
    }

    /** 开单前绊住的两种：单位已退出底册（或在册底册查无这家）、联系人整栏空着 */
    private String blocker(TAdrCycleTask r) {
        if (StringUtils.isBlank(r.getContactMobile())) {
            return "联系人那一栏整季空着，催不成";
        }
        if (StringUtils.isBlank(r.getSiteNo())) {
            return "被催单位那一栏空着，底册里对不上，催不成";
        }
        TAdrOrgArch org = this.adrOrgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                .eq("site_no", r.getSiteNo()).eq("del_flag", DEL_NORMAL).last("limit 1"));
        if (org == null) {
            return "单位「" + r.getSiteNo() + "」在册底册里查无记录，催不成";
        }
        if (Integer.valueOf(ORG_EXITED).equals(org.getStatus())) {
            return "单位「" + r.getSiteNo() + "」已经退出底册，催不成";
        }
        return null;
    }

    /**
     * 催成两笔一笔落：情形 0→1 与催出时刻同一笔 update，少一笔都不算成；
     * 两条道各记各的送达，站内消息头一条没落，短信不发、不许顶数。
     */
    private CycleRoundVo.Item urge(TAdrCycleTask r, String roundNo, Date at) {
        String content = notifyContent(r);

        CycleNotifyChannel.NotifyReceipt station = this.stationChannel.send(r, content, at);
        TAdrCycleNotice stationRow = noticeRow(r, CHANNEL_STATION, station, at);

        CycleNotifyChannel.NotifyReceipt sms;
        TAdrCycleNotice smsRow;
        if (!station.isDelivered()) {
            // 头一条没落不许拿第二条顶数：短信这一轮根本不发，照实记一笔未发
            sms = CycleNotifyChannel.NotifyReceipt.fail(r.getContactMobile(),
                    "头一条站内消息没落，不许拿短信息顶数，本轮未发");
            smsRow = noticeRow(r, CHANNEL_SMS, sms, at);
        } else {
            sms = this.smsChannel.send(r, content, at);
            smsRow = noticeRow(r, CHANNEL_SMS, sms, at);
        }

        TAdrCycleSend send = baseSend(r, roundNo);
        CycleRoundVo.Item item = new CycleRoundVo.Item();
        item.setItemId(r.getId());
        item.setItemNo(r.getItemNo());
        item.setSiteNo(r.getSiteNo());
        item.setActedAt(at);

        if (!station.isDelivered()) {
            // 通道绊住不等于条目催不成：仍压在候催，候下一轮再开口，后头的接着跑
            send.setAction(ACT_WITHHELD);
            send.setStatusAfter(STATUS_WAIT);
            send.setActedAt(at);
            send.setNote(clip("站内消息未送达：" + station.getFailNote()));
            this.adrCycleSendMapper.insert(send);
            linkNotices(send, stationRow, smsRow);
            item.setAction(ACT_WITHHELD);
            item.setStatusAfter(STATUS_WAIT);
            item.setNote(send.getNote());
            item.setStationDelivered(0);
            item.setSmsDelivered(0);
            return item;
        }

        // 催成两笔：改情形＋钉催出时刻，同一笔落下
        int upd = this.adrCycleTaskMapper.update(null, new UpdateWrapper<TAdrCycleTask>()
                .eq("id", r.getId())
                .eq("status", STATUS_WAIT)
                .set("status", STATUS_DONE)
                .set("sent_at", at));
        if (upd == 0) {
            // 并发下已被别处动过：不抢写，本轮压着
            send.setAction(ACT_WITHHELD);
            send.setStatusAfter(STATUS_WAIT);
            send.setActedAt(at);
            send.setNote("落库时条目已不是候催，本轮不抢写");
            this.adrCycleSendMapper.insert(send);
            linkNotices(send, stationRow, smsRow);
            item.setAction(ACT_WITHHELD);
            item.setStatusAfter(STATUS_WAIT);
            item.setNote(send.getNote());
            item.setStationDelivered(station.isDelivered() ? 1 : 0);
            item.setSmsDelivered(sms.isDelivered() ? 1 : 0);
            return item;
        }
        send.setAction(ACT_SENT);
        send.setStatusAfter(STATUS_DONE);
        send.setActedAt(at);
        this.adrCycleSendMapper.insert(send);
        linkNotices(send, stationRow, smsRow);

        item.setAction(ACT_SENT);
        item.setStatusAfter(STATUS_DONE);
        item.setStationDelivered(1);
        item.setSmsDelivered(sms.isDelivered() ? 1 : 0);
        return item;
    }

    /** 压住：条目仍是候催，只逐桩台账留一笔缘故 */
    private CycleRoundVo.Item withhold(TAdrCycleTask r, String roundNo, Date at, String reason) {
        TAdrCycleSend send = baseSend(r, roundNo);
        send.setAction(ACT_WITHHELD);
        send.setStatusAfter(STATUS_WAIT);
        send.setActedAt(at);
        send.setNote(reason);
        this.adrCycleSendMapper.insert(send);

        CycleRoundVo.Item item = new CycleRoundVo.Item();
        item.setItemId(r.getId());
        item.setItemNo(r.getItemNo());
        item.setSiteNo(r.getSiteNo());
        item.setAction(ACT_WITHHELD);
        item.setStatusAfter(STATUS_WAIT);
        item.setActedAt(at);
        item.setNote(reason);
        return item;
    }

    /** 催不成（开单前绊住）：就地转情形，缘故跟着落在本条上；条目屏上照旧翻得着 */
    private CycleRoundVo.Item fail(TAdrCycleTask r, String roundNo, Date at, String reasonIn) {
        String reason = clip(reasonIn);
        this.adrCycleTaskMapper.update(null, new UpdateWrapper<TAdrCycleTask>()
                .eq("id", r.getId())
                .eq("status", STATUS_WAIT)
                .set("status", STATUS_FAIL)
                .set("fail_reason", reason));

        TAdrCycleSend send = baseSend(r, roundNo);
        send.setAction(ACT_FAILED);
        send.setStatusAfter(STATUS_FAIL);
        send.setActedAt(at);
        send.setNote(reason);
        this.adrCycleSendMapper.insert(send);

        CycleRoundVo.Item item = new CycleRoundVo.Item();
        item.setItemId(r.getId());
        item.setItemNo(r.getItemNo());
        item.setSiteNo(r.getSiteNo());
        item.setAction(ACT_FAILED);
        item.setStatusAfter(STATUS_FAIL);
        item.setActedAt(at);
        item.setNote(reason);
        return item;
    }

    /**
     * 日子过了仍未催出：撤回并转催不成。撤回这一笔查得着痕迹（withdraw_at＋逐桩 skipped 行），
     * 但头回定的日子 due_at 不许被后一笔盖掉；没催出过，sent_at 也始终空着。
     */
    private CycleRoundVo.Item withdrawOverdue(TAdrCycleTask r, String roundNo, Date at) {
        String reason = "日子过了仍未催出去，撤回并转催不成";
        this.adrCycleTaskMapper.update(null, new UpdateWrapper<TAdrCycleTask>()
                .eq("id", r.getId())
                .eq("status", STATUS_WAIT)
                .set("status", STATUS_FAIL)
                .set("fail_reason", reason)
                .set("withdraw_at", at));

        TAdrCycleSend send = baseSend(r, roundNo);
        send.setAction(ACT_SKIPPED);
        send.setStatusAfter(STATUS_FAIL);
        send.setActedAt(at);
        send.setNote(reason);
        this.adrCycleSendMapper.insert(send);

        CycleRoundVo.Item item = new CycleRoundVo.Item();
        item.setItemId(r.getId());
        item.setItemNo(r.getItemNo());
        item.setSiteNo(r.getSiteNo());
        item.setAction(ACT_SKIPPED);
        item.setStatusAfter(STATUS_FAIL);
        item.setActedAt(at);
        item.setNote(reason);
        return item;
    }

    private TAdrCycleSend baseSend(TAdrCycleTask r, String roundNo) {
        TAdrCycleSend send = new TAdrCycleSend();
        send.setRoundNo(roundNo);
        send.setItemId(r.getId());
        send.setItemNo(r.getItemNo());
        send.setSiteNo(r.getSiteNo());
        send.setDelFlag(DEL_NORMAL);
        return send;
    }

    private TAdrCycleNotice noticeRow(TAdrCycleTask r, String channel,
                                      CycleNotifyChannel.NotifyReceipt rc, Date at) {
        TAdrCycleNotice n = new TAdrCycleNotice();
        n.setItemId(r.getId());
        n.setChannel(channel);
        n.setDelivered(rc.isDelivered() ? 1 : 0);
        n.setDeliveredAt(rc.isDelivered() ? (rc.getDeliveredAt() == null ? at : rc.getDeliveredAt()) : null);
        n.setTarget(rc.getTarget());
        n.setFailNote(rc.isDelivered() ? null : clip(rc.getFailNote()));
        n.setDelFlag(DEL_NORMAL);
        return n;
    }

    private void linkNotices(TAdrCycleSend send, TAdrCycleNotice... rows) {
        for (TAdrCycleNotice n : rows) {
            n.setSendId(send.getId());
            this.adrCycleNoticeMapper.insert(n);
        }
    }

    /** 缘故落库统一截到 250 字（列长 255），通道把整段异常带回来时也不撑爆台账 */
    private static String clip(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 250 ? s.substring(0, 250) : s;
    }

    /** 催送通知正文：事由与办法照条目指的那一份写 */
    private static String notifyContent(TAdrCycleTask r) {
        StringBuilder sb = new StringBuilder();
        sb.append("催送单号 ").append(r.getItemNo() == null ? "" : r.getItemNo());
        if (StringUtils.isNotBlank(r.getSiteNo())) {
            sb.append("：被催单位 ").append(r.getSiteNo());
        }
        if (StringUtils.isNotBlank(r.getBizType())) {
            sb.append("，事由类别「").append(r.getBizType()).append("」");
        }
        if (StringUtils.isNotBlank(r.getRefDoc())) {
            sb.append("，事由与办法照《").append(r.getRefDoc()).append("》办理");
        }
        sb.append("，请按期提交定期安全性更新报告。");
        return sb.length() > 240 ? sb.substring(0, 240) : sb.toString();
    }
}
