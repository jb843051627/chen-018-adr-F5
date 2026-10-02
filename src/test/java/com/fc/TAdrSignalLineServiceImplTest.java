package com.fc;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAdrSignalLine;
import com.fc.v2.model.custom.adr.LineIntakeReceiptVo;
import com.fc.v2.model.custom.adr.SignalVerdictVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信号检出限值线求值口径集成测试——省里立下的规矩：
 * 同一类事全辖只准一套说法，取线只准走一处。在场＝含起不含止；
 * 补算照那一期的日子回取；交棒当日旧退新进，一处判两个地方同一个答案；
 * 已停用不进现行但留档；存量缺交棒之日照长期在场认；并列听顺位再听代号；
 * 无线可依两栏留空；进门三条分界数非写不可、越段不进账、回执指名栏目；
 * 结论钉死代号与启用之日，事后换线不改史。
 */
class TAdrSignalLineServiceImplTest extends AdrSignalLineTestBase {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date at(String ymd) throws Exception {
        return FMT.parse(ymd);
    }

    /** 直插一条线（绕开进门校验，模拟存量册里各式各样的账） */
    private Long rawLine(String code, String th1, String th2, String th3,
                         String effStart, String effEnd, Integer priority, Integer status) throws Exception {
        TAdrSignalLine l = new TAdrSignalLine();
        l.setRuleCode(code);
        l.setRuleName(code + "号线");
        l.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        l.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        l.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        l.setEffStart(effStart == null ? null : at(effStart + " 00:00:00"));
        l.setEffEnd(effEnd == null ? null : at(effEnd + " 00:00:00"));
        l.setPriority(priority);
        l.setStatus(status);
        l.setDelFlag(0);
        lineMapper.insert(l);
        return l.getId();
    }

    // ---------------- 在场窗口：含起不含止 ----------------

    @Test
    void 在场窗口_启用当日到得了_交棒当日到不了() throws Exception {
        Long id = rawLine("X1", "10", "20", "30", "2026-03-01", "2026-09-01", 1, 0);

        assertFalse(service.usable(id, at("2026-02-28 23:59:59")), "启用前一日不在场");
        assertTrue(service.usable(id, at("2026-03-01 00:00:00")), "启用当日零刻起在场");
        assertTrue(service.usable(id, at("2026-08-31 23:59:59")), "交棒前一刻还在场");
        assertFalse(service.usable(id, at("2026-09-01 00:00:00")), "交棒当日零刻起不在场");
    }

    @Test
    void 交棒当日_旧线退场新线进场_屏上名单与逐条判定同一答案() throws Exception {
        Long oldId = rawLine("OLD", "10", "20", "30", "2026-01-01", "2026-09-01", 1, 0);
        Long newId = rawLine("NEW", "15", "25", "35", "2026-09-01", null, 1, 0);

        Date handover = at("2026-09-01 00:00:00");
        // 一处判：旧线交棒当日便不在场，新线当日起在场
        assertFalse(service.usable(oldId, handover), "旧线交棒当日退场");
        assertTrue(service.usable(newId, handover), "新线交棒当日进场");
        // 两个地方同一个答案：屏上名单只剩新线，求值钉的也是新线
        List<TAdrSignalLine> list = service.listAvailable(handover);
        assertEquals(1, list.size());
        assertEquals("NEW", list.get(0).getRuleCode());
        SignalVerdictVo v = service.evaluate(new BigDecimal("18"), handover);
        assertEquals("NEW", v.getRuleCode());
        assertEquals(at("2026-09-01 00:00:00"), v.getEffStart());
        assertEquals(2, v.getLevel(), "18 落进新线中界 25，归 2 档");
    }

    // ---------------- 补算回取 ----------------

