package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrSignalLineMapper;
import com.fc.v2.model.auto.TAdrSignalLine;
import com.fc.v2.model.custom.adr.LineIntakeReceiptVo;
import com.fc.v2.model.custom.adr.SignalVerdictVo;
import com.fc.v2.service.ITAdrSignalLineService;

/**
 * 信号检出限值线 Service业务层处理——全辖一处口径的那个求值入口。
 *
 * 在场＝"启用之日到得了、交棒之日到不了"，含起不含止；只写启用之日、
 * 没写交棒之日的存量线照长期在场认；已停用的线不进现行求值（档里留着供事后翻检）。
 * 同一刻在场不止一条时，让线顺位最大的说话；顺位并列取代号靠后那条——
 * 两次求值给回两条不同的线就算错。屏上名单、逐条判定、计数、求值，
 * 全部出自 {@link #inEffectAt(Date)} 这一回计算。
 *
 * 时段比较一律在 Java 侧做（Date 对 Date）：早年把 java.util.Date 直接绑给
 * JDBC 时，serverTimezone 与本机时区不对称，边界判定在切换当日随机错档——
 * 这个坑不许再踩。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrSignalLineServiceImpl implements ITAdrSignalLineService {

    /** 线的情形：现行 */
    private static final int STATUS_CURRENT = 0;
    /** 线的情形：已停用（不进现行求值，档里留着） */
    private static final int STATUS_RETIRED = 1;

    /** 中心划的那一段：比值下限（含） */
    private static final BigDecimal SEGMENT_FLOOR = BigDecimal.ZERO;
    /** 中心划的那一段：比值上限（含） */
    private static final BigDecimal SEGMENT_CEILING = new BigDecimal("100");

    /** 让线顺位大的先说话；顺位并列，代号靠后的说话（确定性，不认随机取法） */
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

    @Override
    public TAdrSignalLine selectTAdrSignalLineById(Long id) {
        return this.adrSignalLineMapper.selectById(id);
    }

    // ---------------- 一处口径：在场判定 ----------------

    /**
     * 这条线在 at 那一刻在不在场。含起不含止：启用之日到得了、交棒之日到不了；
     * 交棒之日空着的存量线照长期在场认；已停用的不进现行求值。
     */
    private boolean inEffect(TAdrSignalLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() == STATUS_RETIRED) {
            return false;
        }
        if (r.getEffStart() == null || at.before(r.getEffStart())) {
            return false;
        }
        return r.getEffEnd() == null || at.before(r.getEffEnd());
    }

    /**
     * at 那一刻的在场名单：全册过同一道在场判定，再按"顺位降序、代号降序"排定。
     * 屏上摆的名单、逐条判定、计数、求值取线，全部出自这一回计算。
     */
    private List<TAdrSignalLine> inEffectAt(Date at) {
        List<TAdrSignalLine> in = new ArrayList<TAdrSignalLine>();
        if (at == null) {
            return in;
        }
        List<TAdrSignalLine> all = this.adrSignalLineMapper.selectList(new QueryWrapper<TAdrSignalLine>());
        for (TAdrSignalLine r : all) {
            if (inEffect(r, at)) {
                in.add(r);
            }
        }
        in.sort(PRIORITY_THEN_CODE_DESC);
        return in;
    }

    // ---------------- 求值入口 ----------------

    /** 按代号取线的旧方法：签名不改，口径换成"at 时刻在场的那一版" */
    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (!inSegment(input)) {
            return 0;
        }
        TAdrSignalLine top = null;
        for (TAdrSignalLine r : inEffectAt(at)) {
            if (ruleCode.equals(r.getRuleCode()) && (top == null || PRIORITY_THEN_CODE_DESC.compare(r, top) < 0)) {
                top = r;
            }
        }
        return top == null ? 0 : levelOf(top, input);
    }

    /** 同名重载：at 时刻在场诸线里顺位最大者说话，结论上钉死代号与启用之日 */
    @Override
    public SignalVerdictVo evaluate(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return SignalVerdictVo.invalid("求值缺件：比值与那一刻都非说不可");
        }
        if (!inSegment(input)) {
            return SignalVerdictVo.invalid("比值越出中心划的那一段[0,100]，不求值");
        }
        List<TAdrSignalLine> in = inEffectAt(at);
        if (in.isEmpty()) {
            return SignalVerdictVo.noLine();
        }
        TAdrSignalLine top = in.get(0);
        return SignalVerdictVo.of(top.getRuleCode(), top.getEffStart(), levelOf(top, input));
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return evaluate(input, at).getLevel();
    }

    @Override
    public List<TAdrSignalLine> listAvailable(Date at) {
        return inEffectAt(at);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        return inEffect(this.adrSignalLineMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return inEffectAt(at).size();
    }

    // ---------------- 进门校验 ----------------

    @Override
    public LineIntakeReceiptVo insertTAdrSignalLine(TAdrSignalLine line) {
        if (line == null) {
            List<String> faults = new ArrayList<String>();
            faults.add("线本身是空的，没东西可进账");
            return LineIntakeReceiptVo.rejected(faults);
        }
        List<String> faults = new ArrayList<String>();
        checkThreshold(faults, "低界比值", "th1Max", line.getTh1Max());
        checkThreshold(faults, "中界比值", "th2Max", line.getTh2Max());
        checkThreshold(faults, "高界比值", "th3Max", line.getTh3Max());
        if (!faults.isEmpty()) {
            return LineIntakeReceiptVo.rejected(faults);
        }
        if (line.getStatus() == null) {
            line.setStatus(STATUS_CURRENT);
        }
        if (line.getDelFlag() == null) {
            line.setDelFlag(0);
        }
        this.adrSignalLineMapper.insert(line);
        return LineIntakeReceiptVo.accepted(line);
    }

    /** 三条分界数非写不可；报成负数、报出中心划的那一段，逐栏指名 */
    private void checkThreshold(List<String> faults, String label, String field, BigDecimal v) {
        if (v == null) {
            faults.add(label + "（" + field + "）空着——三条分界数非写不可");
            return;
        }
        if (v.compareTo(SEGMENT_FLOOR) < 0) {
            faults.add(label + "（" + field + "）报成 " + v.toPlainString() + "——负数不进账");
            return;
        }
        if (v.compareTo(SEGMENT_CEILING) > 0) {
            faults.add(label + "（" + field + "）报成 " + v.toPlainString() + "——越出中心划的那一段[0,100]");
        }
    }

    /** 比值在不在中心划的那一段[0,100]里 */
    private boolean inSegment(BigDecimal input) {
        return input.compareTo(SEGMENT_FLOOR) >= 0 && input.compareTo(SEGMENT_CEILING) <= 0;
    }

    /** 分档：落进哪条界归哪档，越过高界归 4 档；存量线缺哪条界就跳过哪条 */
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
