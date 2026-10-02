package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.custom.adr.CycleRoundVo;

/**
 * 定期报告催送单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）。
 *
 * <p>本轮该催哪几桩、催出去几条与压住几条，全在服务器这一回执行里算定再落库；
 * 翻单（{@link #listDue(Date)}）与回写（{@link #runRound(Date)}）用同一把尺。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrCycleTaskService {

    /** 按主键回查条目 */
    TAdrCycleTask selectTAdrCycleTaskById(Long id);

    /**
     * 该时刻已开口、尚未越过止点、情形仍为候催的条目（执行窗口内 + 到期 + 尚未处理）。
     * 翻单只此一把尺；{@link #runRound(Date)} 挑条目走的也是这把尺，两处不许各算各的。
     */
    List<TAdrCycleTask> listDue(Date at);

    /**
     * 旧口：执行一次，返回**成功催出条数**；单条失败跳过继续。
     * 回话形状照旧不动；内部已是新跑法（等同 {@link #runRound(Date)} 的已催出数）。
     */
    int runOnce(Date at);

    /**
     * 新口（同名另挂）：完整跑一回催送轮，三数（候催/已催出/催不成）与逐条落点
     * 一趟清点一并交出；一个该动的也没挑着就回「本轮无到期条目」，那不叫跑砸。
     */
    CycleRoundVo runRound(Date at);

    /** Quartz 入口：按此刻跑一回 */
    CycleRoundVo runRound();
}
