package com.lkd.business.msgHandler;

import com.lkd.annotations.ProcessType;
import com.lkd.business.MsgHandler;
import org.springframework.stereotype.Component;


/**
 * @Description
 * @Author susu
 * @Date 2023/8/10
 */
@Component
@ProcessType(value = "abc")
public class AbcMsgHandler  implements MsgHandler {


    @Override
    public void process(String jsonMsg)   {
        System.out.println("======>"+jsonMsg);
    }
}
