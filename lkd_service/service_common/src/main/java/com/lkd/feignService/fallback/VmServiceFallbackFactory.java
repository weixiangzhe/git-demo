package com.lkd.feignService.fallback;

import com.google.common.collect.Lists;
import com.lkd.dto.VmInfoDTO;
import com.lkd.feignService.VMService;
import com.lkd.viewmodel.RegionViewModel;
import com.lkd.viewmodel.SkuViewModel;
import com.lkd.viewmodel.VendingMachineViewModel;
import com.lkd.viewmodel.VmSearch;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class VmServiceFallbackFactory implements FallbackFactory<VMService> {
    @Override
    public VMService create(Throwable throwable) {
        log.error("调用售货机服务失败",throwable);
        return new VMService() {
            @Override
            public List<VmInfoDTO> search(VmSearch vmSearch) {
                return null;
            }

            @Override
            public Integer getNodeCountByOwnerId(Integer ownerId) {
                return null;
            }

            @Override
            public Integer getVmCountByOwnerId(Integer ownerId) {
                return null;
            }

            @Override
            public VendingMachineViewModel getVMInfo(String innerCode) {
                return null;
            }
            @Override
            public void inventory(int percent) {

            }

            @Override
            public List<SkuViewModel> getAllSkuByInnerCode(String innerCode) {
                return Lists.newArrayList();
            }

            @Override
            public SkuViewModel getSku(String innerCode, String skuId) {
                return null;
            }

            @Override
            public SkuViewModel getSkuById(long skuId) {
                return null;
            }

            @Override
            public RegionViewModel getRegionById(String regionId) {
                RegionViewModel viewModel = new RegionViewModel();

                return viewModel;
            }

            @Override
            public String getNodeName(Long id) {
                return null;
            }

            @Override
            public boolean hasCapacity(String innerCode, Long skuId) {
                return false;
            }
        };
    }
}
