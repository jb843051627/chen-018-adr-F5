package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAdrSignalLine;
import com.fc.v2.model.custom.adr.LineAdmitReceiptVo;
import com.fc.v2.model.custom.adr.SignalVerdictVo;

/**
 * 信号检出限值线 Service（全辖一处口径：取线只准走这一处）。
 *
 * <p>在场口径（含起不含止，全辖统一，不许翻单子时算进去、回写时又不算）：
 * 未删 + 现行(status=0) + 启用之日到得了(eff_start &lt;= at)
 * + 交棒之日到不了(eff_end 为空，或 at &lt; eff_end)。交棒当日旧线不在场、新线在场。
 * 并列了断：让线顺位(priority)最大者说话；顺位也一样，取代号(rule_code)靠后者。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrSignalLineService {

    /** 按主键查询规则（旧方法，签名不改） */
    TAdrSignalLine selectTAdrSignalLineById(Long id);

    /**
     * <b>唯一的求值入口</b>：按 at 这一刻在场的线求结论。
     * 在场是哪一条、该取哪一册的界、钉死的代号与启用之日，全由本方法现算现给；
     * 页面不许自己请一条线再贴一个答案。
     *
     * <p>一条都够不着时不兜底：available=false、文案「无线可依」，代号与启用之日留空。
     * 入算比值报成负数、越出中心划定段[0,100]的，同样不给结论（前年旧案，今年堵死）。
     * 结论钉死线代号与启用之日，事后换线不改史。
     */
    SignalVerdictVo evaluate(BigDecimal input, Date at);

    /**
     * 旧求值方法，签名原样保留：按 ruleCode 取 at 时刻在场的那一条判档（1..4）。
     * 无在场版本 / 输入越界 / 参数缺失一律返回 0。内部仍走同一回在场计算，绕不过新入口。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 旧求值方法，签名原样保留：at 时刻在场线中顺位最高者的档位；无在场线返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻在场名单（让线顺位降序、代号降序）；屏上名单与逐条判定出自同一回计算 */
    List<TAdrSignalLine> listAvailable(Date at);

    /** 单条线在 at 时刻是否在场（批量导入/报表等旁路复用，口径与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻在场线条数（列表页角标 / 看板），与名单同源 */
    int countAvailable(Date at);

    /**
     * 新线进门：三条分界数非写不可，空着起不来；比值为负、越出中心划定段[0,100]的不进账。
     * 回执逐栏指名空在哪一栏、越在哪一栏；拒收时一行不落库。
     */
    LineAdmitReceiptVo admitLine(TAdrSignalLine line);
}
