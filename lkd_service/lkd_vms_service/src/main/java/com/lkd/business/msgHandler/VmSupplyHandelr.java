package com.lkd.business.msgHandler;

import com.lkd.annotations.ProcessType;
import com.lkd.business.MsgHandler;
import com.lkd.contract.SupplyCfg;
import com.lkd.utils.JsonUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/14
 */
@Component
@ProcessType("vmSupplyResp")
public class VmSupplyHandelr implements MsgHandler {
    @Override
    public void process(String jsonMsg) throws IOException {
        SupplyCfg byJson = JsonUtil.getByJson(jsonMsg, SupplyCfg.class);
        System.out.println(byJson);
    }
}
