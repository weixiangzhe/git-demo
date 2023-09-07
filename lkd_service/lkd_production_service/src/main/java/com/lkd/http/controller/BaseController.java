package com.lkd.http.controller;
import com.google.common.base.Strings;
import com.lkd.http.view.TokenObject;
import com.lkd.utils.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * controller父类
 */
public class BaseController {

    @Autowired
    private HttpServletRequest request; //自动注入request

    /**
     * 返回用户ID
     * @return
     */
    public Integer getUserId(){
         String token = request.getHeader("Authorization");
        try {
            TokenObject tokenObject = JWTUtil.decode(token);
            if (tokenObject.getUserId() == null) {
                return null;
            } else {
                return tokenObject.getUserId();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 返回用户名称
     * @return
     */
    public String getUserName(){
        return request.getHeader("userName");
    }

    /**
     * 返回登录类型
     * @return
     */
    public Integer getLoginType(){
        String loginType = request.getHeader("loginType");
        if(Strings.isNullOrEmpty(loginType)){
            return null;
        }else {
            return Integer.parseInt(loginType);
        }
    }
}