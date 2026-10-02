package com.fc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAdrCycleCalendar;
import com.fc.v2.model.auto.TAdrCycleNotice;
import com.fc.v2.model.auto.TAdrCycleSend;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.model.auto.TSysNoticeUser;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.adr.CycleRoundVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定期报告催送轮规矩集成测试——前年两张人工日历排出来的两笔旧账立下的规矩：
 * 漏一家整季挂待提交、同一桩事派下两回；今年起系统照日子跑。
 *
 * 一把尺（止点含当日、可提前开口、翻单回写同源）、两层班段（夜/节假日不发声、岁末另排且
 * 同单位只认头一条）、已催不回挑、两笔落库、两条送达头一条不顶、单条绊住不停全场、
 * 逾期撤回留痕不盖旧时刻、三数与逐条同源、空轮不叫跑砸、并发不错数。
 */
class TAdrCycleTaskServiceImplTest extends CycleTaskTestBase {

    // ---------- 造数 ----------

    private TAdrCycleTask seed(String itemNo, String site, String due, String amount) {
        return seed(itemNo, site, due, amount, "13800000000", "admin");
    }

    private TAdrCycleTask seed(String itemNo, String site, String due, String amount,
                               String mobile, String watcher) {
        TAdrCycleTask t = new TAdrCycleTask();
        t.setId(nextId());
        t.setItemNo(itemNo);
        t.setSiteNo(site);
        t.setBizType("定期安全性更新报告");
        t.setDueAt(d(due));
        t.setAmount(amount == null ? null : new BigDecimal(amount));
        t.setRefDoc("PSUR催送办法2026版");
        t.setContactMobile(mobile);
        t.setWatcherNo(watcher);
        t.setStatus(0);
        t.setDelFlag(0);
        taskMapper.insert(t);
        return t;
    }

    private void seedOrg(String siteNo, int status) {
        TAdrOrgArch org = new TAdrOrgArch();
        org.setId(nextId());
        org.setSiteNo(siteNo);
        org.setSiteName(siteNo + "单位");
        org.setStatus(status);
        org.setDelFlag(0);
        orgMapper.insert(org);
    }

    private void seedWatcher(String username) {
        TSysUser u = new TSysUser();
        u.setId(nextId());
        u.setUsername(username);
        u.setNickname(username);
        userMapper.insert(u);
    }

    private void seedHoliday(String ymd) {
        TAdrCycleCalendar c = new TAdrCycleCalendar();
        c.setId(nextId());
        c.setHoliday(java.sql.Date.valueOf(ymd));
        c.setDayName("假");
        c.setDelFlag(0);
        calendarMapper.insert(c);
    }

    private TAdrCycleTask reload(long id) {
        return taskMapper.selectById(id);
    }

    private List<TAdrCycleSend> sendsOfRound(String roundNo) {
        return sendMapper.selectList(new QueryWrapper<TAdrCycleSend>()
                .eq("round_no", roundNo).orderByAsc("id"));
    }

    private List<TAdrCycleNotice> noticesOfSend(long sendId) {
        return noticeMapper.selectList(new QueryWrapper<TAdrCycleNotice>()
                .eq("send_id", sendId).orderByAsc("id"));
    }

    // ================= 一把尺：到没到 =================

