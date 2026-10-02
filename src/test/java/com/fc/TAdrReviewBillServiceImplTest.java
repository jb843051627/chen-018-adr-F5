package com.fc;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.model.auto.TAdrReviewBill;
import com.fc.v2.model.auto.TAdrReviewSign;
import com.fc.v2.model.custom.adr.ReviewAuditVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评价结论三道口签认规矩集成测试——去年夏天那桩返工立下的规矩：
 * 县初核／市复核／省审定一道一道往上走；任一人即过与两名点齐由本道头一笔定死；
 * 已落名数只顺着签迹点，屏上与库里写花都不作凭；抢步视同没落；打回钉死；
 * 省审定退档整批沉底、两堆不混列；立单经手人与各趟记录人各归各栏；
 * 走到头从省往县倒读，总数与各道名数对得拢。
 */
class TAdrReviewBillServiceImplTest extends AdrReviewBillTestBase {

    /** 库里那行单头（不经视图） */
    private TAdrReviewBill rawBill(Long id) {
        return billMapper.selectById(id);
    }

    /** 直插一笔在册签迹（绕开服务，模拟有人往档里硬塞——去年「屏上已核、档里一名」那种脏账） */
    private void rawSign(String billNo, int round, int node, int mode, String signer) {
        TAdrReviewSign s = new TAdrReviewSign();
        s.setBillNo(billNo);
        s.setRoundNo(round);
        s.setNodeNo(node);
        s.setSignMode(mode);
        s.setSigner(signer);
        s.setActionKind(0);
        s.setDelFlag(0);
        signMapper.insert(s);
    }

    private long countSigns(String billNo, int round, int delFlag) {
        return signMapper.selectCount(new QueryWrapper<TAdrReviewSign>()
                .eq("bill_no", billNo).eq("round_no", round).eq("del_flag", delFlag));
    }

    // ---------------- 立单 ----------------

    @Test
    void 立单_头一名经手人跟单走到底_当前道次不预写() {
        TAdrReviewBill b = service.openBill("Q-001", "县经办·老周");
        assertNotNull(b);
        assertEquals("县经办·老周", b.getOpener());
        assertEquals(1, b.getRoundNo().intValue());
        // 新单停在县初核（视图现算），但库里当前道次那格空着等人回填是不做的
        assertEquals(0, b.getNodeNo().intValue());
        assertEquals(0, b.getStatus().intValue());
        assertNull(rawBill(b.getId()).getNodeNo(), "当前道次不落库");
        // 同号不许立第二张；当年第二件另立新号
        assertNull(service.openBill("Q-001", "别人"));
        assertNotNull(service.openBill("Q-002", "县经办·老周"));
    }

    // ---------------- 任一人即过：三道口各一名走到底 ----------------

    @Test
    void 任一人即过_头一名落下就过口_三道走完核讫() {
        Long id = service.openBill("A-1", "立单人").getId();
        assertEquals(0, service.selectTAdrReviewBillById(id).getNodeNo().intValue());

        assertNotNull(service.approve(id, "县·甲", "县初核讫"));
        assertEquals(1, service.selectTAdrReviewBillById(id).getNodeNo().intValue(), "县一名即过，到市复核");
        assertNotNull(service.approve(id, "市·甲", "市复核讫"));
        assertEquals(2, service.selectTAdrReviewBillById(id).getNodeNo().intValue());
        TAdrReviewBill done = service.approve(id, "省·甲", "省审定讫");
        assertNotNull(done);
        assertEquals(1, done.getStatus().intValue(), "省落章即核讫");
        assertEquals(2, done.getNodeNo().intValue());
        // 核讫之后没有下一口
        assertNull(service.approve(id, "省·甲", "还想签"));
        assertNull(service.reject(id, "省·甲", "事后找茬"));

        ReviewAuditVo audit = service.audit(id);
        assertTrue(audit.isPassed(), audit.getMessage());
        assertEquals(java.util.Arrays.asList(1, 1, 1), audit.getNodeCounts());
        assertEquals(3, audit.getTotalCount(), "县1市1省1，总数3名");
    }

    // ---------------- 两名点齐：候名、改法、重名都拦 ----------------

