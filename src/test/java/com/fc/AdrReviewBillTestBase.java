package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrReviewBillMapper;
import com.fc.v2.mapper.auto.TAdrReviewSignMapper;
import com.fc.v2.service.impl.TAdrReviewBillServiceImpl;
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

/**
 * 签认单领域服务集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring、不连 MySQL/Redis。
 */
public abstract class AdrReviewBillTestBase {

    protected TAdrReviewBillServiceImpl service;
    protected TAdrReviewBillMapper billMapper;
    protected TAdrReviewSignMapper signMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:review_" + getClass().getSimpleName() + "_" + System.nanoTime()
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
        factory.getConfiguration().addMapper(TAdrReviewBillMapper.class);
        factory.getConfiguration().addMapper(TAdrReviewSignMapper.class);

        SqlSession session = factory.openSession(true);
        this.billMapper = session.getMapper(TAdrReviewBillMapper.class);
        this.signMapper = session.getMapper(TAdrReviewSignMapper.class);

        this.service = new TAdrReviewBillServiceImpl();
        inject(this.service, "adrReviewBillMapper", this.billMapper);
        inject(this.service, "adrReviewSignMapper", this.signMapper);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
