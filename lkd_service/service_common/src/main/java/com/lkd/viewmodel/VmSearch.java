package com.lkd.viewmodel;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/21
 */
@Data
public class VmSearch implements Serializable {
    /**
     * 纬度
     */
    private Double lat;
    /**
     * 经度
     */
    private Double lon;
    /**
     * 搜索半径
     */
    private Integer distance;


}