    @Test
    void 没到出手那一日_原地不动() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        // 止点 10 日 23:59:59，提前 0 日；5 日跑，没到
        TAdrCycleTask t = seed("C1", "YF00", "2026-03-10 23:59:59", "0");
        CycleRoundVo vo = service.runRound(d("2026-03-05 10:00:00"));
        assertTrue(vo.isEmpty());
        assertEquals("本轮无到期条目", vo.message());
        assertEquals(0, reload(t.getId()).getStatus().intValue());
        assertEquals(0, sendMapper.selectCount(new QueryWrapper<TAdrCycleSend>()).intValue());
    }

    @Test
    void 提前几日开口_到了窗口就归进本轮() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask t = seed("C2", "YF00", "2026-03-10 23:59:59", "3");
        // 3 月 7 日 00:00 起开口：8 日白班在窗内
        CycleRoundVo vo = service.runRound(d("2026-03-08 10:00:00"));
        assertEquals(1, vo.getDoneCount());
        assertEquals(1, reload(t.getId()).getStatus().intValue());
        // 7 日之前不动
        TAdrCycleTask t2 = seed("C2B", "YF00", "2026-03-10 23:59:59", "3");
        assertTrue(service.runRound(d("2026-03-06 10:00:00")).isEmpty());
        assertEquals(0, reload(t2.getId()).getStatus().intValue());
    }

    @Test
    void 恰好到期与止点最后一刻都算到_边界闭区间() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask early = seed("C3A", "YF00", "2026-03-10 23:59:59", "3");
        // 开口起点那一秒：3 月 7 日 00:00:00，但夜里不发声——压着；尺上是到了
        assertEquals(1, service.listDue(d("2026-03-07 03:00:00")).size());
        CycleRoundVo night = service.runRound(d("2026-03-07 03:00:00"));
        assertEquals(1, night.getWaitCount(), "夜里压着");
        assertEquals(0, reload(early.getId()).getStatus().intValue());
        // 止点最后一刻 23:59:59 仍算在窗（夜里，压着但尺上到了，不算逾期）
        CycleRoundVo edge = service.runRound(d("2026-03-10 23:59:59"));
        assertEquals(1, edge.getWaitCount());
        assertEquals(0, reload(early.getId()).getStatus().intValue());
    }

    @Test
    void 翻单与回写同一把尺_两边点的对得上() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        seed("C4A", "YF00", "2026-06-11 23:59:59", "0");
        seed("C4B", "YF00", "2026-06-20 23:59:59", "5");
        seed("C4C", "YF00", "2026-06-01 23:59:59", "0"); // 已逾期
        java.util.Date at = d("2026-06-11 10:00:00");
        List<TAdrCycleTask> due = service.listDue(at);
        CycleRoundVo vo = service.runRound(at);
        // 翻单点到窗内的：C4A；回写逐条里属于「窗内」落点（sent/withheld/failed 开单前）的也应同源
        assertEquals(1, due.size());
        assertEquals("C4A", due.get(0).getItemNo());
        long inWindow = vo.getItems().stream()
                .filter(i -> !"skipped".equals(i.getAction())).count();
        assertEquals(due.size(), inWindow, "逾期的另算撤回，窗内的名单两边一致");
    }

    // ================= 班段：夜与节假日不发声 =================

    @Test
    void 夜里到期只压着_候下一轮开口() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask t = seed("C5", "YF00", "2026-03-10 23:59:59", "0");
        CycleRoundVo vo = service.runRound(d("2026-03-10 21:59:59"));
        assertEquals(1, vo.getDoneCount(), "21:59 还在白班");

        TAdrCycleTask t2 = seed("C5B", "YF00", "2026-03-12 23:59:59", "1");
        CycleRoundVo night = service.runRound(d("2026-03-11 22:30:00"));
        assertEquals(1, night.getWaitCount());
        assertEquals(0, reload(t2.getId()).getStatus().intValue(), "仍候催");
        // 次日白班开口，还在止点之内，催出去
        CycleRoundVo morning = service.runRound(d("2026-03-12 08:00:00"));
        assertEquals(1, morning.getDoneCount());
        assertEquals(1, reload(t2.getId()).getStatus().intValue());
    }

    @Test
    void 法定节假日整日不发声_次日开口() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        seedHoliday("2026-05-01");
        TAdrCycleTask t = seed("C6", "YF00", "2026-05-05 23:59:59", "5"); // 4/30 起开口
        CycleRoundVo holiday = service.runRound(d("2026-05-01 10:00:00"));
        assertEquals(1, holiday.getWaitCount());
        assertTrue(holiday.getItems().get(0).getNote().contains("法定节假日"));
        assertEquals(0, reload(t.getId()).getStatus().intValue());
        CycleRoundVo workday = service.runRound(d("2026-05-06 10:00:00"));
        // 5/6 已过止点 → 逾期撤回
        assertEquals(1, workday.getFailCount());
        assertEquals("skipped", workday.getItems().get(0).getAction());
    }

    // ================= 岁末半个月另排两段 + 同单位只认头一条 =================

    @Test
    void 岁末另排两段_正午与晚间压着() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask a = seed("YE1", "YF00", "2026-12-20 23:59:59", "5");
        CycleRoundVo noon = service.runRound(d("2026-12-18 12:30:00"));
        assertEquals(1, noon.getWaitCount(), "12:30 两段之间压着");
        assertEquals(0, reload(a.getId()).getStatus().intValue());
        CycleRoundVo evening = service.runRound(d("2026-12-18 19:00:00"));
        assertEquals(1, evening.getWaitCount(), "19:00 段外压着");
        CycleRoundVo ok = service.runRound(d("2026-12-18 15:00:00"));
        assertEquals(1, ok.getDoneCount(), "下午段 14-18 开口");
    }

    @Test
    void 岁末同一单位一轮只认头一次落地_第二条不入账() {
        seedOrg("YF00", 0);
        seedOrg("YF02", 0);
        seedWatcher("admin");
        TAdrCycleTask first = seed("YE2A", "YF00", "2026-12-20 23:59:59", "5");
        TAdrCycleTask second = seed("YE2B", "YF00", "2026-12-21 23:59:59", "5");
        TAdrCycleTask other = seed("YE2C", "YF02", "2026-12-20 23:59:59", "5");

        CycleRoundVo vo = service.runRound(d("2026-12-18 09:00:00"));
        assertEquals(2, vo.getDoneCount(), "YF00 头一条 + YF02 一条");
        assertEquals(0, vo.getWaitCount(), "第二条不入账：不压账、不点数");
        assertEquals(1, reload(first.getId()).getStatus().intValue());
        assertEquals(0, reload(second.getId()).getStatus().intValue(), "第二条原样候催，没动它");
        assertEquals(1, reload(other.getId()).getStatus().intValue());
        // 台账只有两条桩
        List<TAdrCycleSend> rows = sendsOfRound(vo.getRoundNo());
        assertEquals(2, rows.size());
        // 同日下一轮（比如 10 点再跑一次），第二条仍不入账
        CycleRoundVo again = service.runRound(d("2026-12-18 10:00:00"));
        assertTrue(again.isEmpty(), "头一条已催出，当日第二条仍不入账");
        // 次日它成了这家的头一条
        CycleRoundVo nextDay = service.runRound(d("2026-12-19 09:00:00"));
        assertEquals(1, nextDay.getDoneCount());
        assertEquals(1, reload(second.getId()).getStatus().intValue());
    }

    // ================= 已催出的不再回挑 =================

    @Test
    void 上一轮已催出_下一轮不当候催挑回() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask t = seed("C7", "YF00", "2026-03-10 23:59:59", "0");
        CycleRoundVo r1 = service.runRound(d("2026-03-10 10:00:00"));
        assertEquals(1, r1.getDoneCount());
        CycleRoundVo r2 = service.runRound(d("2026-03-10 11:00:00"));
        assertTrue(r2.isEmpty(), "不再派第二回");
        assertEquals(1, reload(t.getId()).getStatus().intValue());
        assertEquals(1, sendMapper.selectCount(new QueryWrapper<TAdrCycleSend>().eq("action", "sent")).intValue());
    }

    // ================= 催成两笔 + 两条道送达 =================

    @Test
    void 催成两笔同时落_情形改已催出并钉催出时刻() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask t = seed("C8", "YF00", "2026-03-10 23:59:59", "0");
        java.util.Date at = d("2026-03-10 09:30:00");
        CycleRoundVo vo = service.runRound(at);
        TAdrCycleTask after = reload(t.getId());
        assertEquals(1, after.getStatus().intValue());
        assertNotNull(after.getSentAt(), "催出那一刻钉在单上");
        assertEquals(at, after.getSentAt());
        // 台账一桩、送达两条，各记各的
        TAdrCycleSend send = sendsOfRound(vo.getRoundNo()).get(0);
        assertEquals("sent", send.getAction());
        List<TAdrCycleNotice> notices = noticesOfSend(send.getId());
        assertEquals(2, notices.size());
        assertEquals("station", notices.get(0).getChannel());
        assertEquals("sms", notices.get(1).getChannel());
        assertEquals(1, notices.get(0).getDelivered().intValue());
        assertEquals(1, notices.get(1).getDelivered().intValue());
        // 站内消息真落了一张挂给值守人的未读公告
        TSysNotice n = noticeSysMapper.selectList(new QueryWrapper<TSysNotice>()).get(0);
        assertTrue(n.getTitle().contains("C8"));
        TSysNoticeUser link = noticeUserMapper.selectList(new QueryWrapper<TSysNoticeUser>()).get(0);
        assertEquals(n.getId(), link.getNoticeId());
        assertEquals(0, link.getState().intValue());
    }

    @Test
    void 头一条站内消息没落_不许拿短信顶数_条目仍候催() {
        seedOrg("YF00", 0);
        // 不种值守人：站内消息查无此人，落不了
        TAdrCycleTask t = seed("C9", "YF00", "2026-03-10 23:59:59", "0", "13800000000", "ghost");
        CycleRoundVo vo = service.runRound(d("2026-03-10 09:30:00"));
        assertEquals(0, vo.getDoneCount());
        assertEquals(1, vo.getWaitCount(), "通道绊住压住候下一轮");
        assertEquals(0, reload(t.getId()).getStatus().intValue());
        assertNull(reload(t.getId()).getSentAt(), "没催成就不钉催出时刻");
        TAdrCycleSend send = sendsOfRound(vo.getRoundNo()).get(0);
        List<TAdrCycleNotice> notices = noticesOfSend(send.getId());
        assertEquals(0, notices.get(0).getDelivered().intValue(), "站内未送达");
        assertEquals(0, notices.get(1).getDelivered().intValue(), "短信不许顶数，记未发");
        assertTrue(notices.get(1).getFailNote().contains("顶数"));
        // 下一轮值守人补上，催出去了
        seedWatcher("ghost");
        CycleRoundVo r2 = service.runRound(d("2026-03-10 10:30:00"));
        assertEquals(1, r2.getDoneCount());
        assertEquals(1, reload(t.getId()).getStatus().intValue());
    }

    // ================= 单条绊住：催不成，不停全场 =================

    @Test
    void 开单前单位已退出底册_就地催不成_缘故落条上_屏上翻得着() {
        seedOrg("YF00", 0);
        seedOrg("YF99", 1); // 已退出
        seedWatcher("admin");
        TAdrCycleTask bad = seed("C10A", "YF99", "2026-03-10 23:59:59", "0");
        TAdrCycleTask good = seed("C10B", "YF00", "2026-03-10 23:59:59", "0");
        CycleRoundVo vo = service.runRound(d("2026-03-10 10:00:00"));
        assertEquals(1, vo.getDoneCount());
        assertEquals(1, vo.getFailCount());
        TAdrCycleTask afterBad = reload(bad.getId());
        assertEquals(2, afterBad.getStatus().intValue());
        assertTrue(afterBad.getFailReason().contains("退出底册"));
        // 条目还在表里，屏上翻得着，没有删
        assertNotNull(taskMapper.selectById(bad.getId()));
        assertEquals(1, reload(good.getId()).getStatus().intValue(), "后头的接着跑完，没被掐断");
    }

    @Test
    void 联系人整栏空着_就地催不成_后面的继续跑() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask bad = seed("C11A", "YF00", "2026-03-11 23:59:59", "0", "  ", "admin");
        TAdrCycleTask good = seed("C11B", "YF00", "2026-03-11 23:59:59", "0");
        CycleRoundVo vo = service.runRound(d("2026-03-11 10:00:00"));
        assertEquals(1, vo.getDoneCount());
        assertEquals(1, vo.getFailCount());
        assertTrue(reload(bad.getId()).getFailReason().contains("联系人"));
        assertEquals(1, reload(good.getId()).getStatus().intValue());
    }

    // ================= 日子过了：撤回转催不成，留痕不盖旧时刻 =================

    @Test
    void 逾期未催出_撤回转催不成_旧日子不盖_痕迹查得着() {
        seedOrg("YF00", 0);
        seedWatcher("admin");
        TAdrCycleTask t = seed("C12", "YF00", "2026-03-10 23:59:59", "0");
        java.util.Date due = t.getDueAt();
        java.util.Date at = d("2026-03-12 09:00:00");
        CycleRoundVo vo = service.runRound(at);
        TAdrCycleTask after = reload(t.getId());
        assertEquals(2, after.getStatus().intValue());
        assertEquals(due, after.getDueAt(), "头回定的日子不许被后一笔盖掉");
        assertNull(after.getSentAt(), "没催出过，催出时刻始终空着");
        assertNotNull(after.getWithdrawAt(), "撤回这一刻有痕");
        assertEquals(at, after.getWithdrawAt());
        assertTrue(after.getFailReason().contains("撤回"));
        TAdrCycleSend send = sendsOfRound(vo.getRoundNo()).get(0);
        assertEquals("skipped", send.getAction());
        // 催不成之后不再回挑
        assertTrue(service.runRound(d("2026-03-13 09:00:00")).isEmpty());
        assertEquals(due, reload(t.getId()).getDueAt(), "再跑也不盖");
    }

    // ================= 三数与逐条同源 =================

    @Test
    void 屏上三数与档里逐条点的对得上_一趟清点() {
        seedOrg("YF00", 0);
        seedOrg("YF99", 1);
        seedWatcher("admin");
        seed("N1", "YF00", "2026-04-11 23:59:59", "0");   // 窗内但夜里→压住
        seed("N2", "YF99", "2026-04-11 23:59:59", "0");   // 夜里先压着，绊住等开口轮再判
        seed("N3", "YF00", "2026-04-15 23:59:59", "0");   // 未到，不进本轮
        seed("N4", "YF00", "2026-04-01 23:59:59", "0");   // 逾期→skipped
        TAdrCycleTask held = seed("N5", "YF00", "2026-04-11 23:59:59", "0");
        CycleRoundVo vo = service.runRound(d("2026-04-11 23:00:00"));
        int sent = 0, wait = 0, fail = 0;
        for (CycleRoundVo.Item i : vo.getItems()) {
            if ("sent".equals(i.getAction())) {
                sent++;
            } else if ("withheld".equals(i.getAction())) {
                wait++;
            } else {
                fail++;
            }
        }
        assertEquals(sent, vo.getDoneCount());
        assertEquals(wait, vo.getWaitCount());
        assertEquals(fail, vo.getFailCount());
        assertEquals(0, vo.getDoneCount(), "夜里没有催出去的");
        assertEquals(3, vo.getWaitCount(), "N1/N2/N5 三条压着");
        assertEquals(1, vo.getFailCount(), "N4 逾期撤回转催不成");
        // 逐桩台账行数＝本轮条数，档里逐条点的与三数同源
        List<TAdrCycleSend> rows = sendsOfRound(vo.getRoundNo());
        assertEquals(vo.getItems().size(), rows.size());
        assertEquals(rows.stream().filter(r -> "sent".equals(r.getAction())).count(), vo.getDoneCount());
        assertEquals(rows.stream().filter(r -> "withheld".equals(r.getAction())).count(), vo.getWaitCount());
        assertEquals(rows.stream().filter(r -> "failed".equals(r.getAction())
                || "skipped".equals(r.getAction())).count(), vo.getFailCount());
        assertEquals(0, reload(held.getId()).getStatus().intValue());

        // 次日白班再开一轮：压住的三条各有去处——YF00 两条催成，已退出那条催不成
        CycleRoundVo next = service.runRound(d("2026-04-12 10:00:00"));
        // 三条在 4/12 都已越过 4/11 止点：逾期撤回（夜里压过了开口轮，仍按日子算）
        assertEquals(0, next.getDoneCount());
        assertEquals(3, next.getFailCount());
    }

    @Test
    void 本轮无到期条目是正常一轮_不是跑砸() {
        CycleRoundVo vo = service.runRound(d("2026-01-01 10:00:00"));
        assertTrue(vo.isEmpty());
        assertEquals(0, vo.getWaitCount());
        assertEquals(0, vo.getDoneCount());
        assertEquals(0, vo.getFailCount());
        assertEquals("本轮无到期条目", vo.message());
    }

    // ================= 同一刻并进两条：不多不少 =================

    @Test
    void 同一刻并进两条不同单位_不多数一条不少数一条() {
        seedOrg("YF00", 0);
        seedOrg("YF02", 0);
        seedWatcher("admin");
        TAdrCycleTask a = seed("P1", "YF00", "2026-07-10 23:59:59", "0");
        TAdrCycleTask b = seed("P2", "YF02", "2026-07-10 23:59:59", "0");
        CycleRoundVo vo = service.runRound(d("2026-07-10 10:00:00"));
        assertEquals(2, vo.getItems().size());
        assertEquals(2, vo.getDoneCount());
        assertEquals(1, reload(a.getId()).getStatus().intValue());
        assertEquals(1, reload(b.getId()).getStatus().intValue());
        // 两条桩同一轮次号：同一刻并进、同一回执行
        List<TAdrCycleSend> rows = sendsOfRound(vo.getRoundNo());
        assertEquals(2, rows.size());
        assertEquals(1, rows.stream().map(TAdrCycleSend::getRoundNo).distinct().count());
    }

    // ================= 旧口回话不变 =================

    @Test
    void 旧口runOnce仍回成功催出条数_单条绊住不影响旁条() {
        seedOrg("YF00", 0);
        seedOrg("YF99", 1);
        seedWatcher("admin");
        seed("Q1", "YF00", "2026-08-10 23:59:59", "0");
        seed("Q2", "YF99", "2026-08-10 23:59:59", "0");
        int done = service.runOnce(d("2026-08-10 10:00:00"));
        assertEquals(1, done);
        // 空轮回 0
        assertEquals(0, service.runOnce(d("2026-08-10 11:00:00")));
    }
}
