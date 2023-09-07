package com.lkd.contract;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/18
 */
@Data
public class OrderCheck extends AbstractContract implements Serializable {

    public OrderCheck() {
        super.setMsgType("orderCheck");
    }

    private String orderNO;
}
