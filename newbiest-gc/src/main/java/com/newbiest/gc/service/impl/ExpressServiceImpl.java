package com.newbiest.gc.service.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.kyexpress.openapi.sdk.KyeClient;
import com.kyexpress.openapi.sdk.client.DefaultKyeClient;
import com.kyexpress.openapi.sdk.client.KyeAccessTokenClient;
import com.kyexpress.openapi.sdk.internal.util.KyeConstants;
import com.kyexpress.openapi.sdk.model.KyeAccessToken;
import com.kyexpress.openapi.sdk.model.KyeAppInfo;
import com.kyexpress.openapi.sdk.request.DefaultRequest;
import com.kyexpress.openapi.sdk.response.AccessTokenKyeResponse;
import com.kyexpress.openapi.sdk.response.DefaultKyeResponse;
import com.newbiest.base.exception.ClientException;
import com.newbiest.base.exception.ClientParameterException;
import com.newbiest.base.exception.ExceptionManager;
import com.newbiest.base.service.BaseService;
import com.newbiest.base.ui.model.NBReferenceList;
import com.newbiest.base.ui.service.UIService;
import com.newbiest.base.utils.CollectionUtils;
import com.newbiest.base.utils.StringUtils;
import com.newbiest.gc.ExpressConfiguration;
import com.newbiest.gc.GcExceptions;
import com.newbiest.gc.express.dto.OrderInfo;
import com.newbiest.gc.express.dto.WaybillDelivery;
import com.newbiest.gc.service.ExpressService;
import com.newbiest.mms.model.MaterialLot;
import com.newbiest.mms.model.MaterialLotHistory;
import com.newbiest.mms.repository.MaterialLotHistoryRepository;
import com.newbiest.mms.repository.MaterialLotRepository;
import com.newbiest.msg.DefaultParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author guoxunbo
 * @date 2020-07-08 13:52
 */
@Service
@Slf4j
@Data
public class ExpressServiceImpl implements ExpressService {

    public static final String ZJ_SHIPPING_ADDRESS = "ZJShippingAddress";
    public static final String SH_SHIPPING_ADDRESS= "SHShippingAddress";

    @Autowired
    ExpressConfiguration expressConfiguration;

    @Autowired
    UIService uiService;

    @Autowired
    MaterialLotRepository materialLotRepository;

    @Autowired
    MaterialLotHistoryRepository materialLotHistoryRepository;

    @Autowired
    BaseService baseService;

    @Value("${spring.profiles.active}")
    private String profiles;

    private boolean isProdEnv() {
        return "prod".equalsIgnoreCase(profiles);
    }