    @Test
    void 两名点齐_只落一名照旧候名_满两名才过() {
        Long id = service.openBill("B-1", "立单人").getId();
        // 县初核定成两名点齐（重载口占）
        TAdrReviewBill first = service.approve(id, "县·甲", "头名", 1, 2);
        assertNotNull(first);
        assertEquals(0, first.getNodeNo().intValue(), "两名点齐只落一名，停在县初核候名");
        assertEquals(2, first.getNeedCount().intValue());
        assertEquals(1, first.getSignCount().intValue());
        assertEquals(1, first.getSignMode().intValue());

        // 本口已开张，再改口说任一人即过——不认
        assertNull(service.approve(id, "县·乙", "想改办法", 0, 1));
        // 应落名数与办法对不上也不认
        assertNull(service.approve(id, "县·乙", "拿数糊弄", 1, 3));
        // 同一个人不许在本口落第二遍
        assertNull(service.approve(id, "县·甲", "自己凑数", 1, 2));
        assertEquals(1, service.selectTAdrReviewBillById(id).getSignCount().intValue(), "那几笔全视同没落");

        // 第二名落下才过口
        TAdrReviewBill second = service.approve(id, "县·乙", "二名齐", 1, 2);
        assertNotNull(second);
        assertEquals(1, second.getNodeNo().intValue(), "两名点齐，进市复核");
    }

    @Test
    void 不口占办法的旧签名_按任一人即过落() {
        Long id = service.openBill("B-2", "立单人").getId();
        // 原有三参签名不动：默认任一人即过
        assertNotNull(service.approve(id, "县·甲", null));
        assertEquals(1, service.selectTAdrReviewBillById(id).getNodeNo().intValue());
    }

    // ---------------- 抢步视同没落（即便前道后来补齐，那笔也永不复活） ----------------

    @Test
    void 不许抢步_县市空着的那笔点不进去_补了前道也不复活() {
        String no = "C-1";
        Long id = service.openBill(no, "立单人").getId();
        // 县初核空着，有人绕过服务往档里替市复核落了一笔（去年事故的形状）
        rawSign(no, 1, 1, 0, "市·抢步");

        TAdrReviewBill view = service.selectTAdrReviewBillById(id);
        assertEquals(0, view.getNodeNo().intValue(), "市那笔视同没落，单还停在县初核");
        assertEquals(0, view.getSignCount().intValue(), "点签迹点不到那笔，本口已落名数仍是0");

        // 县初核正经落下一名
        assertNotNull(service.approve(id, "县·甲", "补县"));
        // 市里早先抢步那笔落库在前，永远点不进去——单停在市复核候着新名
        view = service.selectTAdrReviewBillById(id);
        assertEquals(1, view.getNodeNo().intValue());
        assertEquals(0, view.getSignCount().intValue(), "抢步的旧账不算数，市口已落名数0");

        // 省审定跨过市复核直接落章，同样视同没落：先把市补正经
        rawSign(no, 1, 2, 0, "省·跨章"); // 市还空着时省先落章
        assertNotNull(service.approve(id, "市·甲", "补市"));
        view = service.selectTAdrReviewBillById(id);
        assertEquals(2, view.getNodeNo().intValue(), "到了省审定，但省那笔跨章是抢步");
        assertEquals(0, view.getSignCount().intValue());
        assertNotNull(service.approve(id, "省·甲", "省正经落章"));
        assertEquals(1, service.selectTAdrReviewBillById(id).getStatus().intValue());

        // 倒读只点正经三笔，两笔抢步物理留档但不计数
        ReviewAuditVo audit = service.audit(id);
        assertTrue(audit.isPassed(), audit.getMessage());
        assertEquals(3, audit.getTotalCount());
        assertEquals(5, countSigns(no, 1, 0), "抢步那两笔没毁，还在档里供查账");
    }

    // ---------------- 打回钉死 ----------------

