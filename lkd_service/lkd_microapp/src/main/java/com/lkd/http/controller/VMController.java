package com.lkd.http.controller;


import com.lkd.dto.VmInfoDTO;
import com.lkd.feignService.VMService;
import com.lkd.viewmodel.SkuInfoViewModel;
import com.lkd.viewmodel.SkuViewModel;
import com.lkd.viewmodel.VendingMachineViewModel;
import com.lkd.viewmodel.VmSearch;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vm")
public class VMController {

    @Autowired
    private VMService vmService;


    /**
     * 搜索附近售货机
     * @param vmSearch
     * @return
     */
    @PostMapping("/search")
    public List<VmInfoDTO> search(@RequestBody VmSearch vmSearch){
        return vmService.search(vmSearch);
    }


    /**
     * 获取售货机商品列表
     * @param innerCode
     * @return
     */
    @GetMapping("/skuList/{innerCode}")
    public List<SkuViewModel> getSkuListByIm(@PathVariable("innerCode") String innerCode) {
        return vmService.getAllSkuByInnerCode(innerCode);
    }
    /**
     * 扫码获取商品详情，用来后续支付
     * @param innerCode
     * @param sku
     * @return
     */
    @GetMapping("/sku/{innerCode}/{sku}")
        public SkuInfoViewModel getSku(@PathVariable("innerCode") String innerCode, @PathVariable("sku") String sku) {
        SkuViewModel skuViewModel = vmService.getSku(innerCode, sku);
        SkuInfoViewModel skuInfoViewModel=new SkuInfoViewModel();
        BeanUtils.copyProperties(skuViewModel,skuInfoViewModel);
        VendingMachineViewModel vmInfo = vmService.getVMInfo(innerCode);
        if (!ObjectUtils.isEmpty(vmInfo)){
            skuInfoViewModel.setAddr(vmInfo.getNodeAddr());
            skuInfoViewModel.setInnerCode(innerCode);
        }

        return skuInfoViewModel;

    }

}
