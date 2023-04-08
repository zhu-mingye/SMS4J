package kim.wind.sms.huawei.service;

import com.dtflys.forest.config.ForestConfiguration;
import kim.wind.sms.api.SmsBlend;
import kim.wind.sms.api.callback.CallBack;
import kim.wind.sms.api.entity.SmsResponse;
import kim.wind.sms.comm.annotation.Restricted;
import kim.wind.sms.comm.constant.Constant;
import kim.wind.sms.comm.delayedTime.DelayedTime;
import kim.wind.sms.comm.factory.BeanFactory;
import kim.wind.sms.huawei.config.HuaweiConfig;
import kim.wind.sms.huawei.entity.HuaweiResponse;
import kim.wind.sms.huawei.utils.HuaweiBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.Executor;

import static kim.wind.sms.comm.utils.SmsUtil.listToString;


@Slf4j
public class HuaweiSmsImpl implements SmsBlend {
    public HuaweiSmsImpl(HuaweiConfig config, Executor pool, DelayedTime delayed) {
        this.config = config;
        this.pool = pool;
        this.delayed = delayed;
    }

    private HuaweiConfig config;

    private Executor pool;

    private DelayedTime delayed;

    private final ForestConfiguration http = BeanFactory.getForestConfiguration();

    @Override
    @Restricted
    public SmsResponse sendMessage(String phone, String message) {
        LinkedHashMap<String,String> mes = new LinkedHashMap<>();
        mes.put(UUID.randomUUID().toString().replaceAll("-",""),message);
        return sendMessage(phone,config.getTemplateId(),mes);
    }

    @Override
    @Restricted
    public SmsResponse sendMessage(String phone, String templateId, LinkedHashMap<String, String> messages) {
        String url = config.getUrl() + Constant.HUAWEI_REQUEST_URL;
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            list.add(entry.getValue());
        }
        String mess = HuaweiBuilder.listToString(list);
        String requestBody = HuaweiBuilder.buildRequestBody(config.getSender(), phone, config.getTemplateId(), mess, config.getStatusCallBack(), config.getSignature());
        Map<String,String> headers = new LinkedHashMap<>();
        headers.put("Authorization",Constant.HUAWEI_AUTH_HEADER_VALUE);
        headers.put("X-WSSE",HuaweiBuilder.buildWsseHeader(config.getAppKey(), config.getAppSecret()));
        headers.put("Content-Type",Constant.FROM_URLENCODED);
        SmsResponse smsResponse = new SmsResponse();
        http.post(url)
                .addHeader(headers)
                .addBody(requestBody)
                .onSuccess(((data,req,res)->{
                    HuaweiResponse jsonBody = res.get(HuaweiResponse.class);
                    smsResponse.setCode(jsonBody.getCode());
                    smsResponse.setMessage(jsonBody.getDescription());
                    smsResponse.setBizId(jsonBody.getResult().get(0).getSmsMsgId());
                    smsResponse.setData(jsonBody.getResult());
                }))
                .onError((ex,req,res)->{
                    HuaweiResponse huaweiResponse = res.get(HuaweiResponse.class);
                    smsResponse.setErrMessage(huaweiResponse.getDescription());
                    smsResponse.setErrorCode(huaweiResponse.getCode());
                    log.debug(huaweiResponse.getDescription());
                })
                .execute();
        return smsResponse;
    }

    @Override
    @Restricted
    public SmsResponse massTexting(List<String> phones, String message) {
        return sendMessage(listToString(phones),message);
    }

    @Override
    @Restricted
    public SmsResponse massTexting(List<String> phones, String templateId, LinkedHashMap<String, String> messages) {
        return sendMessage(listToString(phones), templateId, messages);
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String message, CallBack callBack) {
        pool.execute(() -> {
            SmsResponse smsResponse = sendMessage(phone, message);
            callBack.callBack(smsResponse);
        });
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String message) {
        pool.execute(() -> sendMessage(phone, message));
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String templateId, LinkedHashMap<String, String> messages, CallBack callBack) {
        pool.execute(() -> {
            SmsResponse smsResponse = sendMessage(phone, templateId, messages);
            callBack.callBack(smsResponse);
        });
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String templateId, LinkedHashMap<String, String> messages) {
        pool.execute(() -> {
            sendMessage(phone, templateId, messages);
        });
    }

    @Override
    @Restricted
    public void delayedMessage(String phone, String message, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(phone, message);
            }
        }, delayedTime);
    }

    @Override
    @Restricted
    public void delayedMessage(String phone, String templateId, LinkedHashMap<String, String> messages, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(phone, templateId, messages);
            }
        }, delayedTime);
    }

    @Override
    @Restricted
    public void delayMassTexting(List<String> phones, String message, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                massTexting(phones, message);
            }
        }, delayedTime);
    }

    @Override
    @Restricted
    public void delayMassTexting(List<String> phones, String templateId, LinkedHashMap<String, String> messages, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                massTexting(phones, templateId, messages);
            }
        }, delayedTime);
    }
}
