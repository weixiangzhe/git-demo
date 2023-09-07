package com.lkd.http.viewModel;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/11
 */
@Data
public class CancelTaskViewModel implements Serializable {
    private Long id;

    /**
     * 拒绝理由
     */
    private String desc;

}
