package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fc.v2.common.base.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 值守班表法定节假日历对象 t_adr_cycle_calendar
 *
 * <p>法定节假日整日整场不发声（岁末特殊班表期间不查此表）。
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_adr_cycle_calendar")
@ApiModel(value = "TAdrCycleCalendar", description = "值守班表法定节假日历")
public class TAdrCycleCalendar extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 法定节假日（整日不发声） */
    @TableField("holiday")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "法定节假日")
    private Date holiday;

    /** 假日名 */
    @TableField("day_name")
    @ApiModelProperty(value = "假日名")
    private String dayName;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getHoliday() {
        return holiday;
    }

    public void setHoliday(Date holiday) {
        this.holiday = holiday;
    }

    public String getDayName() {
        return dayName;
    }

    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