    /**
     * 获取跨域请求的的token
     * @throws ClientException
     */
    private String getToken() throws ClientException {
        try {
            KyeAppInfo appInfo = new KyeAppInfo();
            appInfo.setAppkey(expressConfiguration.getAppKey());
            appInfo.setAppsecret(expressConfiguration.getAppSecret());

            AccessTokenKyeResponse response = KyeAccessTokenClient.accessToken(isProdEnv() ? KyeConstants.TOKEN_SERVER_URL : KyeConstants.SANDBOX_TOKEN_SERVER_URL, appInfo);

            KyeAccessToken accessToken = null;
            if(response != null && response.isSuccess()) {
                accessToken = response.getAccessToken();
            }

            if(accessToken == null || StringUtils.isNullOrEmpty(accessToken.getToken())) {
                throw new ClientException(GcExceptions.GET_EXPRESS_TOKEN_ERROR);
            }

            if (log.isDebugEnabled()) {
                log.debug("Get Token [" + accessToken.getToken() + "]");
            }
            return accessToken.getToken();
        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }

    private List sendRequest(String methodCode, Object parameter) throws ClientException{
        try {
            if (log.isInfoEnabled()) {
                log.info("Start to send [" + methodCode + "] to express.");
            }
            String token = getToken();
            KyeClient kyeClient = new DefaultKyeClient(isProdEnv() ? KyeConstants.SERVER_URL : KyeConstants.SANDBOX_SERVER_URL, expressConfiguration.getAppKey(), expressConfiguration.getAppSecret(), token);
            DefaultRequest request = new DefaultRequest(methodCode, parameter, KyeConstants.REQUEST_DATA_FORMAT_JSON, KyeConstants.RESPONSE_DATA_FORMAT_JSON);
            DefaultKyeResponse response = kyeClient.execute(request);
            if (response == null) {
                throw new ClientException(GcExceptions.EXPRESS_NETWORK_ERROR);
            }
            if (log.isInfoEnabled()) {
                log.info("end to send [" + methodCode + "] to express.");
                log.info("response code is [" + response.getCode() + "]");
                log.info("response msg is [" + response.getMsg() + "]");
                log.info("response data is [" + response.getData() + "]");
            }

            if (!response.isSuccess()) {
                throw new ClientException(response.getMsg());
            }
            return DefaultParser.getObjectMapper().readValue(response.getData(), List.class);
        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }

    /**
     * 绑定快递单号
     * @param materialLots
     * @param expressNumber
     */
    public List<MaterialLot> recordExpressNumber(List<MaterialLot> materialLots, String expressNumber, String planOrderType) throws ClientException{
        try {
            for (MaterialLot materialLot : materialLots) {
                if (!StringUtils.isNullOrEmpty(materialLot.getExpressNumber())) {
                    throw new ClientParameterException(GcExceptions.MATERIAL_LOT_ALREADY_RECORD_EXPRESS, materialLot.getMaterialLotId());
                }
                materialLot.setExpressNumber(expressNumber);
                materialLot.setPlanOrderType(planOrderType);
                materialLot = materialLotRepository.saveAndFlush(materialLot);

                MaterialLotHistory history = (MaterialLotHistory) baseService.buildHistoryBean(materialLot, MaterialLotHistory.TRANS_TYPE_RECORD_EXPRESS);
                materialLotHistoryRepository.save(history);
            }
            return materialLots;
        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }

    /**
     * 给跨域速递下单
     * @param materialLots 物料批次
     * @param serviceMode 服务模式 次日达等等
     * @param payMode 支付方式
     */
    public List<MaterialLot> planOrder(List<MaterialLot> materialLots, int serviceMode, int payMode) throws ClientException {
        try {
            Optional optional = materialLots.stream().filter(materialLot -> StringUtils.isNullOrEmpty(materialLot.getReserved51())).findFirst();
            if (optional.isPresent()) {
                throw new ClientException(GcExceptions.PICKUP_ADDRESS_IS_NULL);
            }
            Set<String> pickUpAddresses = materialLots.stream().map(MaterialLot :: getReserved51).collect(Collectors.toSet());
            if (CollectionUtils.isNotEmpty(pickUpAddresses)  && pickUpAddresses.size() != 1) {
                throw new ClientException(GcExceptions.PICKUP_ADDRESS_MORE_THEN_ONE);
            }

            Map<String, Object> requestParameters = Maps.newHashMap();
            requestParameters.put("customerCode", expressConfiguration.getCustomerCode());
            requestParameters.put("platformFlag", expressConfiguration.getPlatformFlag());

            List<OrderInfo> orderInfos = Lists.newArrayList();
            OrderInfo orderInfo = new OrderInfo();
            // 寄件人信息
            orderInfo.setPreWaybillDelivery(buildPreWaybillDelivery());
            // 收货人信息
            WaybillDelivery preWaybillPickup = new WaybillDelivery();
            preWaybillPickup.setPerson(materialLots.get(0).getReserved52());
            preWaybillPickup.setMobile(materialLots.get(0).getReserved53());
            preWaybillPickup.setAddress(pickUpAddresses.iterator().next());
            orderInfo.setPreWaybillPickup(preWaybillPickup);

            orderInfo.setServiceMode(serviceMode);
            orderInfo.setPayMode(payMode);

            orderInfo.setOrderId(ExpressConfiguration.PLAN_ORDER_DEFAULT_ORDER_ID);
            orderInfo.setPaymentCustomer(expressConfiguration.getCustomerCode());
            orderInfos.add(orderInfo);

            requestParameters.put("orderInfos", orderInfos);

            List responseMap = sendRequest(ExpressConfiguration.PLAN_ORDER_METHOD, requestParameters);
            String waybillNumber = (String) ((Map)responseMap.get(0)).get("waybillNumber");

            if (log.isDebugEnabled()) {
                List<String> materialLotIds = materialLots.stream().map(MaterialLot :: getMaterialLotId).collect(Collectors.toList());
                log.debug(String.format("MaterialLotIds [%s] records express number [%s]", materialLotIds, waybillNumber));
            }
            recordExpressNumber(materialLots, waybillNumber, MaterialLot.PLAN_ORDER_TYPE_AUTO);
            return materialLots;
        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }

    public void cancelOrderByMaterialLots(List<MaterialLot> materialLots) throws ClientException {
        try {
            List<String> expressNumbers = materialLots.stream().filter(materialLot -> !StringUtils.isNullOrEmpty(materialLot.getExpressNumber()))
                    .map(MaterialLot :: getExpressNumber).collect(Collectors.toList());
            for (String expressNumber : expressNumbers) {
                cancelOrder(expressNumber);
            }
        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }

    public void cancelOrder(String expressNumber) throws ClientException {
        try {
            List<MaterialLot> materialLots = materialLotRepository.getByExpressNumber(expressNumber);
            if (CollectionUtils.isNotEmpty(materialLots)) {
                String planOrderType = materialLots.get(0).getPlanOrderType();
                if (MaterialLot.PLAN_ORDER_TYPE_AUTO.equals(planOrderType)) {
                    Map<String, Object> requestParameters = Maps.newHashMap();
                    requestParameters.put("customerCode", expressConfiguration.getCustomerCode());
                    requestParameters.put("waybillNumber", expressNumber);
                    sendRequest(ExpressConfiguration.CANCEL_ORDER_METHOD, requestParameters);
                }
                for (MaterialLot materialLot : materialLots) {
                    materialLot.clearReservedInfo();
                    materialLot = materialLotRepository.saveAndFlush(materialLot);

                    MaterialLotHistory history = (MaterialLotHistory) baseService.buildHistoryBean(materialLot, MaterialLotHistory.TRANS_TYPE_CANCEL_EXPRESS);
                    materialLotHistoryRepository.save(history);
                }
            }

        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }

    /**
     * 创建寄件人信息
     * @return
     * @throws ClientException
     */
    public WaybillDelivery buildPreWaybillDelivery() throws ClientException{
        try {
            // TODO 当前只有ZJ发货，后续如果有别的发货的话，再加入判断逻辑
            String shippingAddress = ZJ_SHIPPING_ADDRESS;
            List<? extends NBReferenceList> nbReferenceLists = uiService.getReferenceList(shippingAddress, NBReferenceList.CATEGORY_SYSTEM);
            if (CollectionUtils.isEmpty(nbReferenceLists)) {
                throw new ClientParameterException(GcExceptions.SHIPPING_ADDRESS_IS_NULL, shippingAddress);
            }
            WaybillDelivery preWaybillDelivery = new WaybillDelivery();
            for (NBReferenceList nbReference : nbReferenceLists) {
                if (nbReference.getKey().equals("companyName")) {
                    preWaybillDelivery.setCompanyName(nbReference.getValue());
                    continue;
                }
                if (nbReference.getKey().equals("person")) {
                    preWaybillDelivery.setPerson(nbReference.getValue());
                    continue;
                }
                if (nbReference.getKey().equals("phone")) {
                    preWaybillDelivery.setPhone(nbReference.getValue());
                    continue;
                }
                if (nbReference.getKey().equals("mobile")) {
                    preWaybillDelivery.setMobile(nbReference.getValue());
                    continue;
                }if (nbReference.getKey().equals("provinceName")) {
                    preWaybillDelivery.setProvinceName(nbReference.getValue());
                    continue;
                }
                if (nbReference.getKey().equals("cityName")) {
                    preWaybillDelivery.setCityName(nbReference.getValue());
                    continue;
                }
                if (nbReference.getKey().equals("countyName")) {
                    preWaybillDelivery.setCountyName(nbReference.getValue());
                    continue;
                }
                if (nbReference.getKey().equals("address")) {
                    preWaybillDelivery.setAddress(nbReference.getValue());
                    continue;
                }
            }
            return preWaybillDelivery;
        } catch (Exception e) {
            throw ExceptionManager.handleException(e, log);
        }
    }


}
