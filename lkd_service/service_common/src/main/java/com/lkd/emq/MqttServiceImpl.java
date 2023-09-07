package com.lkd.emq;

import com.google.common.base.Strings;
import com.lkd.business.MsgHandler;
import com.lkd.business.MsgHandlerContext;
import com.lkd.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class MqttServiceImpl implements MqttService{
    @Autowired
    private MsgHandlerContext msgHandlerContext;

    /**
     * mqtt消息处理
     * @param topic
     * @param message
     */
    @Override
    public void  processMessage(String topic, MqttMessage message) {
        //EMQ发送数据第一步进入这里
        String msgContent = new String(message.getPayload());
        log.info("接收到消息:"+msgContent);
        try {
            //接受到emq发送的数据，获取msgType的数据赋值msgType
            String msgType = JsonUtil.getValueByNodeName("msgType",msgContent);
            if(Strings.isNullOrEmpty(msgType)) return;
             //调用上下文中的方法获取具体对象地址信息
            MsgHandler msgHandler = msgHandlerContext.getMsgHandler(msgType);
            System.out.println("msgHandler=========>"+msgHandler);
            if(msgHandler == null)return;
            //根据具体对象地址信息返回EMQ数据
            msgHandler.process(msgContent);
        } catch (IOException e) {
            log.error("process msg error,msg is: "+msgContent,e);
        }
    }
}