    @Test
    void 补算去年那一期_回取当时在场的老线_不拿最新立的顶() throws Exception {
        // 年初那三条写在纸上（老线），入秋换线立进系统（新线）
        rawLine("L1", "10", "20", "30", "2025-01-01", "2025-09-01", 1, 0);
        rawLine("L1", "50", "60", "70", "2025-09-01", null, 1, 0);

        // 补算去年下半年那一期：照那一期的日子回取，18 该归老线的 2 档
        SignalVerdictVo back = service.evaluate(new BigDecimal("18"), at("2025-07-15 10:00:00"));
        assertEquals(2, back.getLevel(), "去年七月 18 落老线中界 20，归 2 档");
        assertEquals(at("2025-01-01 00:00:00"), back.getEffStart(), "钉的是老线的启用之日");
        // 同一品种同一桩事搁到今天求值，才轮到新线说话
        SignalVerdictVo now = service.evaluate(new BigDecimal("18"), at("2026-06-01 10:00:00"));
        assertEquals(1, now.getLevel(), "今天 18 落新线低界 50，归 1 档");
        assertEquals(at("2025-09-01 00:00:00"), now.getEffStart());
    }

    // ---------------- 已停用与存量兼容 ----------------

    @Test
    void 已停用的线_不进现行求值_档里还翻得到() throws Exception {
        Long id = rawLine("STOP", "10", "20", "30", "2025-01-01", null, 9, 1);
        Date now = at("2026-06-01 00:00:00");

        assertFalse(service.usable(id, now), "已停用不进现行求值");
        assertEquals(0, service.countAvailable(now));
        assertTrue(service.listAvailable(now).isEmpty());
        SignalVerdictVo v = service.evaluate(new BigDecimal("5"), now);
        assertTrue(v.isNoLine(), "册上只有停用线，求值无线可依");
        assertNotNull(service.selectTAdrSignalLineById(id), "档里还翻得到，供事后翻检");
    }

    @Test
    void 存量线只写启用之日_照长期在场认_不许当不存在() throws Exception {
        Long id = rawLine("LEGACY", "10", "20", "30", "2024-06-01", null, 1, 0);

        assertTrue(service.usable(id, at("2024-06-01 00:00:00")));
        assertTrue(service.usable(id, at("2026-10-02 00:00:00")), "没写交棒之日，长期在场");
        assertTrue(service.usable(id, at("2036-01-01 00:00:00")), "十年后照样在场");
        assertFalse(service.usable(id, at("2024-05-31 00:00:00")), "启用之前还是不在场");
    }

    // ---------------- 并列了断 ----------------

    @Test
    void 并列在场_让线顺位大的说话() throws Exception {
        rawLine("LOW", "10", "20", "30", "2026-01-01", null, 1, 0);
        rawLine("HIGH", "40", "50", "60", "2026-01-01", null, 5, 0);
        Date now = at("2026-06-01 00:00:00");

        SignalVerdictVo v = service.evaluate(new BigDecimal("45"), now);
        assertEquals("HIGH", v.getRuleCode(), "顺位大的说话");
        assertEquals(2, v.getLevel(), "45 落 HIGH 中界 50，归 2 档");
        // 屏上名单也是这个次序
        List<TAdrSignalLine> list = service.listAvailable(now);
        assertEquals("HIGH", list.get(0).getRuleCode());
        assertEquals("LOW", list.get(1).getRuleCode());
    }

    @Test
    void 顺位并列_代号靠后的说话_两次求值给回同一条() throws Exception {
        rawLine("R-A", "10", "20", "30", "2026-01-01", null, 3, 0);
        rawLine("R-B", "40", "50", "60", "2026-01-01", null, 3, 0);
        Date now = at("2026-06-01 00:00:00");

        SignalVerdictVo first = service.evaluate(new BigDecimal("45"), now);
        SignalVerdictVo second = service.evaluate(new BigDecimal("45"), now);
        assertEquals("R-B", first.getRuleCode(), "顺位一样，代号靠后的说话");
        assertEquals("R-B", second.getRuleCode(), "随机取法一概不认，两次须同一条");
        assertEquals(first.getEffStart(), second.getEffStart());
    }

