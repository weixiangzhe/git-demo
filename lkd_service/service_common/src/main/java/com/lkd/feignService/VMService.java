package com.lkd.feignService;

import com.lkd.dto.VmInfoDTO;
import com.lkd.feignService.fallback.VmServiceFallbackFactory;
import com.lkd.viewmodel.RegionViewModel;
import com.lkd.viewmodel.SkuViewModel;
import com.lkd.viewmodel.VendingMachineViewModel;
import com.lkd.viewmodel.VmSearch;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "vm-service",fallbackFactory = VmServiceFallbackFactory.class)
public interface VMService{


    @PostMapping("/vm/search")
     List<VmInfoDTO> search(@RequestBody VmSearch vmSearch);

    @GetMapping("/node/countForOwner/{ownerId}")
    Integer getNodeCountByOwnerId(@PathVariable("ownerId") Integer ownerId);

    @GetMapping("/vm/countByOwner/{ownerId}")
    Integer getVmCountByOwnerId(@PathVariable("ownerId") Integer ownerId);

    @GetMapping("/vm/info/{innerCode}")
    VendingMachineViewModel getVMInfo(@PathVariable("innerCode") String innerCode);

    @GetMapping("/vm/inventory/{percent}")
    void inventory(@PathVariable("percent") int percent);

    @GetMapping("/vm/skuList/{innerCode}")
    List<SkuViewModel> getAllSkuByInnerCode(@PathVariable("innerCode") String innerCode);
    @GetMapping("/vm/sku/{innerCode}/{skuId}")
    SkuViewModel getSku(@PathVariable("innerCode") String innerCode,@PathVariable("skuId") String skuId);
    @GetMapping("/sku/skuViewModel/{skuId}")
    SkuViewModel getSkuById(@PathVariable("skuId") long skuId);

    @GetMapping("/region/regionInfo/{regionId}")
    RegionViewModel getRegionById(@PathVariable("regionId") String regionId);

    @GetMapping("/node/nodeName/{id}")
    String getNodeName(@PathVariable("id") Long id);

    @GetMapping("/vm/hasCapacity/{innerCode}/{skuId}")
     boolean hasCapacity(@PathVariable("innerCode") String innerCode, @PathVariable("skuId") Long skuId);
}