    @Test
    void 哪道打回钉死在哪道_不加签不改签不接办() {
        Long id = service.openBill("D-1", "立单人").getId();
        assertNotNull(service.approve(id, "县·甲", null));
        TAdrReviewBill rejected = service.reject(id, "市·驳回人", "材料不对");
        assertNotNull(rejected);
        assertEquals(2, rejected.getStatus().intValue(), "已打回");
        assertEquals(1, rejected.getNodeNo().intValue(), "钉在市复核那一道");

        // 事后既不往上加签、也不回头改已落的签，旁道接不了
        assertNull(service.approve(id, "市·甲", "想补签"));
        assertNull(service.approve(id, "省·甲", "省里替他接"));
        assertNull(service.reject(id, "市·乙", "再打一回"));
        // 市这一层不是省审定，分层退档也不开
        assertNull(service.rollback(id, "退回县里"));
        // 县初核那一名原样没动
        assertEquals(1, service.liveSigns(id).stream()
                .filter(s -> s.getNodeNo() == 0).count());
    }

    // ---------------- 省审定分层退档：整批沉底、两堆分列、重走一遍 ----------------

    @Test
    void 省审定退档_旧批整批沉底_退回县初核重签_两堆不混() {
        String no = "E-1";
        Long id = service.openBill(no, "立单人").getId();
        // 县两名点齐、市任一人——头一趟走到省审定那一口（省尚未落章）
        assertNotNull(service.approve(id, "县·甲", null, 1, 2));
        assertNotNull(service.approve(id, "县·乙", null, 1, 2));
        assertNotNull(service.approve(id, "市·甲", null));
        assertEquals(2, service.selectTAdrReviewBillById(id).getNodeNo().intValue());

        // 省审定认定表述不合规矩：整批退档
        TAdrReviewBill back = service.rollback(id, "省·审定官", "结论表述不合规矩");
        assertNotNull(back);
        assertEquals(0, back.getNodeNo().intValue(), "退回县初核重新起签");
        assertEquals(0, back.getStatus().intValue());
        assertEquals(2, back.getRoundNo().intValue(), "趟次加一");

        // 旧批（3笔署名＋1笔退档标记）整批沉底，一句不毁；新趟在册一笔没有
        assertEquals(4, countSigns(no, 1, 1), "旧批连退档标记都沉在旧堆");
        assertEquals(0, countSigns(no, 2, 0), "新堆还是空的");
        assertTrue(service.liveSigns(id).isEmpty(), "屏上只见新堆，旧批不并列给人看");
        // 退档后没打回钉死，还能重签
        assertNotNull(service.approve(id, "县·新一", null));
    }

    @Test
    void 退档后重走一遍_倒读只点新趟_旧批不混看成一道两名() {
        String no = "E-2";
        Long id = service.openBill(no, "立单人").getId();
        assertNotNull(service.approve(id, "县·甲", null, 1, 2));
        assertNotNull(service.approve(id, "县·乙", null, 1, 2));
        assertNotNull(service.approve(id, "市·旧", null));
        // 停在省审定时省退档：旧堆县2、市1
        assertNotNull(service.rollback(id, "省·官", "表述不合"));

        // 第二趟：县仍两名点齐、市一、省一，一道一道重新走
        assertNotNull(service.approve(id, "县·丙", null, 1, 2));
        assertNotNull(service.approve(id, "县·丁", null, 1, 2));
        assertNotNull(service.approve(id, "市·新", null));
        assertNotNull(service.approve(id, "省·新", null));

        TAdrReviewBill done = service.selectTAdrReviewBillById(id);
        assertEquals(1, done.getStatus().intValue());
        ReviewAuditVo audit = service.audit(id);
        assertTrue(audit.isPassed(), audit.getMessage());
        // 去年混看的形状：同一道落过两回名。这里旧堆3笔署名、新堆4笔各归各堆，
        // 倒读只数本趟在册：县2、市1、省1，共4名，绝不把旧堆那名并进来
        assertEquals(java.util.Arrays.asList(2, 1, 1), audit.getNodeCounts());
        assertEquals(4, audit.getTotalCount());
        assertEquals(0, countSigns(no, 1, 0), "旧批署名一笔也不在册，全沉了底");
        assertEquals(4, countSigns(no, 2, 0), "新堆4笔在册");
    }