    // ---------------- 无线可依 ----------------

    @Test
    void 一条都够不着_无线可依_代号与启用之日留空() throws Exception {
        // 邻近那条明年才启用，不许拿它的数凑一个
        rawLine("FUTURE", "10", "20", "30", "2027-01-01", null, 9, 0);
        Date now = at("2026-06-01 00:00:00");

        SignalVerdictVo v = service.evaluate(new BigDecimal("15"), now);
        assertTrue(v.isNoLine());
        assertEquals("无线可依", v.getMessage());
        assertNull(v.getRuleCode(), "代号留空");
        assertNull(v.getEffStart(), "启用之日留空");
        assertEquals(0, v.getLevel(), "不硬给结论");
        assertEquals(0, service.evaluateTop(new BigDecimal("15"), now));
    }

    // ---------------- 进门校验 ----------------

    @Test
    void 进门_分界数空着_指名空在哪一栏_不进账() {
        TAdrSignalLine l = new TAdrSignalLine();
        l.setRuleCode("IN-1");
        l.setTh1Max(new BigDecimal("10"));
        // 中界、高界空着
        LineIntakeReceiptVo r = service.insertTAdrSignalLine(l);

        assertFalse(r.isAccepted());
        assertEquals(2, r.getFaults().size(), "空两栏指名两栏");
        assertTrue(r.getFaults().get(0).contains("中界比值"), r.getFaults().get(0));
        assertTrue(r.getFaults().get(0).contains("th2Max"), "指名到栏");
        assertTrue(r.getFaults().get(1).contains("高界比值"), r.getFaults().get(1));
        assertEquals(0, lineMapper.selectCount(null).intValue(), "空着起不来，库里没它的账");
    }

    @Test
    void 进门_比值报负报出段_指名越在哪一栏_不进账() {
        TAdrSignalLine l = new TAdrSignalLine();
        l.setRuleCode("IN-2");
        l.setTh1Max(new BigDecimal("-3"));
        l.setTh2Max(new BigDecimal("50"));
        l.setTh3Max(new BigDecimal("120"));
        LineIntakeReceiptVo r = service.insertTAdrSignalLine(l);

        assertFalse(r.isAccepted());
        assertEquals(2, r.getFaults().size());
        assertTrue(r.getFaults().get(0).contains("低界比值") && r.getFaults().get(0).contains("-3"),
                "负数那栏指名带数：" + r.getFaults().get(0));
        assertTrue(r.getFaults().get(1).contains("高界比值") && r.getFaults().get(1).contains("120"),
                "越段那栏指名带数：" + r.getFaults().get(1));
        assertEquals(0, lineMapper.selectCount(null).intValue(), "前年那两条今年不许再进账");
    }

    @Test
    void 进门_三条齐整且在段内_进账_缺省按现行立() {
        TAdrSignalLine l = new TAdrSignalLine();
        l.setRuleCode("IN-3");
        l.setTh1Max(new BigDecimal("10"));
        l.setTh2Max(new BigDecimal("20"));
        l.setTh3Max(new BigDecimal("100"));
        LineIntakeReceiptVo r = service.insertTAdrSignalLine(l);

        assertTrue(r.isAccepted(), r.getMessage());
        assertNotNull(r.getLine().getId());
        assertEquals(0, r.getLine().getStatus().intValue(), "没口占情形，按现行立");
        assertEquals(1, lineMapper.selectCount(null).intValue());
    }

    // ---------------- 结论钉线 ----------------

