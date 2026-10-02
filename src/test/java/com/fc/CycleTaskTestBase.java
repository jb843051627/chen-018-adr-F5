package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrCycleCalendarMapper;
import com.fc.v2.mapper.auto.TAdrCycleNoticeMapper;
import com.fc.v2.mapper.auto.TAdrCycleSendMapper;
import com.fc.v2.mapper.auto.TAdrCycleTaskMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TSysNoticeMapper;
import com.fc.v2.mapper.auto.TSysNoticeUserMapper;
import com.fc.v2.mapper.auto.TSysUserMapper;
import com.fc.v2.service.cycle.DutyClock;
import com.fc.v2.service.cycle.LoggingCycleSmsGateway;
import com.fc.v2.service.cycle.SmsNotifyChannel;
import com.fc.v2.service.cycle.StationMessageChannel;
import com.fc.v2.service.impl.TAdrCycleTaskServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 催送轮集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring。
 */
public abstract class CycleTaskTestBase {

    protected TAdrCycleTaskServiceImpl service;
    protected TAdrCycleTaskMapper taskMapper;
    protected TAdrCycleSendMapper sendMapper;
    protected TAdrCycleNoticeMapper noticeMapper;
    protected TAdrCycleCalendarMapper calendarMapper;
    protected TAdrOrgArchMapper orgMapper;
    protected TSysUserMapper userMapper;
    protected TSysNoticeMapper noticeSysMapper;
    protected TSysNoticeUserMapper noticeUserMapper;

    protected StationMessageChannel stationChannel;
    protected SmsNotifyChannel smsChannel;
    protected DutyClock clock;

    private final AtomicLong idSeq = new AtomicLong(2000);

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cycle_" + getClass().getSimpleName() + "_" + System.nanoTime()
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
        factory.getConfiguration().addMapper(TAdrCycleTaskMapper.class);
        factory.getConfiguration().addMapper(TAdrCycleSendMapper.class);
        factory.getConfiguration().addMapper(TAdrCycleNoticeMapper.class);
        factory.getConfiguration().addMapper(TAdrCycleCalendarMapper.class);
        factory.getConfiguration().addMapper(TAdrOrgArchMapper.class);
        factory.getConfiguration().addMapper(TSysUserMapper.class);
        factory.getConfiguration().addMapper(TSysNoticeMapper.class);
        factory.getConfiguration().addMapper(TSysNoticeUserMapper.class);

        SqlSession session = factory.openSession(true);
        this.taskMapper = session.getMapper(TAdrCycleTaskMapper.class);
        this.sendMapper = session.getMapper(TAdrCycleSendMapper.class);
        this.noticeMapper = session.getMapper(TAdrCycleNoticeMapper.class);
        this.calendarMapper = session.getMapper(TAdrCycleCalendarMapper.class);
        this.orgMapper = session.getMapper(TAdrOrgArchMapper.class);
        this.userMapper = session.getMapper(TSysUserMapper.class);
        this.noticeSysMapper = session.getMapper(TSysNoticeMapper.class);
        this.noticeUserMapper = session.getMapper(TSysNoticeUserMapper.class);

        this.clock = new DutyClock();
        inject(this.clock, "calendarMapper", this.calendarMapper);

        this.stationChannel = new StationMessageChannel();
        inject(this.stationChannel, "userMapper", this.userMapper);
        inject(this.stationChannel, "noticeMapper", this.noticeSysMapper);
        inject(this.stationChannel, "noticeUserMapper", this.noticeUserMapper);

        this.smsChannel = new SmsNotifyChannel();
        inject(this.smsChannel, "smsGateway", new LoggingCycleSmsGateway());

        this.service = new TAdrCycleTaskServiceImpl();
        inject(this.service, "adrCycleTaskMapper", this.taskMapper);
        inject(this.service, "adrCycleSendMapper", this.sendMapper);
        inject(this.service, "adrCycleNoticeMapper", this.noticeMapper);
        inject(this.service, "adrOrgArchMapper", this.orgMapper);
        inject(this.service, "adrCycleCalendarMapper", this.calendarMapper);
        inject(this.service, "dutyClock", this.clock);
        inject(this.service, "stationChannel", this.stationChannel);
        inject(this.service, "smsChannel", this.smsChannel);
    }

    protected static void inject(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    protected long nextId() {
        return idSeq.incrementAndGet();
    }

    protected static java.util.Date d(String text) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(text);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
