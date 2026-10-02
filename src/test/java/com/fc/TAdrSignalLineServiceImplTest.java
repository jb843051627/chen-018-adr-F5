package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrSignalLineMapper;
import com.fc.v2.model.auto.TAdrSignalLine;
import com.fc.v2.model.custom.adr.LineAdmitReceiptVo;
import com.fc.v2.model.custom.adr.SignalVerdictVo;
import com.fc.v2.service.impl.TAdrSignalLineServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信号检出限值线规矩集成测试——今年省里这一套全辖一个口径：
 * 含起不含止、交棒当日新替旧、往期回取不拿最新顶替、停用不进现行、并列听顺位再听代号、
 * 一条够不着写「无线可依」、进门空栏越栏逐栏拒收、结论钉死代号与启用之日。
 */
class TAdrSignalLineServiceImplTest {

    private TAdrSignalLineServiceImpl service;
    private TAdrSignalLineMapper mapper;
    private final AtomicLong idSeq = new AtomicLong(2000);
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:signal_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        try (Connection conn = ds.getConnection();
             Reader reader = new InputStreamReader(
                     getClass().getResourceAsStream("/schema-h2.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(conn, reader);
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource((DataSource) ds);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(TAdrSignalLineMapper.class);
        SqlSession session = factory.openSession(true);
        this.mapper = session.getMapper(TAdrSignalLineMapper.class);

        this.service = new TAdrSignalLineServiceImpl();
        Field f = TAdrSignalLineServiceImpl.class.getDeclaredField("adrSignalLineMapper");
        f.setAccessible(true);
        f.set(this.service, this.mapper);
    }

    private Date at(String text) throws Exception {
        return sdf.parse(text);
    }

    /** 落一条线：代号、三界、启用、交棒(null=长期在场)、顺位、状态(0现行 1停用) */
    private TAdrSignalLine seed(String code, String t1, String t2, String t3,
                                String start, String end, int priority, int status) throws Exception {
        TAdrSignalLine r = new TAdrSignalLine();
        r.setId(idSeq.incrementAndGet());
        r.setRuleCode(code);
        r.setRuleName(code + "号线");
        r.setTh1Max(t1 == null ? null : new BigDecimal(t1));
        r.setTh2Max(t2 == null ? null : new BigDecimal(t2));
        r.setTh3Max(t3 == null ? null : new BigDecimal(t3));
        r.setEffStart(at(start));
        r.setEffEnd(end == null ? null : at(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        mapper.insert(r);
        return r;
    }

    // ---------------- 在场口径：含起不含止 ----------------

    @Test
    void 启用之日当日在场_交棒之日当日不在场() throws Exception {
        seed("L1", "10", "20", "30", "2026-01-01 00:00:00", "2026-07-01 00:00:00", 1, 0);
        // 启用当日（含起）
        assertTrue(service.usable(mapper.selectList(null).get(0).getId(), at("2026-01-01 00:00:00")));
        // 交棒前一刻还在场
        assertEquals(1, service.countAvailable(at("2026-06-30 23:59:59")));
        // 交棒当日（不含止）——不在场
        assertEquals(0, service.countAvailable(at("2026-07-01 00:00:00")));
    }

    @Test
    void 交棒当日旧线退新线进_一处判定两处同答() throws Exception {
        TAdrSignalLine oldLine = seed("OLD", "10", "20", "30",
                "2026-01-01 00:00:00", "2026-09-01 00:00:00", 5, 0);
        TAdrSignalLine newLine = seed("NEW", "11", "22", "33",
                "2026-09-01 00:00:00", null, 5, 0);
        Date handover = at("2026-09-01 00:00:00");
        // 名单只认新线
        List<TAdrSignalLine> onScreen = service.listAvailable(handover);
        assertEquals(1, onScreen.size());
        assertEquals("NEW", onScreen.get(0).getRuleCode());
        // 逐条判与名单同源：旧线 usable=false，新线 usable=true
        assertFalse(service.usable(oldLine.getId(), handover));
        assertTrue(service.usable(newLine.getId(), handover));
        // 求值也是新线
        assertEquals("NEW", service.evaluate(new BigDecimal("5"), handover).getRuleCode());
    }

    @Test
    void 补算去年下半年照当期日子回取_不拿最新线顶替() throws Exception {
        // 年初三条（纸上）→ 秋天三条（系统里）。补算 6 月那一期必须回取旧线
        seed("SPRING", "10", "20", "30", "2026-01-01 00:00:00", "2026-09-01 00:00:00", 5, 0);
        seed("AUTUMN", "90", "95", "99", "2026-09-01 00:00:00", null, 5, 0);
        SignalVerdictVo back = service.evaluate(new BigDecimal("15"), at("2026-06-15 12:00:00"));
        assertTrue(back.isAvailable());
        assertEquals("SPRING", back.getRuleCode());
        assertEquals(2, back.getLevel()); // 旧线 15 落在中界
        // 同一比值放到今天，走秋线：超高界
        SignalVerdictVo now = service.evaluate(new BigDecimal("15"), at("2026-10-01 00:00:00"));
        assertEquals("AUTUMN", now.getRuleCode());
        assertEquals(1, now.getLevel());
    }

    @Test
    void 只有启用之日没写交棒之日的存量线_照长期在场认() throws Exception {
        seed("LEGACY", "10", "20", "30", "2020-01-01 00:00:00", null, 1, 0);
        assertEquals(1, service.countAvailable(at("2026-10-01 00:00:00")));
        assertTrue(service.evaluate(new BigDecimal("5"), at("2026-10-01 00:00:00")).isAvailable());
    }

    // ---------------- 已停用不进现行，仍在档供翻检 ----------------

    @Test
    void 已停用的线不进现行求值_但还在档里() throws Exception {
        TAdrSignalLine stopped = seed("DEAD", "10", "20", "30",
                "2026-01-01 00:00:00", null, 9, 1);
        assertNotNull(mapper.selectById(stopped.getId()));
        assertEquals(0, service.countAvailable(at("2026-10-01 00:00:00")));
        assertFalse(service.usable(stopped.getId(), at("2026-10-01 00:00:00")));
        SignalVerdictVo v = service.evaluate(new BigDecimal("5"), at("2026-10-01 00:00:00"));
        assertFalse(v.isAvailable());
        // 已删的线同样不进现行
        stopped.setStatus(0);
        stopped.setDelFlag(1);
        mapper.updateById(stopped);
        assertEquals(0, service.countAvailable(at("2026-10-01 00:00:00")));
    }

    // ---------------- 并列了断：顺位大的说话，再并列代号靠后的说话 ----------------

    @Test
    void 同刻并列听让线顺位_顺位并列听代号_两次求值一致() throws Exception {
        seed("A", "10", "20", "30", "2026-01-01 00:00:00", null, 1, 0);
        seed("B", "10", "20", "30", "2026-01-01 00:00:00", null, 9, 0);
        seed("C", "10", "20", "30", "2026-01-01 00:00:00", null, 9, 0);
        Date at = at("2026-10-01 00:00:00");
        assertEquals(3, service.countAvailable(at));
        // 顺位 9 的 B、C 压过 A；B、C 同顺位，代号靠后的 C 说话
        assertEquals("C", service.listAvailable(at).get(0).getRuleCode());
        SignalVerdictVo v1 = service.evaluate(new BigDecimal("5"), at);
        SignalVerdictVo v2 = service.evaluate(new BigDecimal("5"), at);
        assertEquals("C", v1.getRuleCode());
        assertEquals(v1.getRuleCode(), v2.getRuleCode()); // 不许两次给回两条不同的线
        // 按代号取的旧方法仍能直接取到 A（同刻在场，同名不同签名，签名照旧）
        assertEquals(1, service.evaluate("A", new BigDecimal("5"), at));
    }

    // ---------------- 一条都够不着：无线可依，两栏留空 ----------------

    @Test
    void 一条够不着写无线可依_代号启用之日留空_不拿邻近线凑() throws Exception {
        // 唯一一条明天才启用
        seed("FUTURE", "10", "20", "30", "2026-10-03 00:00:00", null, 9, 0);
        SignalVerdictVo v = service.evaluate(new BigDecimal("5"), at("2026-10-02 00:00:00"));
        assertFalse(v.isAvailable());
        assertEquals("无线可依", v.getLabel());
        assertNull(v.getRuleCode());
        assertNull(v.getEffStart());
        assertEquals(0, v.getLevel());
    }

    @Test
    void 入算比值负数或越段不给结论() throws Exception {
        seed("L1", "10", "20", "30", "2026-01-01 00:00:00", null, 1, 0);
        Date at = at("2026-10-01 00:00:00");
        assertFalse(service.evaluate(new BigDecimal("-1"), at).isAvailable());
        assertFalse(service.evaluate(new BigDecimal("101"), at).isAvailable());
        assertEquals(0, service.evaluateTop(new BigDecimal("-1"), at));
        assertEquals(0, service.evaluate("L1", new BigDecimal("101"), at));
    }

    // ---------------- 结论钉死代号与启用之日 ----------------

    @Test
    void 结论压死代号与启用之日_事后换线不改史() throws Exception {
        seed("OLD", "10", "20", "30", "2026-01-01 00:00:00", "2026-10-01 00:00:00", 5, 0);
        Date at = at("2026-09-30 00:00:00");
        SignalVerdictVo pinned = service.evaluate(new BigDecimal("5"), at);
        assertEquals("OLD", pinned.getRuleCode());
        assertEquals(at("2026-01-01 00:00:00"), pinned.getEffStart());
        // 事后旧线改名/改界，结论上钉死的两栏是当时给回的快照值以外，再算仍按当期回取
        TAdrSignalLine old = mapper.selectList(null).get(0);
        old.setRuleName("已改名");
        mapper.updateById(old);
        seed("NEW", "1", "2", "3", "2026-10-01 00:00:00", null, 5, 0);
        SignalVerdictVo still = service.evaluate(new BigDecimal("5"), at);
        assertEquals("OLD", still.getRuleCode());
        assertEquals(at("2026-01-01 00:00:00"), still.getEffStart());
    }

    // ---------------- 进门守规矩：三分界非写不可、不许负、不许越段 ----------------

    @Test
    void 空着分界数起不来_回执指名空在哪一栏且不落库() throws Exception {
        TAdrSignalLine bad = new TAdrSignalLine();
        bad.setId(idSeq.incrementAndGet());
        bad.setRuleCode("BAD1");
        bad.setTh1Max(null);
        bad.setTh2Max(new BigDecimal("20"));
        bad.setTh3Max(new BigDecimal("30"));
        bad.setEffStart(at("2026-10-01 00:00:00"));
        LineAdmitReceiptVo rc = service.admitLine(bad);
        assertFalse(rc.isAccepted());
        assertEquals(1, rc.getBlankFields().size());
        assertTrue(rc.getBlankFields().get(0).contains("th1_max"), rc.getMessage());
        assertTrue(rc.getMessage().contains("低界比值"));
        assertEquals(0, mapper.selectList(null).size()); // 一行不落库
        assertNull(rc.getRuleId());
    }

    @Test
    void 负数与越段比值不进账_回执逐栏点名越在哪() throws Exception {
        TAdrSignalLine bad = new TAdrSignalLine();
        bad.setId(idSeq.incrementAndGet());
        bad.setRuleCode("BAD2");
        bad.setTh1Max(new BigDecimal("-5"));   // 负数
        bad.setTh2Max(new BigDecimal("20"));
        bad.setTh3Max(new BigDecimal("120"));  // 越出中心段
        bad.setEffStart(at("2026-10-01 00:00:00"));
        LineAdmitReceiptVo rc = service.admitLine(bad);
        assertFalse(rc.isAccepted());
        assertTrue(rc.getOutOfRangeFields().get(0).contains("th1_max"));
        assertTrue(rc.getOutOfRangeFields().get(0).contains("-5"));
        assertTrue(rc.getOutOfRangeFields().get(1).contains("th3_max"));
        assertTrue(rc.getOutOfRangeFields().get(1).contains("120"));
        assertEquals(0, mapper.selectList(null).size());
        // 前年那种「保存得进去又能通过求值」：今年根本进不了账，自然谈不上求值
    }

    @Test
    void 三界齐全且在段内_准入并即时可求值() throws Exception {
        TAdrSignalLine good = new TAdrSignalLine();
        good.setId(idSeq.incrementAndGet());
        good.setRuleCode("GOOD");
        good.setTh1Max(new BigDecimal("10"));
        good.setTh2Max(new BigDecimal("20"));
        good.setTh3Max(new BigDecimal("30"));
        good.setEffStart(at("2026-10-01 00:00:00"));
        good.setPriority(1);
        LineAdmitReceiptVo rc = service.admitLine(good);
        assertTrue(rc.isAccepted(), rc.getMessage());
        assertNotNull(rc.getRuleId());
        assertEquals(Integer.valueOf(0), good.getStatus());
        assertEquals(Integer.valueOf(0), good.getDelFlag());
        SignalVerdictVo v = service.evaluate(new BigDecimal("25"), at("2026-10-02 00:00:00"));
        assertTrue(v.isAvailable());
        assertEquals("GOOD", v.getRuleCode());
        assertEquals(3, v.getLevel());
    }

    // ---------------- 屏上名单与逐条判定同源 ----------------

    @Test
    void 屏上名单与逐条判定出自同一回计算() throws Exception {
        TAdrSignalLine a = seed("A", "10", "20", "30", "2026-01-01 00:00:00", null, 1, 0);
        TAdrSignalLine b = seed("B", "10", "20", "30", "2026-01-01 00:00:00", "2026-05-01 00:00:00", 1, 0);
        Date at = at("2026-05-01 00:00:00"); // B 交棒当日
        List<TAdrSignalLine> screen = service.listAvailable(at);
        assertEquals(1, screen.size());
        assertEquals("A", screen.get(0).getRuleCode());
        assertTrue(service.usable(a.getId(), at));
        assertFalse(service.usable(b.getId(), at));
        assertEquals(1, service.countAvailable(at));
    }
}
