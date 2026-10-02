package com.fc.v2.service.cycle;

/**
 * 短信息网关：中心真要接运营商/短信平台时实现本口另挂同名 bean 即可，
 * 催送轮与送达账都不必动。
 *
 * @author fuce
 * @date 2026-10-01
 */
public interface CycleSmsGateway {

    /** 往这个短信号发一条；发不出去抛异常或回 false，都按未送达记账 */
    boolean send(String mobile, String content) throws Exception;
}
