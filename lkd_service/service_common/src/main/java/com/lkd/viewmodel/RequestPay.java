package com.lkd.viewmodel;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/17
 */
@Data
public class RequestPay implements Serializable {

    /**
     * 售货机编号
     */
    private String innerCode;

    /**
     * 商品Id
     */
    private String skuId;

}
