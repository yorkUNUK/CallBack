package com.example.callback.api.controller;

import com.example.callback.common.controller.BaseController;
import com.example.callback.common.model.APIResponse;
import com.example.callback.api.client.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSONObject;
import com.example.callback.api.decrypt.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * User: York
 * Date: 2021/12/28
 * Time: 11:50
 */

@RestController
public class callback extends BaseController{

    //logback
    private final Logger logger =  LoggerFactory.getLogger(this.getClass());
    public static final Marker ultimate = MarkerFactory.getMarker("ULTIMATE");
    public static final Marker openapi = MarkerFactory.getMarker("OPENAPI");
    public static final Marker bestSign = MarkerFactory.getMarker("BESTSIGN");
    public static final String CONTRACT_SEND_RESULT = "CONTRACT_SEND_RESULT"; //合同发送成功
    @Value("${message.token}")
    private String token;
    @Value("${message.aesKey}")
    private String aesKey;
    @Value("${message.encrypt}")
    private int message_encrypt;
    @Value("${sign.auto}")
    private int signAuto;
    @Value("${sign.clientId}")
    private String signClientId;
    @Value("${sign.host}")
    private String signHost;
    @Value("${sign.clientSecret}")
    private String signClientSecret;
    @Value("${sign.privateKey}")
    private String signPrivateKey;
    @Value("${sign.enterpriseName}")
    private String signEnterpriseName;
    @Value("${sign.account}")
    private String signAccount;
    @Value("${sign.bizName}")
    private String signBizName;
    @Value("${sign.sealName}")
    private String signSealName;
    @Value("${sign.pushUrl}")
    private String signPushUrl;

    @RequestMapping(value = "/callback", method = RequestMethod.POST)
    public APIResponse receiveMessage(@RequestBody JSONObject obj, HttpServletRequest request) {
        /**
         * 工具版异步通知
         */
        // 工具版异步通知验签
        if (obj.containsKey("action")) {
            if (signClientSecret != "") {
                logger.info(openapi, "开始验签");
                String accessKey = signClientSecret;
                String httpbody = obj.toJSONString();
                String rtick = request.getHeader("rtick");
                String sign = request.getHeader("sign");
                String calSign = MD5Utils.stringMD5(MD5Utils.stringMD5(httpbody) + rtick + accessKey);

                if (sign.equals(calSign)) {
                    logger.info(openapi, "工具版异步通知验签成功");
                    if (obj.containsKey("action")) {
                        logger.info(openapi, "接收到工具版异步通知：" + obj);
                        return this.success();
                    }
                } else {
                    logger.error(openapi, "工具版异步通知验签失败");
                    return this.fail("验签失败");
                }
            } else {
                logger.info(openapi, "未提供验签的accesskey默认不进行验签，通知直接存入logs");
                logger.info(openapi, "接收到工具版异步通知：" + obj);
                return this.success();
            }
            return this.fail("未知异常");
        }
        /**
         * 旗舰版异步通知
         */
        else if(obj.containsKey("type")) {
            JSONObject responseData;
            String clientId;
            /**
             * 判断异步通知是否加密
             */
            if (obj.containsKey("encryptMsg") && (message_encrypt == 1)) {
                logger.info(ultimate, "接收到旗舰版加密的异步通知：" + obj);
                //加密的情况
                SSQAESEncrypt decrypt = SSQAESEncrypt.instanceOf(token, aesKey);
                SSQAESEncrypt.DecryptedContentAndClientId decryptedContentAndClientId = decrypt.decryptReturnValueContainsClientId(obj.getString("encryptMsg"));
                String decryptedContent = decryptedContentAndClientId.getPlanContent();
                clientId = decryptedContentAndClientId.getFromClientId();
                logger.info(ultimate, "解密后的responseData: " + decryptedContent);
                logger.info(ultimate, "解密后的开发者ID: " + clientId);
                responseData = JSONObject.parseObject(decryptedContent);
            } else if (obj.containsKey("responseData") && (message_encrypt == 0)) {
                //不加密的情况
                logger.info(ultimate, "接收到旗舰版异步通知：" + obj);
                responseData = obj.getJSONObject("responseData");
                clientId = obj.getString("clientId");
            } else {
                return this.fail("请确认异步通知的加密情况！");
            }
            /**
             * 判断是否企业自动签
             */
            if (obj.getString("type").equals(CONTRACT_SEND_RESULT)){
                logger.info(bestSign, "开始尝试自动签署 合同：" + responseData.get("contractId"));
                if (signAuto == 1 && signClientId != null) {
                    if(clientId.equals(signClientId)){
                        String contractId = String.valueOf(responseData.get("contractId"));
                        if(contractId != null) {
                            JSONObject requestBody = getRequestBody(contractId);
                            String result = sign(requestBody);
                            JSONObject resultObj = JSONObject.parseObject(result);
                            if(result != null) {
                                try {
                                    if (resultObj.getJSONObject("data").getIntValue("failureAmount") != 0) {
                                        logger.error(bestSign, resultObj.getJSONObject("data").get("errorInfos").toString());
                                    } else {
                                        logger.info(bestSign, "自动签署成功！");
                                    }
                                }catch(Exception e){
                                    logger.error(bestSign, "自动签署异常:{}", e.getLocalizedMessage() , e);
                                }
                            }else{
                                logger.error(bestSign, "自动签署异常，签署接口的response为null，请检查http code");
                            }
                        }else{
                            logger.error(bestSign, "自动签署异常，异步通知中未包含contractId信息");
                        }
                    }else{
                        logger.error(bestSign, "自动签署异常：请确认开发者ID是否正确");
                    }
                }else{
                    logger.warn(bestSign, "自动签署异常：未开启自动签署或者开发者ID未填入");
                }
            }
            return this.success();
        }
        /**
         * 啥也不是
         */
        else{
            return this.fail("啥也不是");
        }
    }

    public JSONObject getRequestBody(String contractId) {
        List<String> contractIds = new ArrayList<>();
        JSONObject requestBody = new JSONObject();
        JSONObject signer = new JSONObject();
        contractIds.add(contractId);
        signer.put("account", signAccount);
        signer.put("bizName", signBizName);
        signer.put("enterpriseName", signEnterpriseName);
        requestBody.put("signer", signer);
        requestBody.put("sealName", signSealName);
        requestBody.put("contractIds", contractIds);
        requestBody.put("pushUrl", signPushUrl);
        return requestBody;
    }
    public String sign(JSONObject request){
        final BestSignClient bestSignClient = new BestSignClient(
                signHost,
                signClientId,
                signClientSecret,
                signPrivateKey
        );
        final String api = "/api/contracts/sign";
        final String method = "POST";
        String result = bestSignClient.executeRequest(api, method, request);
        return result;
    }
}
