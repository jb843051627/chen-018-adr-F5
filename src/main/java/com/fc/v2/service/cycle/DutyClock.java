package com.fc.v2.service.cycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrCycleCalendarMapper;
import com.fc.v2.model.auto.TAdrCycleCalendar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 值守班表时钟：一天照班切成段，界外整场不发声。
 *
 * <p>日常切法：夜里（22:00 到次日 08:00）整场不发声；法定节假日整日整场不发声。
 * 段外就算条目到期也只压着，候下一轮开口。
 *
 * <p>岁末半个月（12/16—12/31）不走上面那个切法，另排两段：上午 08:00—12:00、
 * 下午 14:00—18:00；这半个月不查节假日历，正午与晚间同样压着。
 *
 * <p>翻单点时段与回写都只问这一个钟，别处不得另算。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Component
public class DutyClock {

    /** 日常白班起 */
    public static final int DAY_OPEN_HOUR = 8;
    /** 日常夜班起（22:00 起整场不发声） */
    public static final int NIGHT_OPEN_HOUR = 22;

    /** 岁末另排：上午段 08:00—12:00，下午段 14:00—18:00 */
    public static final int YEAREND_MORNING_OPEN = 8;
    public static final int YEAREND_MORNING_CLOSE = 12;
    public static final int YEAREND_AFTERNOON_OPEN = 14;
    public static final int YEAREND_AFTERNOON_CLOSE = 18;

    /** 岁末半个月：12 月 16 日起、31 日止 */
    public static final int YEAREND_MONTH = 12;
    public static final int YEAREND_START_DAY = 16;

    @javax.annotation.Resource
    private TAdrCycleCalendarMapper calendarMapper;

    /** 这一会儿在不在可开口的班段里（界外＝只压着） */
    public boolean isVocal(LocalDateTime at) {
        int h = at.getHour();
        if (isYearEnd(at.toLocalDate())) {
            // 岁末另排两段，不查节假日历，不走夜里那个切法
            return (h >= YEAREND_MORNING_OPEN && h < YEAREND_MORNING_CLOSE)
                    || (h >= YEAREND_AFTERNOON_OPEN && h < YEAREND_AFTERNOON_CLOSE);
        }
        if (isHoliday(at.toLocalDate())) {
            return false; // 法定节假日整日整场不发声
        }
        return h >= DAY_OPEN_HOUR && h < NIGHT_OPEN_HOUR;
    }

    /** 压住缘故（落台账 note 用） */
    public String silentReason(LocalDateTime at) {
        if (isYearEnd(at.toLocalDate())) {
            return "岁末班段外，压着候下一轮开口";
        }
        if (isHoliday(at.toLocalDate())) {
            return "法定节假日整场不发声，压着候下一轮开口";
        }
        return "夜间整场不发声，压着候下一轮开口";
    }

    /** 岁末那半个月（含 16 日与 31 日） */
    public boolean isYearEnd(LocalDate date) {
        return date.getMonthValue() == YEAREND_MONTH && date.getDayOfMonth() >= YEAREND_START_DAY;
    }

    /** 当日是不是法定节假日（岁末半个月另排，不查此历） */
    public boolean isHoliday(LocalDate date) {
        if (isYearEnd(date)) {
            return false;
        }
        if (this.calendarMapper == null) {
            return false;
        }
        Integer cnt = this.calendarMapper.selectCount(new QueryWrapper<TAdrCycleCalendar>()
                .eq("holiday", java.sql.Date.valueOf(date))
                .eq("del_flag", 0));
        return cnt != null && cnt > 0;
    }

    /** 轮次执行统一以此取此刻（生产挂 Quartz 时由任务传入 new Date()） */
    public static LocalDateTime toLocal(Date at) {
        return LocalDateTime.ofInstant(at.toInstant(), java.time.ZoneId.systemDefault());
    }
}
