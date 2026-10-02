package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TAdrReviewBillMapper;
import com.fc.v2.mapper.auto.TAdrReviewSignMapper;
import com.fc.v2.model.auto.TAdrReviewBill;
import com.fc.v2.model.auto.TAdrReviewSign;
import com.fc.v2.model.custom.adr.ReviewAuditVo;
import com.fc.v2.service.ITAdrReviewBillService;
import com.fc.v2.util.StringUtils;

/**
 * 评价结论逐级复核签认单 Service业务层处理（approval-chain 形状：县初核／市复核／省审定）。
 *
 * 由头是去年夏天那桩返工：县级报上来的件，市里一位同志看材料齐了就顺手定了结论，
 * 省里复核发觉该两名点齐的那一口只落了一个名，退回重走，前后误了二十来日——
 * 屏上写「已核」，档里只有一名，落名这件事没人点算。今年立三道口，立两条硬杠杠：
 *
 * 一、已落名数不由人往栏里敲。当前停在哪一道、本口落了几名、并署办法是什么，
 *    全由服务顺着签迹（t_adr_review_sign）一笔一笔点出来交回；界面摆着的数、
 *    经办人自己记住的数都当不得依据。库里单头那几格不作凭，当前道次压根不落库。
 * 二、不许抢步、不许替道。要落名只落在当下这一口：县初核空着，市复核落下的那一笔
 *    无处可落，视同没落；哪一道写下打回，单就钉死在该道，不加签、不改签、不接办。
 * 三、退档要分层。只有省审定认定表述不合规矩时走退档：此前各道本趟署名整批沉底
 *    （一句不毁，但不与新趟补的那批同列——去年有两件混看成同一道落过两回名），
 *    单退回县初核重新起签，趟次加一，再一道一道往上走。
 *
 * 落名栏里两个姓名各归各：立单经手人（opener）一旦落定跟单走到底，不许换人；
 * 每笔签迹的落名人才跟着趟次走，办一次续一名，覆不进立单人那栏，也覆不进别道的格。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrReviewBillServiceImpl implements ITAdrReviewBillService {

    /** 三道口：0县初核 1市复核 2省审定，一道一道往上走 */
    private static final int NODE_COUNTY = 0;
    private static final int NODE_CITY = 1;
    private static final int NODE_PROVINCE = 2;
    private static final int NODE_TOTAL = 3;

    /** 同口并署办法 */
    private static final int MODE_ANY_ONE = 0;
    private static final int MODE_BOTH = 1;

    /** 报批情形 0在核 1已核讫 2已打回 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    /** 落名口 0签认 1打回 */
    private static final int KIND_APPROVE = 0;
    private static final int KIND_REJECT = 1;

    private static final int DEL_NORMAL = 0;
    /** 退档沉底：旧批一句不毁，只沉到底层，不与新趟补的并列给人看 */
    private static final int DEL_SUNK = 1;

    @javax.annotation.Resource
    private TAdrReviewBillMapper adrReviewBillMapper;
    @javax.annotation.Resource
    private TAdrReviewSignMapper adrReviewSignMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrReviewBill openBill(String billNo, String opener) {
        if (StringUtils.isEmpty(billNo) || StringUtils.isEmpty(opener)) {
            return null;
        }
        Integer exists = this.adrReviewBillMapper.selectCount(new QueryWrapper<TAdrReviewBill>()
                .eq("bill_no", billNo));
        if (exists != null && exists > 0) {
            // 同一份报告当年第二件要复核的，当场另立一张，不并进头一张单；重号更不许进门
            return null;
        }
        TAdrReviewBill bill = new TAdrReviewBill();
        bill.setBillNo(billNo);
        // 头一名：立单经手人，一旦落定跟着整张单走到底
        bill.setOpener(opener);
        bill.setRoundNo(1);
        // 当前道次不预写在页面等人回填，库里这一格也留空——停在哪一道由签迹点算
        bill.setNodeNo(null);
        bill.setStatus(STATUS_RUNNING);
        bill.setDelFlag(DEL_NORMAL);
        this.adrReviewBillMapper.insert(bill);
        return headView(bill);
    }

    @Override
    public TAdrReviewBill selectTAdrReviewBillById(Long id) {
        TAdrReviewBill bill = load(id);
        return bill == null ? null : headView(bill);
    }

    @Override
    public TAdrReviewBill approve(Long id, String approver, String comment) {
        // 原有签名不动：不口占并署办法，按「任一人即过」落
        return approve(id, approver, comment, MODE_ANY_ONE, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrReviewBill approve(Long id, String approver, String comment, Integer signMode, Integer needCount) {
        TAdrReviewBill bill = load(id);
        if (bill == null || StringUtils.isEmpty(approver)) {
            return null;
        }
        int round = currentRound(bill);
        List<TAdrReviewSign> live = liveSignsOf(bill.getBillNo(), round);
        List<TAdrReviewSign> eff = effectiveSigns(live);
        if (pinnedNode(live) >= 0) {
            return null; // 写下打回的单钉死在该道：既不往上加签，也不回头改已落的签
        }
        int node = currentNode(eff);
        if (node < 0) {
            return null; // 三道口都走满、已核讫，没有下一口可落
        }
        List<TAdrReviewSign> atNode = signsAt(eff, node, KIND_APPROVE);

        int mode;
        if (atNode.isEmpty()) {
            // 本口头一名落下时，并署办法由本道自己口定；定两名点齐就是两名，定任一人就是一人
            mode = signMode == null ? MODE_ANY_ONE : signMode;
            if (mode != MODE_ANY_ONE && mode != MODE_BOTH) {
                return null;
            }
        } else {
            // 办法随头一笔定死：本口已开过张，再拿另一个数来改口的，这一笔视同没落
            mode = atNode.get(0).getSignMode() == null ? MODE_ANY_ONE : atNode.get(0).getSignMode();
            if (signMode != null && !signMode.equals(mode)) {
                return null;
            }
        }
        int need = mode == MODE_BOTH ? 2 : 1;
        if (needCount != null && !needCount.equals(need)) {
            return null; // 应落名数与办法对不上，不许拿数糊弄
        }
        for (TAdrReviewSign s : atNode) {
            if (approver.equals(s.getSigner())) {
                return null; // 同一个人不许在本口落第二遍——两名点齐得是两个名
            }
        }

        TAdrReviewSign sign = new TAdrReviewSign();
        sign.setBillNo(bill.getBillNo());
        sign.setRoundNo(round);
        sign.setNodeNo(node);
        sign.setSignMode(mode);
        sign.setSigner(approver);
        sign.setActionKind(KIND_APPROVE);
        sign.setComment(comment);
        sign.setDelFlag(DEL_NORMAL);
        this.adrReviewSignMapper.insert(sign);

        // 落没落成只看点算：重数一遍本口有效名数，够了才往上走，不够就留在本口候名
        List<TAdrReviewSign> afterEff = effectiveSigns(liveSignsOf(bill.getBillNo(), round));
        int nowCount = signsAt(afterEff, node, KIND_APPROVE).size();
        boolean nodePassed = nowCount >= need;
        boolean allDone = nodePassed && node == NODE_PROVINCE;
        syncHead(bill, afterEff, allDone ? STATUS_PASS : STATUS_RUNNING);
        return headView(load(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrReviewBill reject(Long id, String approver, String comment) {
        TAdrReviewBill bill = load(id);
        if (bill == null || StringUtils.isEmpty(approver)) {
            return null;
        }
        int round = currentRound(bill);
        List<TAdrReviewSign> live = liveSignsOf(bill.getBillNo(), round);
        if (pinnedNode(live) >= 0) {
            return null; // 已打回的单钉死，不写下第二回打回
        }
        int node = currentNode(effectiveSigns(live));
        if (node < 0) {
            return null; // 已核讫，三道口走完，不得再打回
        }
        TAdrReviewSign sign = new TAdrReviewSign();
        sign.setBillNo(bill.getBillNo());
        sign.setRoundNo(round);
        sign.setNodeNo(node);
        sign.setSignMode(MODE_ANY_ONE);
        sign.setSigner(approver);
        sign.setActionKind(KIND_REJECT);
        sign.setComment(comment);
        sign.setDelFlag(DEL_NORMAL);
        this.adrReviewSignMapper.insert(sign);

        // 哪一道写下打回，这单就钉死在该道：落一档「已打回」，旁道没有替他接下去的份
        this.adrReviewBillMapper.update(null, new UpdateWrapper<TAdrReviewBill>()
                .eq("id", bill.getId())
                .set("status", STATUS_VETO)
                .set("node_no", null));
        return headView(load(id));
    }

    @Override
    public TAdrReviewBill rollback(Long id, String comment) {
        return rollback(id, null, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrReviewBill rollback(Long id, String approver, String comment) {
        TAdrReviewBill bill = load(id);
        if (bill == null) {
            return null;
        }
        int round = currentRound(bill);
        List<TAdrReviewSign> live = liveSignsOf(bill.getBillNo(), round);
        if (pinnedNode(live) >= 0) {
            return null; // 已打回钉死的单，不走分层退档
        }
        int node = currentNode(effectiveSigns(live));
        if (node != NODE_PROVINCE) {
            // 分层退档只开在省审定一道：县、市两道要退只能打回钉死，不许整批抹回县里
            return null;
        }
        // 此前各道攒下的署名整批退档，一句不毁，只沉到底层，不与新趟补的同列给人看
        this.adrReviewSignMapper.update(null, new UpdateWrapper<TAdrReviewSign>()
                .eq("bill_no", bill.getBillNo())
                .eq("round_no", round)
                .eq("del_flag", DEL_NORMAL)
                .set("del_flag", DEL_SUNK));
        // 退档这桩事本身也归旧堆：记下是谁因什么退的，跟着旧批一起沉底
        TAdrReviewSign mark = new TAdrReviewSign();
        mark.setBillNo(bill.getBillNo());
        mark.setRoundNo(round);
        mark.setNodeNo(NODE_PROVINCE);
        mark.setSignMode(MODE_ANY_ONE);
        mark.setSigner(approver);
        mark.setActionKind(KIND_REJECT);
        mark.setComment("省审定退档" + (StringUtils.isEmpty(comment) ? "" : "：" + comment));
        mark.setDelFlag(DEL_SUNK);
        this.adrReviewSignMapper.insert(mark);

        // 单退回县初核重新起签，趟次加一；再一道一道往上走
        this.adrReviewBillMapper.update(null, new UpdateWrapper<TAdrReviewBill>()
                .eq("id", bill.getId())
                .set("round_no", round + 1)
                .set("status", STATUS_RUNNING)
                .set("node_no", null)
                .set("sign_mode", null)
                .set("need_count", null)
                .set("sign_count", null));
        return headView(load(id));
    }

    @Override
    public List<TAdrReviewSign> liveSigns(Long id) {
        TAdrReviewBill bill = load(id);
        if (bill == null) {
            return new ArrayList<>();
        }
        return liveSignsOf(bill.getBillNo(), currentRound(bill));
    }

    @Override
    public ReviewAuditVo audit(Long id) {
        TAdrReviewBill bill = load(id);
        if (bill == null) {
            return new ReviewAuditVo(false, "查无此单", new ArrayList<>());
        }
        List<TAdrReviewSign> live = liveSignsOf(bill.getBillNo(), currentRound(bill));
        List<TAdrReviewSign> eff = effectiveSigns(live);
        if (pinnedNode(live) >= 0) {
            return new ReviewAuditVo(false, "这单写下过打回，钉在半道，谈不上定稿", countsOf(eff));
        }
        int cur = currentNode(eff);
        if (cur >= 0) {
            return new ReviewAuditVo(false, "第" + (cur + 1) + "道口（" + nodeName(cur)
                    + "）名没点齐，这一版没法定稿", countsOf(eff));
        }
        // 从省审定一路往回捋到县初核：每一道都要开了张、名数与办法对得上、道内不许重名
        for (int n = NODE_PROVINCE; n >= NODE_COUNTY; n--) {
            List<TAdrReviewSign> atNode = signsAt(eff, n, KIND_APPROVE);
            if (atNode.isEmpty()) {
                return new ReviewAuditVo(false, nodeName(n) + "一道一个名也没落下，断档了", countsOf(eff));
            }
            int mode = atNode.get(0).getSignMode() == null ? MODE_ANY_ONE : atNode.get(0).getSignMode();
            int need = mode == MODE_BOTH ? 2 : 1;
            if (atNode.size() < need) {
                return new ReviewAuditVo(false, nodeName(n) + "定的是"
                        + (mode == MODE_BOTH ? "两名点齐" : "任一人即过") + "，在册只有"
                        + atNode.size() + "名，对不上", countsOf(eff));
            }
            for (int i = 0; i < atNode.size(); i++) {
                for (int j = i + 1; j < atNode.size(); j++) {
                    if (atNode.get(i).getSigner() != null
                            && atNode.get(i).getSigner().equals(atNode.get(j).getSigner())) {
                        return new ReviewAuditVo(false,
                                nodeName(n) + "同一落名的人点了两回，数重了", countsOf(eff));
                    }
                }
            }
        }
        List<Integer> counts = countsOf(eff);
        return new ReviewAuditVo(true,
                "从省审定捋回县初核，三道口姓名逐条点得齐，共" + sum(counts) + "名，对得拢", counts);
    }

    // ----------------------------------------------------------------------

    private TAdrReviewBill load(Long id) {
        if (id == null) {
            return null;
        }
        TAdrReviewBill bill = this.adrReviewBillMapper.selectById(id);
        if (bill == null || !Integer.valueOf(DEL_NORMAL).equals(bill.getDelFlag())) {
            return null;
        }
        return bill;
    }

    private int currentRound(TAdrReviewBill bill) {
        return bill.getRoundNo() == null ? 1 : bill.getRoundNo();
    }

    /** 本趟在册签迹（退档沉底的旧批不在列），按道次、落库先后排 */
    private List<TAdrReviewSign> liveSignsOf(String billNo, int round) {
        if (StringUtils.isEmpty(billNo)) {
            return new ArrayList<>();
        }
        return this.adrReviewSignMapper.selectList(new QueryWrapper<TAdrReviewSign>()
                .eq("bill_no", billNo)
                .eq("round_no", round)
                .eq("del_flag", DEL_NORMAL)
                .orderByAsc("node_no", "id"));
    }

    /**
     * 点得进去的有效签迹：从县初核起一道一道捋，后一道头一笔落库必须晚于前一道
     * （落库先后与道次对得上才算数）。县初核空着时市复核先落下的那笔、省审定跨过
     * 市复核落的章，落库先后对不上，一笔都点不进去——物理留档供查账，视同没落。
     * 头一口名没点齐处之后各道的笔，同样不点。
     */
    private List<TAdrReviewSign> effectiveSigns(List<TAdrReviewSign> live) {
        List<TAdrReviewSign> effective = new ArrayList<>();
        long prevMaxId = Long.MIN_VALUE;
        for (int n = NODE_COUNTY; n < NODE_TOTAL; n++) {
            List<TAdrReviewSign> linked = new ArrayList<>();
            for (TAdrReviewSign s : signsAt(live, n, KIND_APPROVE)) {
                if (s.getId() != null && s.getId() > prevMaxId) {
                    linked.add(s);
                }
            }
            if (linked.isEmpty()) {
                break; // 这一口接不上：空着或是抢步落下的，往后各道一律点不进去
            }
            effective.addAll(linked);
            int mode = linked.get(0).getSignMode() == null ? MODE_ANY_ONE : linked.get(0).getSignMode();
            int need = mode == MODE_BOTH ? 2 : 1;
            long maxId = prevMaxId;
            for (TAdrReviewSign s : linked) {
                if (s.getId() != null && s.getId() > maxId) {
                    maxId = s.getId();
                }
            }
            if (linked.size() < need) {
                break; // 本口还候着名，后面各道的笔再齐也是抢步
            }
            prevMaxId = maxId;
        }
        return effective;
    }

    private List<TAdrReviewSign> signsAt(List<TAdrReviewSign> signs, int node, int kind) {
        List<TAdrReviewSign> out = new ArrayList<>();
        for (TAdrReviewSign s : signs) {
            if (Integer.valueOf(node).equals(s.getNodeNo())
                    && Integer.valueOf(kind).equals(s.getActionKind())) {
                out.add(s);
            }
        }
        return out;
    }

    /** 写下打回的那一道（没有返回 -1）；打回那笔在册，单就钉死在那道 */
    private int pinnedNode(List<TAdrReviewSign> live) {
        for (TAdrReviewSign s : live) {
            if (Integer.valueOf(KIND_REJECT).equals(s.getActionKind())) {
                return s.getNodeNo() == null ? 0 : s.getNodeNo();
            }
        }
        return -1;
    }

    /**
     * 当前所在道次：顺着有效签迹往上数，头一口名没点齐的就是当下这一道；
     * 三道口都点齐返回 -1（已核讫）。道次从不问屏、不问库，只数签迹；
     * 抢步落下的笔点不进有效签迹，自然也推不动道次。
     */
    private int currentNode(List<TAdrReviewSign> effective) {
        for (int n = NODE_COUNTY; n < NODE_TOTAL; n++) {
            List<TAdrReviewSign> atNode = signsAt(effective, n, KIND_APPROVE);
            if (atNode.isEmpty()) {
                return n; // 这一口一个名也没有（或落了也接不上），停在这一口
            }
            int mode = atNode.get(0).getSignMode() == null ? MODE_ANY_ONE : atNode.get(0).getSignMode();
            int need = mode == MODE_BOTH ? 2 : 1;
            if (atNode.size() < need) {
                return n; // 两名点齐只落了一名：屏上照旧候名，不往上走
            }
        }
        return -1;
    }

    private List<Integer> countsOf(List<TAdrReviewSign> effective) {
        List<Integer> counts = new ArrayList<>();
        for (int n = NODE_COUNTY; n < NODE_TOTAL; n++) {
            counts.add(signsAt(effective, n, KIND_APPROVE).size());
        }
        return counts;
    }

    private static int sum(List<Integer> counts) {
        int s = 0;
        for (Integer c : counts) {
            s += c == null ? 0 : c;
        }
        return s;
    }

    /** 把当前口的规矩与名数同步回单头（当前道次不落库，仍留空；入参须是有效签迹） */
    private void syncHead(TAdrReviewBill bill, List<TAdrReviewSign> eff, int status) {
        int node = currentNode(eff);
        UpdateWrapper<TAdrReviewBill> uw = new UpdateWrapper<TAdrReviewBill>()
                .eq("id", bill.getId())
                .set("status", status)
                .set("node_no", null);
        if (node < 0) {
            // 三道口走满已核讫：本口口占栏一并清空，库里不留半截话
            uw.set("sign_mode", null).set("need_count", null).set("sign_count", 0);
        } else {
            List<TAdrReviewSign> atNode = signsAt(eff, node, KIND_APPROVE);
            int mode = atNode.isEmpty() ? MODE_ANY_ONE
                    : (atNode.get(0).getSignMode() == null ? MODE_ANY_ONE : atNode.get(0).getSignMode());
            uw.set("sign_mode", atNode.isEmpty() ? null : mode)
                    .set("need_count", atNode.isEmpty() ? null : (mode == MODE_BOTH ? 2 : 1))
                    .set("sign_count", atNode.size());
        }
        this.adrReviewBillMapper.update(null, uw);
    }

    /** 单头视图：当前道次、本口办法与名数全部现算，库里那几格写了也不作凭 */
    private TAdrReviewBill headView(TAdrReviewBill src) {
        if (src == null) {
            return null;
        }
        TAdrReviewBill view = new TAdrReviewBill();
        view.setId(src.getId());
        view.setBillNo(src.getBillNo());
        // 立单经手人原样回：他一旦落定跟单走到底，谁也覆不了
        view.setOpener(src.getOpener());
        view.setRoundNo(currentRound(src));
        view.setDelFlag(src.getDelFlag());
        view.setCreateBy(src.getCreateBy());
        view.setCreateTime(src.getCreateTime());
        view.setUpdateBy(src.getUpdateBy());
        view.setUpdateTime(src.getUpdateTime());
        view.setRemark(src.getRemark());

        List<TAdrReviewSign> live = liveSignsOf(src.getBillNo(), currentRound(src));
        List<TAdrReviewSign> eff = effectiveSigns(live);
        int pinned = pinnedNode(live);
        if (pinned >= 0) {
            view.setStatus(STATUS_VETO);
            view.setNodeNo(pinned);
            applyNodeView(view, eff, pinned);
            return view;
        }
        int node = currentNode(eff);
        if (node < 0) {
            view.setStatus(STATUS_PASS);
            view.setNodeNo(NODE_PROVINCE);
            view.setSignMode(null);
            view.setNeedCount(null);
            view.setSignCount(0);
            return view;
        }
        view.setStatus(STATUS_RUNNING);
        view.setNodeNo(node);
        applyNodeView(view, eff, node);
        return view;
    }

    /** 视图上本口的并署办法、应落名数、已落名数：全顺着签迹点，不读库里那几栏 */
    private void applyNodeView(TAdrReviewBill view, List<TAdrReviewSign> live, int node) {
        List<TAdrReviewSign> atNode = signsAt(live, node, KIND_APPROVE);
        int mode = atNode.isEmpty() ? MODE_ANY_ONE
                : (atNode.get(0).getSignMode() == null ? MODE_ANY_ONE : atNode.get(0).getSignMode());
        view.setSignMode(atNode.isEmpty() ? null : mode);
        view.setNeedCount(atNode.isEmpty() ? null : (mode == MODE_BOTH ? 2 : 1));
        view.setSignCount(atNode.size());
    }

    private static String nodeName(int node) {
        switch (node) {
            case NODE_COUNTY:
                return "县初核";
            case NODE_CITY:
                return "市复核";
            case NODE_PROVINCE:
                return "省审定";
            default:
                return "第" + (node + 1) + "道";
        }
    }
}
