package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TAdrReviewBill;
import com.fc.v2.model.auto.TAdrReviewSign;
import com.fc.v2.model.custom.adr.ReviewAuditVo;

/**
 * 评价结论逐级复核签认单 Service接口（approval-chain 形状：县初核／市复核／省审定三道口）。
 *
 * 今年立下的规矩：一张单只有签认、打回两个入口，都在这里；屏面上不另设能签、能退、
 * 能改署名的第二处。当前道次不预写，本口已落几名不由人敲——一律由服务顺着签迹
 * 一笔一笔点出来交回，界面摆着的数、经办人心里记的数都作不得凭。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrReviewBillService {

    /** 立单：单号＋立单经手人（头一名，落定即跟单走到底，不许换人）；单号在册立第二张返回 null */
    TAdrReviewBill openBill(String billNo, String opener);

    /** 按主键回查单据（当前道次、并署办法、应落／已落名数均为现算视图，库里那几格不作凭） */
    TAdrReviewBill selectTAdrReviewBillById(Long id);

    /**
     * 签认一票（原有签名不动）：不口占并署办法时，按「任一人即过」落。
     * 返回更新后的单据；抢步、同名重落、已打回、已核讫一律返回 null，这一笔视同没落。
     */
    TAdrReviewBill approve(Long id, String approver, String comment);

    /**
     * 签认一票（同名重载：换一种签法）：本口头一名落下时口定本口并署办法与应落名数，
     * 0=任一人即过（应落一名），1=两名点齐（应落两名）；办法随头一笔定死，
     * 本道再签时拿另一个数来改口的，返回 null。
     */
    TAdrReviewBill approve(Long id, String approver, String comment, Integer signMode, Integer needCount);

    /** 否决（打回）：哪一道写下打回，这单就钉死在该道；返回更新后的单据，被拒返回 null */
    TAdrReviewBill reject(Long id, String approver, String comment);

    /**
     * 省审定分层退档（原有签名不动）：只许停在省审定那一道时走；此前各道本趟署名整批退档
     * 沉底（一句不毁，不与新趟补的并列），单退回县初核重新起签，趟次加一。被拒返回 null。
     */
    TAdrReviewBill rollback(Long id, String comment);

    /** 省审定分层退档重载：记下落退档话的省审定经手人 */
    TAdrReviewBill rollback(Long id, String approver, String comment);

    /** 本单在册签迹（退档沉底的不摆进来），按道次、落库先后排——倒读点算就点它 */
    List<TAdrReviewSign> liveSigns(Long id);

    /**
     * 倒读核对：最终定稿那一版，从省审定一路往回捋到县初核，各级姓名逐条清点，
     * 总数与各道名数都对得拢才算过；单拿出任意一道也要经得起复核。
     */
    ReviewAuditVo audit(Long id);
}
