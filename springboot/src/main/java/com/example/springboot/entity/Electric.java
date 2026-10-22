package com.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 电
 */
@Data
@AllArgsConstructor
@NoArgsConstructor

@TableName(value = "dorm_electric")
public class Electric {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("operatorName")
    private String operatorName;

    @TableField(value = "roomid")
    private Integer roomid;

    @TableField("type")
    private String type;

    @TableField("money")
    private double money;

    @TableField("date")
    private String date;
}
