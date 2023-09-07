package com.lkd.http.controller;

import java.time.ZoneId;
import java.util.Date;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.EasyExcelFactory;
import com.lkd.contract.BarCharCollect;
import com.lkd.entity.OrderCollectEntity;
import com.lkd.http.viewModel.BillExportDataVO;
import com.lkd.service.OrderCollectService;
import com.lkd.service.OrderService;
import com.lkd.service.ReportService;
import com.lkd.viewmodel.Pager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/23
 */
@RestController
@RequestMapping("/report")
public class ReportController {
    @Autowired
    private ReportService reportService;
    @Autowired
    private OrderCollectService orderCollectService;

    /*获取一定日期范围之内的合作商分成汇总数据*/
    @GetMapping("/partnerCollect")
    public Pager<OrderCollectEntity> getPartnerCollect(@RequestParam(value = "pageIndex", required = false, defaultValue = "1") Long pageIndex,
                                                       @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
                                                       @RequestParam(value = "partnerName", required = false, defaultValue = "") String partnerName,
                                                       @RequestParam(value = "start", required = true, defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                                       @RequestParam(value = "end", required = true, defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {

        return reportService.getPartnerCollect(pageIndex, pageSize, partnerName, start, end);
    }


    /**
     * 获取最近12条分账信息
     *
     * @param partnerId
     * @return
     */
    @GetMapping("/top12Collect/{partnerId}")
    public List<OrderCollectEntity> getTop12Collect(@PathVariable Integer partnerId) {
        return reportService.getTop12(partnerId);
    }

    /**
     * 合作商搜索分账信息
     *
     * @param partnerId
     * @param pageIndex
     * @param pageSize
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/search/{partnerId}")
    public Pager<OrderCollectEntity> search(
            @PathVariable Integer partnerId,
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Long pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
            @RequestParam(value = "nodeName", required = false, defaultValue = "") String nodeName,
            @RequestParam(value = "start", defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(value = "end", defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return reportService.search(pageIndex, pageSize, partnerId, nodeName, start, end);
    }


    /**
     * 数据导出
     *
     * @param partnerId
     * @param start
     * @param end
     */

    @GetMapping("/export/{partnerId}/{start}/{end}/{nodeName}")
    public void export(@PathVariable(value = "start")
                       @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                       @PathVariable(value = "end")
                       @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
                       @PathVariable(value = "partnerId") Integer partnerId,
                       @PathVariable(value = "nodeName") String nodeName,
                       HttpServletResponse response) {

        List<OrderCollectEntity> list = reportService.getList(partnerId, nodeName, start, end);
        List<BillExportDataVO> exportDataVOS = list.stream().map(item -> {
            BillExportDataVO billExportDataVO = new BillExportDataVO();
            billExportDataVO.setTotalBill(item.getTotalBill());
            billExportDataVO.setNodeName(item.getNodeName());
            billExportDataVO.setOrderCount(item.getOrderCount());
            billExportDataVO.setDate(Date.from(item.getDate().atTime(0, 0).atZone(ZoneId.systemDefault()).toInstant()));
            return billExportDataVO;
        }).collect(Collectors.toList());
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=bill.xlsx");
        try {
            EasyExcel.write(response.getOutputStream(), BillExportDataVO.class).sheet("分账数据")
                    .doWrite(exportDataVOS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 获取合作商一定日期范围的收益情况
     * @param partnerId
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/collectReport/{partnerId}/{start}/{end}")
    public BarCharCollect getCollectReport(@PathVariable Integer partnerId,
                                           @PathVariable  @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                           @PathVariable  @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        return reportService.getCollect(partnerId,start,end);
    }



}
