package com.lkd.business.msgHandler;

import com.lkd.annotations.ProcessType;
import com.lkd.business.MsgHandler;
import com.lkd.config.TopicConfig;
import com.lkd.contract.SupplyCfg;
import com.lkd.emq.MqttProducer;
import com.lkd.service.VendingMachineService;
import com.lkd.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/14
 */
@Component
@ProcessType(value = "supplyResp")
public class SupplyCfgMsgHandler implements MsgHandler {

    @Autowired
    private VendingMachineService vendingMachineService;

    @Autowired
    private MqttProducer mqttProducer;
    @Override
    public void process(String jsonMsg) throws IOException {
        SupplyCfg supplyCfg = JsonUtil.getByJson(jsonMsg, SupplyCfg.class);
        System.out.println("===============>"+supplyCfg);

        //更新售货机的库存
          vendingMachineService.supply(supplyCfg);

        //将信息发送给售货机
        String topic = TopicConfig.TO_VM_TOPIC + supplyCfg.getInnerCode();
        System.out.println(topic);


        supplyCfg.setMsgType("vmSupplyResp");

        mqttProducer.send(topic, 2, supplyCfg);


    }
}
