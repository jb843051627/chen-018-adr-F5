package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrSignalLineMapper;
import com.fc.v2.model.auto.TAdrSignalLine;
import com.fc.v2.model.custom.adr.LineAdmitReceiptVo;
import com.fc.v2.model.custom.adr.SignalVerdictVo;
import com.fc.v2.service.ITAdrSignalLineService;

/**
 * 信号检出限值线 Service业务层（全辖一处口径，旁处没有第二条能绕过的通道）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrSignalLineServiceImpl implements ITAdrSignalLineService {

    /** 中心划定的比值段：入算比值与进门三条分界数都只准落在 [0, 100] */
    private static final BigDecimal RATIO_MIN = BigDecimal.ZERO;
    private static final BigDecimal RATIO_MAX = new BigDecimal("100");

    /** 让线顺位降序；顺位也并列时代号靠后者说话（随机取法一概不认） */
    private static final Comparator<TAdrSignalLine> PRIORITY_THEN_CODE_DESC = new Comparator<TAdrSignalLine>() {
        @Override
        public int compare(TAdrSignalLine a, TAdrSignalLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TAdrSignalLineMapper adrSignalLineMapper;

    /**
     * 时段比较统一走<strong>墙钟字符串</strong>（yyyy-MM-dd HH:mm:ss）：
     * JDBC 的 serverTimezone 与本机时区不对称，直接拿 java.util.Date 比会整体偏移，
     * 使「交棒当日」这类边界用例错判（实测踩到）。库存时刻与入参时刻一律先格式化再比。
     */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TAdrSignalLine selectTAdrSignalLineById(Long id) {
        return this.adrSignalLineMapper.selectById(id);
    }

    @Override
    public SignalVerdictVo evaluate(BigDecimal input, Date at) {
        if (at == null || !ratioInRange(input)) {
            // 入算比值负数/越段：前年那种「照样通过求值」的路今年堵死，不给结论
            return SignalVerdictVo.noLine();
        }
        TAdrSignalLine winner = topPresent(at);
        if (winner == null) {
            // 一条都够不着不兜底，不拿邻近那条的数凑
            return SignalVerdictVo.noLine();
        }
        SignalVerdictVo vo = new SignalVerdictVo();
        vo.setAvailable(true);
        vo.setLevel(levelOf(winner, input));
        vo.setLabel("第" + vo.getLevel() + "档");
        // 代号与启用之日跟着压死在结论上，事后换线不改史；页面只认这两个给回
        vo.setRuleCode(winner.getRuleCode());
        vo.setRuleName(winner.getRuleName());
        vo.setEffStart(winner.getEffStart());
        vo.setTh1Max(winner.getTh1Max());
        vo.setTh2Max(winner.getTh2Max());
        vo.setTh3Max(winner.getTh3Max());
        return vo;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (!ratioInRange(input)) {
            return 0;
        }
        // 与新入口同一回在场名单里按代号取，绝不另走「挑 eff_end 最新」的旧暗道
        for (TAdrSignalLine r : presentAt(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                return levelOf(r, input);
            }
        }
        return 0;
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null || !ratioInRange(input)) {
            return 0;
        }
        TAdrSignalLine winner = topPresent(at);
        return winner == null ? 0 : levelOf(winner, input);
    }

    @Override
    public List<TAdrSignalLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TAdrSignalLine>();
        }
        // 屏上摆的名单就是逐条判出来的名单：同一回计算
        return presentAt(at);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        for (TAdrSignalLine r : presentAt(at)) {
            if (id.equals(r.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return presentAt(at).size();
    }

    @Override
    public LineAdmitReceiptVo admitLine(TAdrSignalLine line) {
        if (line == null) {
            return LineAdmitReceiptVo.reject(
                    java.util.Collections.singletonList("整条线为空"),
                    java.util.Collections.<String>emptyList());
        }
        List<String> blank = new ArrayList<String>();
        List<String> outOfRange = new ArrayList<String>();
        checkThreshold("低界比值(th1_max)", line.getTh1Max(), blank, outOfRange);
        checkThreshold("中界比值(th2_max)", line.getTh2Max(), blank, outOfRange);
        checkThreshold("高界比值(th3_max)", line.getTh3Max(), blank, outOfRange);
        if (!blank.isEmpty() || !outOfRange.isEmpty()) {
            // 空栏/越栏：不进账、不落库，回执逐栏点名
            return LineAdmitReceiptVo.reject(blank, outOfRange);
        }
        if (line.getStatus() == null) {
            line.setStatus(0);
        }
        if (line.getDelFlag() == null) {
            line.setDelFlag(0);
        }
        this.adrSignalLineMapper.insert(line);
        return LineAdmitReceiptVo.ok(line.getId());
    }

    private void checkThreshold(String fieldLabel, BigDecimal v,
                                List<String> blank, List<String> outOfRange) {
        if (v == null) {
            blank.add(fieldLabel + "为空");
            return;
        }
        if (!ratioInRange(v)) {
            outOfRange.add(fieldLabel + "=" + v.toPlainString()
                    + " 越出中心划定段[" + RATIO_MIN.toPlainString() + "," + RATIO_MAX.toPlainString() + "]");
        }
    }

    private boolean ratioInRange(BigDecimal v) {
        return v != null
                && v.compareTo(RATIO_MIN) >= 0
                && v.compareTo(RATIO_MAX) <= 0;
    }

    /** 取线只准走这一处：at 刻在场名单，已按让线顺位/代号排定 */
    private List<TAdrSignalLine> presentAt(Date at) {
        String atText = ts(at);
        List<TAdrSignalLine> present = new ArrayList<TAdrSignalLine>();
        // 已停用/已删的也在档里供事后翻检，但现行这一路不捞——所以只取 status=0、del_flag=0
        List<TAdrSignalLine> all = this.adrSignalLineMapper.selectList(
                new QueryWrapper<TAdrSignalLine>().eq("del_flag", 0).eq("status", 0));
        for (TAdrSignalLine r : all) {
            if (isPresent(r, atText)) {
                present.add(r);
            }
        }
        present.sort(PRIORITY_THEN_CODE_DESC);
        return present;
    }

    /**
     * 在场：启用之日到得了（含起）、交棒之日到不了（不含止）。
     * eff_end 为空照长期在场认——兼容去年存量册里只有启用之日的那几条，不许当不存在。
     */
    private boolean isPresent(TAdrSignalLine r, String atText) {
        if (r.getEffStart() == null) {
            return false;
        }
        if (ts(r.getEffStart()).compareTo(atText) > 0) {
            return false;
        }
        // 交棒当日旧线从当日起不在场：at == eff_end 时不算（含起不含止，一处判两个地方同一个答案）
        return r.getEffEnd() == null || ts(r.getEffEnd()).compareTo(atText) > 0;
    }

    /** 并列了断后的头一条；一条都够不着返回 null（调用方写「无线可依」） */
    private TAdrSignalLine topPresent(Date at) {
        List<TAdrSignalLine> present = presentAt(at);
        return present.isEmpty() ? null : present.get(0);
    }

    /** 分档：等于上限取高一档 */
    private int levelOf(TAdrSignalLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }
}
