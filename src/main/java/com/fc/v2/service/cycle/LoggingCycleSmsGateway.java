package com.fc.v2.service.cycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 占位短信息网关：系统还没接真网关前，只在日志里留痕并当已发出。
 *
 * <p>真接入运营商/短信平台时，另写一个实现 {@link CycleSmsGateway} 的 {@code @Component}
 * 替换本件（删掉或停用本类），催送轮一行不改。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Component
public class LoggingCycleSmsGateway implements CycleSmsGateway {

    private static final Logger logger = LoggerFactory.getLogger(LoggingCycleSmsGateway.class);

    @Override
    public boolean send(String mobile, String content) {
        logger.info("【占位短信网关】发给 {}：{}", mobile, content);
        return true;
    }
}
