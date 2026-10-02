package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.adr.CycleRoundVo;
import com.fc.v2.service.ITAdrCycleTaskService;
import com.fc.v2.service.ITSysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 *测试类
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	private static final Logger logger = LoggerFactory.getLogger(V2Task.class);

	@Autowired
    private ITSysUserService userService;

	@Autowired
	private ITAdrCycleTaskService adrCycleTaskService;
    /**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }

    /**
     * 定期安全性更新报告催送轮：系统照日子跑，替代人工排日历。
     * Quartz 里配禁并发（QuartzDisallowConcurrentExecution），到点开跑；
     * 空轮回「本轮无到期条目」，属正常一轮，不告警。
     */
    public void runAdrCycleTask()
    {
        CycleRoundVo round = adrCycleTaskService.runRound();
        logger.info("催送轮 {} {}：候催{} 已催出{} 催不成{}",
                round.getRoundNo(), round.message(),
                round.getWaitCount(), round.getDoneCount(), round.getFailCount());
    }
}
