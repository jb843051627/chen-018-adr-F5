package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAdrSignalLine;
import com.fc.v2.model.custom.adr.LineIntakeReceiptVo;
import com.fc.v2.model.custom.adr.SignalVerdictVo;

/**
 * 信号检出限值线 Service接口。
 *
 * 全辖一处口径：指定那一刻在场的是哪一条线、该取哪一册的界，全部收进
 * 本接口实现里那个求值入口现算现给——已停用的线进不进现行、一条都够不着时
 * 兜不兜底、并列时听顺位还是听代号，旁处寻不到第二条能绕过它的通道；
 * 结论上要钉的代号与启用之日以它的给回为准，页面不许自己请一条线再贴一个答案。
 *
 * 在场＝"启用之日到得了、交棒之日到不了"，含起不含止；只写了启用之日、
 * 没写交棒之日的存量线照长期在场认。屏上摆的在场名单与逐条判出来的名单，
 * 出自同一回计算。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrSignalLineService {

    /** 按主键查询规则（事后翻检走这里；已停用的线也翻得到，但现行求值不捞它） */
    TAdrSignalLine selectTAdrSignalLineById(Long id);

    /**
     * 按代号取线的旧方法，签名不改：按 ruleCode 在 at 时刻在场的那一版判档（1..4）。
     * 该代号此刻无在场版本 / 输入越出中心划段 / 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 同名重载：at 时刻在场诸线里，让线顺位最大者说话（顺位并列取代号靠后那条），
     * 给回钉死线代号与启用之日的求值结论；一条都够不着时写明"无线可依"，
     * 代号与启用之日两栏留空，不拿邻近那条的数凑一个。
     */
    SignalVerdictVo evaluate(BigDecimal input, Date at);

    /** 多规则求值：at 时刻在场线中顺位最高者的档位；无在场线返回 0（与同名重载同一回计算） */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻在场名单（屏上摆的这份）：让线顺位降序、代号降序，与逐条判定同一回计算 */
    List<TAdrSignalLine> listAvailable(Date at);

    /** 单条线在 at 时刻是否在场（批量导入/报表等旁路复用，口径与名单同一回计算） */
    boolean usable(Long id, Date at);

    /** at 时刻在场线条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /**
     * 进门校验＋进账：三条分界数非写不可，空着起不来；比值报成负数、报成中心划的
     * 那一段以外的数，这条线不进账。回执逐栏指名空在哪一栏、越在哪一栏。
     */
    LineIntakeReceiptVo insertTAdrSignalLine(TAdrSignalLine line);
}