    @Test
    void 退档只开在省审定_县市两层退不动整批() {
        Long id = service.openBill("E-3", "立单人").getId();
        assertNull(service.rollback(id, "县里就想退"), "县初核没到省，分层退档不开");
        assertNotNull(service.approve(id, "县·甲", null, 1, 2));
        assertNotNull(service.approve(id, "县·乙", null, 1, 2));
        assertNull(service.rollback(id, "市里想整批退"), "市复核也退不动，只能打回钉死");
        assertEquals(1, service.selectTAdrReviewBillById(id).getNodeNo().intValue());
    }

    // ---------------- 栏里写花不作凭：只认点算交回 ----------------

    @Test
    void 屏上与库里写花都不作凭_签没签成只看服务交回() {
        String no = "F-1";
        Long id = service.openBill(no, "立单人").getId();
        // 一笔没签，有人把单头栏里写成「省审定、已落99名、已核讫」
        billMapper.update(null, new UpdateWrapper<TAdrReviewBill>()
                .eq("id", id)
                .set("node_no", 2).set("sign_count", 99)
                .set("sign_mode", 1).set("need_count", 2).set("status", 1));

        TAdrReviewBill view = service.selectTAdrReviewBillById(id);
        assertEquals(0, view.getNodeNo().intValue(), "当前道次以签迹点算为准：还在县初核");
        assertEquals(0, view.getStatus().intValue(), "写了已核讫也不算核讫");
        assertEquals(0, view.getSignCount().intValue(), "99名是假的，点出来是0");
        assertNull(view.getSignMode(), "本口没开张，办法也无");

        // 签认照常落在县初核，服务没被写花的栏骗到
        assertNotNull(service.approve(id, "县·甲", null));
        assertEquals(1, service.selectTAdrReviewBillById(id).getNodeNo().intValue());
        assertFalse(service.audit(id).isPassed(), "没走完就是没走完");
    }

    // ---------------- 立单经手人与各趟记录人两栏不覆 ----------------

    @Test
    void 立单经手人跟单走到底_退档复签也换人不了_记录人各归各笔() {
        String no = "G-1";
        Long id = service.openBill(no, "头一名·老周").getId();
        assertNotNull(service.approve(id, "县·甲", null));
        assertNotNull(service.approve(id, "市·甲", null));
        assertNotNull(service.rollback(id, null, "省退"), "走到省审定才退得动");
        // 第二趟换了一批人办，立单经手人仍是头一名
        TAdrReviewBill b2 = service.approve(id, "县·新一", null);
        assertNotNull(b2);
        assertEquals("头一名·老周", b2.getOpener(), "头一名一旦落定，再录多少回也轮不着他改人");
        assertEquals("头一名·老周", rawBill(id).getOpener());

        // 每笔签迹的记录人跟自己那趟、那道走，覆不进别人的格
        List<TAdrReviewSign> round2 = signMapper.selectList(new QueryWrapper<TAdrReviewSign>()
                .eq("bill_no", no).eq("round_no", 2).eq("del_flag", 0));
        assertEquals(1, round2.size());
        assertEquals("县·新一", round2.get(0).getSigner());
        assertEquals(0, round2.get(0).getNodeNo().intValue());
        List<TAdrReviewSign> round1 = signMapper.selectList(new QueryWrapper<TAdrReviewSign>()
                .eq("bill_no", no).eq("round_no", 1));
        assertTrue(round1.stream().anyMatch(s -> "县·甲".equals(s.getSigner())));
        assertTrue(round1.stream().noneMatch(s -> "县·新一".equals(s.getSigner())),
                "后一道/后一趟的记录人覆不进前一道的格");
    }

    // ---------------- 单拿出任意一道也经得起复核 ----------------

    @Test
    void 倒读_任意一道缺名或重名都判没走成() {
        // 县两名点齐只走到一名时，倒读点出县口缺一名
        String no = "H-1";
        Long id = service.openBill(no, "立单人").getId();
        assertNotNull(service.approve(id, "县·甲", null, 1, 2));
        ReviewAuditVo mid = service.audit(id);
        assertFalse(mid.isPassed());
        assertEquals(java.util.Arrays.asList(1, 0, 0), mid.getNodeCounts(), "县1、市0、省0，点得清清楚楚");
    }
}
