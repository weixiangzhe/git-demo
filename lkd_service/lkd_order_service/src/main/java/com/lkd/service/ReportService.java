package com.lkd.service;

import com.lkd.contract.BarCharCollect;
import com.lkd.contract.BaseContract;
import com.lkd.entity.OrderCollectEntity;
import com.lkd.viewmodel.Pager;

import java.time.LocalDate;
import java.util.List;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/23
 */
public interface ReportService {

    Pager<OrderCollectEntity> getPartnerCollect(Long pageIndex, Long pageSize, String name, LocalDate start, LocalDate end);


    /**
     * 获取合作商前12条点位分账数据
     *
     * @param partnerId
     * @return
     */
    List<OrderCollectEntity> getTop12(Integer partnerId);
    /*
     * 合作商搜索分账信息
     * */

    Pager<OrderCollectEntity> search(Long pageIndex, Long pageSize, Integer partnerId, String nodeName, LocalDate start, LocalDate end);

    /**
     * @param partnerId
     * @param nodeName
     * @param start
     * @param end
     * @description:获取分账数据列表
     */


    List<OrderCollectEntity> getList(Integer partnerId, String nodeName, LocalDate start, LocalDate end);

    /**
     * 获取一定日期内合作商的收益统计
     * @param partnerId
     * @param start
     * @param end
     * @return
     */
    BarCharCollect getCollect(Integer partnerId, LocalDate start, LocalDate end);

}
