package com.lkd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lkd.contract.VendoutResp;
import com.lkd.entity.OrderEntity;
import com.lkd.http.viewModel.CreateOrderReq;
import com.lkd.http.viewModel.OrderResp;
import com.lkd.viewmodel.OrderViewModel;
import com.lkd.viewmodel.Pager;

import java.util.List;

public interface OrderService extends IService<OrderEntity> {



    /*
     * 获取商圈下销量最好的前10商品
     * */

    List<Long>getTop10Sku(Integer businessId);

    /**
     * 查询订单
     *
     * @param pageIndex
     * @param pageSize
     * @return
     */

    Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo, String start, String end);

    /**
     * 创建订单
     *
     * @param req 请求参数
     * @return 响应结果
     */
    OrderResp createOrder(CreateOrderReq req);

    /**
     * 处理出货结果
     *
     * @param vendoutResp 出货请求参数
     * @return
     */
    boolean vendoutResult(VendoutResp vendoutResp);

    /**
     * 支付完成
     *
     * @param orderNo
     * @param thirdNo
     * @return
     */
    boolean payComplete(String orderNo, String thirdNo);

    /**
     * 支付完成
     *
     * @param orderNo
     * @return
     */
    boolean payComplete(String orderNo);

    /**
     * 通过订单编号获取订单实体
     *
     * @param orderNo
     * @return
     */
    OrderEntity getByOrderNo(String orderNo);

    /**
     * 取消订单
     *
     * @param orderNo
     * @return
     */
    Boolean cancel(String orderNo);

}
