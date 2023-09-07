package com.lkd.business.msgHandler;

import com.google.common.collect.Maps;
import com.lkd.annotations.ProcessType;
import com.lkd.business.MsgHandler;
import com.lkd.business.MsgHandlerContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MsgHandlerContextImp implements ApplicationContextAware, MsgHandlerContext{
    private ApplicationContext ctx;
    private Map<String, MsgHandler> handlerMap = Maps.newHashMap();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
        System.out.println("--------->系统启动1");
        //获取所有的显示MsgHandler的接口的实现类
        Map<String,MsgHandler> map = ctx.getBeansOfType(MsgHandler.class);

        map.values().stream().forEach(v->{
            String msgType = v.getClass().getAnnotation(ProcessType.class).value();
            handlerMap.put(msgType,v);
        });
    }

    public MsgHandler getMsgHandler(String msgType){
        /*
        * 上述方法系统启动就执行到上下文中
        * */
        return handlerMap.get(msgType);
    }
}
