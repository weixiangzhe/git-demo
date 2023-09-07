package com.lkd.contract;

import com.google.common.collect.Lists;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/23
 */
@Data
public class BarCharCollect implements Serializable {

    private List<String> xAxis = Lists.newArrayList();
    private List<Integer> yAxis = Lists.newArrayList();
}