    @Test
    void 结论钉死代号与启用之日_事后换线不改史() throws Exception {
        Long oldId = rawLine("P1", "10", "20", "30", "2026-01-01", null, 1, 0);
        Date now = at("2026-06-01 00:00:00");

        SignalVerdictVo settled = service.evaluate(new BigDecimal("25"), now);
        assertEquals("P1", settled.getRuleCode());
        assertEquals(at("2026-01-01 00:00:00"), settled.getEffStart());
        assertEquals(3, settled.getLevel());

        // 事后换线：旧线交棒（当日零刻起不在场）、新线当日启用
        TAdrSignalLine old = lineMapper.selectById(oldId);
        old.setEffEnd(at("2026-06-01 00:00:00"));
        lineMapper.updateById(old);
        rawLine("P1", "80", "90", "100", "2026-06-01", null, 1, 0);

        assertEquals("P1", settled.getRuleCode(), "已落定的结论不跟着换线走");
        assertEquals(3, settled.getLevel(), "历史那笔还是 3 档");
        SignalVerdictVo again = service.evaluate(new BigDecimal("25"), now);
        assertEquals(1, again.getLevel(), "重新求值才按新线说话");
        assertEquals(at("2026-06-01 00:00:00"), again.getEffStart());
    }

    @Test
    void 降档与销警是两笔_各留各的() throws Exception {
        rawLine("D1", "10", "20", "30", "2026-01-01", null, 1, 0);
        Date now = at("2026-06-01 00:00:00");

        SignalVerdictVo alarm = service.evaluate(new BigDecimal("25"), now);
        SignalVerdictVo cleared = service.evaluate(new BigDecimal("5"), now);

        assertEquals(3, alarm.getLevel(), "先前那一档的警报");
        assertEquals(1, cleared.getLevel(), "销警是另外一笔");
        assertEquals("D1", alarm.getRuleCode());
        assertEquals("D1", cleared.getRuleCode());
        assertFalse(alarm == cleared, "两笔各留各的，不互相覆写");
    }

    // ---------------- 按代号取线的旧方法 ----------------

    @Test
    void 按代号取线_签名照旧_按在场那一版判档() throws Exception {
        rawLine("C1", "10", "20", "30", "2025-01-01", "2025-09-01", 1, 0);
        rawLine("C1", "50", "60", "70", "2025-09-01", null, 1, 0);

        assertEquals(2, service.evaluate("C1", new BigDecimal("18"), at("2025-07-15 00:00:00")),
                "回取去年，按当时在场的老版判");
        assertEquals(1, service.evaluate("C1", new BigDecimal("18"), at("2026-06-01 00:00:00")),
                "今天按在场的新版判");
        assertEquals(0, service.evaluate("C1", new BigDecimal("18"), at("2024-12-31 00:00:00")),
                "启用之前该代号无在场版本，不给结论");
        assertEquals(0, service.evaluate("C1", new BigDecimal("-1"), at("2026-06-01 00:00:00")),
                "比值越段不求值");
        assertEquals(0, service.evaluate("NOPE", new BigDecimal("18"), at("2026-06-01 00:00:00")),
                "册上没这个代号");
    }

    // ---------------- 屏上名单与逐条判定同一回计算 ----------------

    @Test
    void 屏上在场名单与逐条判定_出自同一回计算() throws Exception {
        Long a = rawLine("S-A", "10", "20", "30", "2026-01-01", null, 1, 0);
        Long b = rawLine("S-B", "10", "20", "30", "2026-03-01", "2026-09-01", 2, 0);
        Long c = rawLine("S-C", "10", "20", "30", "2026-01-01", null, 3, 1);
        Date now = at("2026-06-01 00:00:00");

        List<TAdrSignalLine> list = service.listAvailable(now);
        assertEquals(2, list.size(), "在册三条，停用那条不上屏");
        for (TAdrSignalLine l : list) {
            assertTrue(service.usable(l.getId(), now), "屏上有的，逐条判也在场");
        }
        assertTrue(service.usable(a, now));
        assertTrue(service.usable(b, now));
        assertFalse(service.usable(c, now), "逐条判出去的，屏上也没有");
        assertEquals(service.countAvailable(now), list.size(), "角标与名单同一回计算");
    }
}
