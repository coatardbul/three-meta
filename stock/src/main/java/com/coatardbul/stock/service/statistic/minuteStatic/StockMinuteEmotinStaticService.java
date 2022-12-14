package com.coatardbul.stock.service.statistic.minuteStatic;

import com.coatardbul.baseCommon.constants.StaticLatitudeEnum;
import com.coatardbul.baseCommon.exception.BusinessException;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.feign.RiverServerFeign;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.common.constants.Constant;
import com.coatardbul.stock.mapper.StockMinuterEmotionMapper;
import com.coatardbul.stock.mapper.StockStaticTemplateMapper;
import com.coatardbul.stock.model.bo.AxiosAllDataBo;
import com.coatardbul.stock.model.bo.AxiosBaseBo;
import com.coatardbul.stock.model.bo.AxiosYinfoDataBo;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.model.dto.StockEmotionDayRangeDTO;
import com.coatardbul.stock.model.entity.StockMinuterEmotion;
import com.coatardbul.stock.model.entity.StockStaticTemplate;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.statistic.business.StockVerifyService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/2/8
 *
 * @author Su Xiaolei
 */
@Service
@Slf4j
public class StockMinuteEmotinStaticService {
    @Autowired
    BaseServerFeign baseServerFeign;
    @Autowired
    RiverRemoteService riverRemoteService;
    @Autowired
    StockStrategyService stockStrategyService;
    @Autowired
    RiverServerFeign riverServerFeign;
    @Autowired
    StockStaticTemplateMapper stockStaticTemplateMapper;
    @Autowired
    StockMinuterEmotionMapper stockMinuterEmotionMapper;
    @Autowired
    StockVerifyService stockVerifyService;
    @Autowired
    RedisTemplate redisTemplate;

    /**
     * ????????????????????????????????????
     * 1.?????????????????????????????????????????????????????????,????????????????????????
     * 2.??????????????????????????????timeStr????????????????????????????????????????????????
     *
     * @param dto ??????
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws InterruptedException
     */
    public void refreshDay(StockEmotionDayDTO dto) throws IllegalAccessException, ParseException, InterruptedException {
        //????????????
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        //??????????????????
        StockStaticTemplate stockStaticTemplate = stockVerifyService.verifyObjectSign(dto.getObjectEnumSign());
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);
        //???????????????
        if (StaticLatitudeEnum.day.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            //todo
        }
        //??????
        if (StaticLatitudeEnum.minuter.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            //???????????????????????????????????????HH:mm
            if (StringUtils.isNotBlank(dto.getTimeStr())) {
                //todo
                if (stockVerifyService.isIllegalDateTimeStr(dto.getDateStr(), dto.getTimeStr())) {
                    return;
                }
                timeStrProcess(dto, templateIdList);
                return;
            }
            if (dto.getTimeInterval() != null) {
                timeIntervalProcess(dto, templateIdList);
            }

        }
    }


    public void quickRefreshDay(StockEmotionDayDTO dto) throws ParseException, IllegalAccessException {
        //????????????
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        //??????????????????
        StockStaticTemplate stockStaticTemplate = stockVerifyService.verifyObjectSign(dto.getObjectEnumSign());
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);
        //???????????????
        if (StaticLatitudeEnum.day.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            //todo
        }
        //??????
        if (StaticLatitudeEnum.minuter.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            if (dto.getTimeInterval() != null) {
                timeIntervalQuickProcess(dto, templateIdList);
            }

        }
    }

    private void timeIntervalQuickProcess(StockEmotionDayDTO dto, List<String> templateIdList)  {
        //????????????????????????
        if (templateIdList == null || templateIdList.size() == 0) {
            return;
        }
        //???????????????????????????
        List<String> timeIntervalListData = stockVerifyService.getRemoteTimeInterval(dto.getTimeInterval());

        for (String templateId : templateIdList) {

            List<String> executeTimeList = new ArrayList<>();
            String key = templateId + "_" + dto.getDateStr();
            if (redisTemplate.opsForValue().get(key) != null) {
                String objectStaticArray = (String) redisTemplate.opsForValue().get(key);
                List<AxiosBaseBo> axiosBaseBos = JsonUtil.readToValue(objectStaticArray, new TypeReference<List<AxiosBaseBo>>() {
                });
                List<String> collect = axiosBaseBos.stream().map(AxiosBaseBo::getDateTimeStr).collect(Collectors.toList());
                executeTimeList= timeIntervalListData.stream().filter(o1->!collect.contains(o1)).collect(Collectors.toList());
            } else {
                executeTimeList = timeIntervalListData;
            }
            //??????
            for (String timeStr : executeTimeList) {
                String dateStr=dto.getDateStr();
                Constant.emotionByMinuterThreadPool.execute(() -> {
                    try {
                        timeStrQuickProcess(dateStr, timeStr, templateId);
                    }catch (Exception e){
                        log.error(e.getMessage(),e);
                    }
                });
            }
        }
    }





    private void timeStrQuickProcess(String dateStr, String timeStr, String templateId) {
        //??????????????????
        AxiosBaseBo axiosBaseBo = null;
        try {
            axiosBaseBo = getAxiosBaseBo(dateStr, templateId, timeStr);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (axiosBaseBo == null) {
            return;
        }
        String key = templateId + "_" + dateStr;
        String currentDateTimeStr = axiosBaseBo.getDateTimeStr();
        if (redisTemplate.opsForValue().get(key) != null) {
            String objectStaticArray = (String) redisTemplate.opsForValue().get(key);
            List<AxiosBaseBo> axiosBaseBos = JsonUtil.readToValue(objectStaticArray, new TypeReference<List<AxiosBaseBo>>() {
            });
            List<AxiosBaseBo> collect = axiosBaseBos.stream().filter(o1 -> o1.getDateTimeStr().equals(currentDateTimeStr)).collect(Collectors.toList());
            if (collect == null || collect.size() == 0) {
                axiosBaseBos.add(axiosBaseBo);
                redisTemplate.opsForValue().set(key, JsonUtil.toJson(axiosBaseBos), 1, TimeUnit.HOURS);
            }
        } else {
            List<AxiosBaseBo> list = new ArrayList<>();
            list.add(axiosBaseBo);
            redisTemplate.opsForValue().set(key, JsonUtil.toJson(list), 1, TimeUnit.HOURS);
        }

    }


    /**
     * ????????????HH:mm?????????
     *
     * @param dto
     * @param templateIdList
     */
    private void timeStrProcess(StockEmotionDayDTO dto, List<String> templateIdList) {
        if (templateIdList == null || templateIdList.size() == 0) {
            return;
        }
        for (String templateId : templateIdList) {
            timeStrProcess(dto, templateId);
        }
    }


    private void timeStrProcess(StockEmotionDayDTO dto, String templateId) {
        StockMinuterEmotion stockMinuterEmotion = stockMinuterEmotionMapper.selectAllByDateAndObjectSignAndTemplateId(dto.getDateStr(), dto.getObjectEnumSign(), templateId);
        //??????????????????
        AxiosBaseBo axiosBaseBo = null;
        try {
            axiosBaseBo = getAxiosBaseBo(dto.getDateStr(), templateId, dto.getTimeStr());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        //????????????
        if (stockMinuterEmotion == null) {
            StockMinuterEmotion defaultAddStockMinuterEmotion = getDefaultAddStockMinuterEmotion(dto.getDateStr(), dto.getObjectEnumSign(), templateId);
            List<AxiosBaseBo> list = new ArrayList<>();
            if (axiosBaseBo != null) {
                list.add(axiosBaseBo);
            }
            defaultAddStockMinuterEmotion.setObjectStaticArray(JsonUtil.toJson(list));
            stockMinuterEmotionMapper.insertSelective(defaultAddStockMinuterEmotion);
        } else {
            //??????????????????????????????
            String objectStaticArray = stockMinuterEmotion.getObjectStaticArray();
            List<AxiosBaseBo> axiosBaseBos = JsonUtil.readToValue(objectStaticArray, new TypeReference<List<AxiosBaseBo>>() {
            });
            //???????????????????????????????????????????????????
            if (axiosBaseBos == null || axiosBaseBos.size() == 0) {
                List<AxiosBaseBo> list = new ArrayList<>();
                if (axiosBaseBo != null) {
                    list.add(axiosBaseBo);
                }
                stockMinuterEmotion.setObjectStaticArray(JsonUtil.toJson(list));
            } else {
                boolean flag = false;
                //??????????????????????????????????????????
                for (AxiosBaseBo axiosBase : axiosBaseBos) {
                    if (dto.getTimeStr().equals(axiosBase.getDateTimeStr())) {
                        if (axiosBaseBo != null) {
                            axiosBase.setValue(axiosBaseBo.getValue());
                        }
                    } else {
                        flag = true;
                    }
                }
                if (flag) {
                    if (axiosBaseBo != null) {
                        axiosBaseBos.add(axiosBaseBo);
                    }
                }
                stockMinuterEmotion.setObjectStaticArray(JsonUtil.toJson(axiosBaseBos));
            }
            stockMinuterEmotionMapper.updateByPrimaryKeySelective(stockMinuterEmotion);
        }
    }

    /**
     * ????????????????????????
     *
     * @param dto
     * @param templateIdList
     */
    private void timeIntervalProcess(StockEmotionDayDTO dto, List<String> templateIdList) {
        //????????????????????????
        if (templateIdList == null || templateIdList.size() == 0) {
            return;
        }
        //???????????????????????????
        List<String> timeIntervalListData = stockVerifyService.getRemoteTimeInterval(dto.getTimeInterval());
        for (String timeStr : timeIntervalListData) {
            dto.setTimeStr(timeStr);
            timeStrProcess(dto, templateIdList);
        }
    }

    private StockMinuterEmotion getDefaultAddStockMinuterEmotion(String dateStr, String objectEnumSign, String templatedId) {
        StockMinuterEmotion addStockMinuterEmotion = new StockMinuterEmotion();
        addStockMinuterEmotion.setId(baseServerFeign.getSnowflakeId());
        addStockMinuterEmotion.setDate(dateStr);
        addStockMinuterEmotion.setObjectSign(objectEnumSign);
        addStockMinuterEmotion.setTemplateId(templatedId);
        return addStockMinuterEmotion;
    }

    private AxiosBaseBo getAxiosBaseBo(String dateStr, String templateId, String timeStr) throws NoSuchMethodException, ScriptException, FileNotFoundException {
        StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
        stockStrategyQueryDTO.setRiverStockTemplateId(templateId);
        stockStrategyQueryDTO.setDateStr(dateStr);
        stockStrategyQueryDTO.setTimeStr(timeStr);
        StrategyBO strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
        AxiosBaseBo axiosBaseBo = new AxiosBaseBo();
        axiosBaseBo.setDateTimeStr(timeStr);
        if (strategy.getTotalNum() != null) {
            axiosBaseBo.setValue(new BigDecimal(strategy.getTotalNum()));
        }
        return axiosBaseBo;
    }

    /**
     * @param dto
     */
    public void refreshDayRange(StockEmotionDayRangeDTO dto) {
        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(dto.getBeginDate(), dto.getEndDate());
        for (String dateStr : dateIntervalList) {
            Constant.emotionIntervalByDateThreadPool.execute(() -> {
                StockEmotionDayDTO stockEmotionDayDTO = new StockEmotionDayDTO();
                stockEmotionDayDTO.setDateStr(dateStr);
                stockEmotionDayDTO.setObjectEnumSign(dto.getObjectEnumSign());
                stockEmotionDayDTO.setTimeInterval(dto.getTimeInterval());
                try {
                    refreshDay(stockEmotionDayDTO);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            });

        }
    }

    /**
     * ???????????????????????????
     *
     * @param dto
     */
    public AxiosAllDataBo getDayDetail(StockEmotionDayDTO dto) {
        //????????????????????????
        List<StockStaticTemplate> stockStaticTemplates = stockStaticTemplateMapper.selectAllByObjectSign(dto.getObjectEnumSign());
        if (stockStaticTemplates == null || stockStaticTemplates.size() == 0) {
            throw new BusinessException("??????????????????");
        }

        List<StockMinuterEmotion> stockMinuterEmotions = stockMinuterEmotionMapper.selectAllByDateAndObjectSign(dto.getDateStr(), dto.getObjectEnumSign());
        if (stockMinuterEmotions == null || stockMinuterEmotions.size() == 0) {
            return null;
        }
        //??????????????????
        List<String> timeIntervalListData = stockVerifyService.getRemoteTimeInterval(dto.getTimeInterval());

        return buildAxiosDate(stockMinuterEmotions, timeIntervalListData);
    }

    /**
     * ???????????????????????????
     *
     * @param stockMinuterEmotions ????????????
     * @param timeIntervalListData ????????????
     * @return
     */
    private AxiosAllDataBo buildAxiosDate(List<StockMinuterEmotion> stockMinuterEmotions, List<String> timeIntervalListData) {
        AxiosAllDataBo result = new AxiosAllDataBo();
        //x?????????
        result.setDateTimeStrArray(timeIntervalListData);
        //y?????????
        List<AxiosYinfoDataBo> yAxiosArray = new ArrayList<>();
        for (StockMinuterEmotion stockMinuterEmotion : stockMinuterEmotions) {
            AxiosYinfoDataBo axiosYinfoDataBo = new AxiosYinfoDataBo();
            //y?????????
            if (StringUtils.isNotBlank(stockMinuterEmotion.getTemplateId())) {
                axiosYinfoDataBo.setName(riverRemoteService.getTemplateNameById(stockMinuterEmotion.getTemplateId()));

            } else {
                axiosYinfoDataBo.setName(stockMinuterEmotion.getName());
            }
            //y???????????????
            axiosYinfoDataBo.setYAxiosInfo(JsonUtil.readToValue(stockMinuterEmotion.getObjectStaticArray(), new TypeReference<List<AxiosBaseBo>>() {
            }));
            yAxiosArray.add(axiosYinfoDataBo);
        }
        result.setYbaseInfo(yAxiosArray);
        return result;
    }


    public void filterDate(StockEmotionDayDTO dto) throws IllegalAccessException {
        //??????????????????
        StockStaticTemplate stockStaticTemplate = stockVerifyService.verifyObjectSign(dto.getObjectEnumSign());
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);
        //???????????????
        if (StaticLatitudeEnum.day.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            //todo
        }
        //??????
        if (StaticLatitudeEnum.minuter.getCode().equals(stockStaticTemplate.getStaticLatitude())) {

            for (String templateId : templateIdList) {
                StockMinuterEmotion stockMinuterEmotion = stockMinuterEmotionMapper.selectAllByDateAndObjectSignAndTemplateId(dto.getDateStr(), dto.getObjectEnumSign(), templateId);
                if (stockMinuterEmotion != null) {
                    //??????????????????????????????
                    String objectStaticArray = stockMinuterEmotion.getObjectStaticArray();
                    List<AxiosBaseBo> axiosBaseBos = JsonUtil.readToValue(objectStaticArray, new TypeReference<List<AxiosBaseBo>>() {
                    });
                    Map<String, Integer> map = new HashMap<>();
                    //???????????????????????????
                    List<AxiosBaseBo> newList = new ArrayList<>();
                    for (AxiosBaseBo axiosBaseBo : axiosBaseBos) {
                        if (map.containsKey(axiosBaseBo.getDateTimeStr())) {
                            continue;
                        } else {
                            if (axiosBaseBo.getValue() != null) {
                                newList.add(axiosBaseBo);
                                map.put(axiosBaseBo.getDateTimeStr(), 1);
                            }
                        }
                    }
                    stockMinuterEmotion.setObjectStaticArray(JsonUtil.toJson(newList));
                    stockMinuterEmotionMapper.updateByPrimaryKeySelective(stockMinuterEmotion);
                }
            }
        }
    }

    private Map<String, Integer> convertListToMap(List<String> timeIntervalListData) {
        Map<String, Integer> map = new HashMap<>();
        for (String timeStr : timeIntervalListData) {
            if (!map.containsKey(timeStr)) {
                map.put(timeStr, 1);
            }
        }
        return map;
    }

    public void supplementRefreshDay(StockEmotionDayDTO dto) throws IllegalAccessException {
        //??????????????????
        StockStaticTemplate stockStaticTemplate = stockVerifyService.verifyObjectSign(dto.getObjectEnumSign());
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);
        //???????????????????????????
        List<String> timeIntervalListData = stockVerifyService.getRemoteTimeInterval(dto.getTimeInterval());

        for (String templateId : templateIdList) {
            filterTimeInfo(dto, timeIntervalListData, templateId);
        }
    }


    /**
     * ??????
     *
     * @param dto
     * @param timeIntervalListData ????????????
     * @param templateId           ??????id
     */
    private void filterTimeInfo(StockEmotionDayDTO dto, List<String> timeIntervalListData, String templateId) {
        //??????????????????????????????????????????
        StockMinuterEmotion stockMinuterEmotion = stockMinuterEmotionMapper.selectAllByDateAndObjectSignAndTemplateId(dto.getDateStr(), dto.getObjectEnumSign(), templateId);
        //?????????map
        Map<String, Integer> stringIntegerMap = convertListToMap(timeIntervalListData);
        if (stockMinuterEmotion != null && StringUtils.isNotBlank(stockMinuterEmotion.getObjectStaticArray())) {
            String objectStaticArray = stockMinuterEmotion.getObjectStaticArray();
            List<AxiosBaseBo> axiosBaseBos = JsonUtil.readToValue(objectStaticArray, new TypeReference<List<AxiosBaseBo>>() {
            });
            Map<String, AxiosBaseBo> haveValue = axiosBaseBos.stream().collect(Collectors.toMap(AxiosBaseBo::getDateTimeStr, Function.identity(), (o1, o2) -> o1));
            //????????????????????????
            for (Map.Entry<String, AxiosBaseBo> set : haveValue.entrySet()) {
                if (stringIntegerMap.containsKey(set.getKey())) {
                    stringIntegerMap.remove(set.getKey());
                }
            }
        }
        //????????????time???????????????
        for (Map.Entry<String, Integer> set : stringIntegerMap.entrySet()) {
            dto.setTimeStr(set.getKey());
            timeStrProcess(dto, templateId);
        }
    }


    public void forceRefreshDay(StockEmotionDayDTO dto) throws IllegalAccessException, InterruptedException, ParseException {
        stockMinuterEmotionMapper.deleteByDateAndObjectSign(dto.getDateStr(), dto.getObjectEnumSign());
        refreshDay(dto);
    }

    public void supplementRefreshDayRange(StockEmotionDayRangeDTO dto) {
        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(dto.getBeginDate(), dto.getEndDate());
        for (String dateStr : dateIntervalList) {
            StockEmotionDayDTO stockEmotionDayDTO = new StockEmotionDayDTO();
            stockEmotionDayDTO.setDateStr(dateStr);
            stockEmotionDayDTO.setObjectEnumSign(dto.getObjectEnumSign());
            stockEmotionDayDTO.setTimeInterval(dto.getTimeInterval());
            Constant.emotionIntervalByDateThreadPool.execute(() -> {
                try {
                    filterDate(stockEmotionDayDTO);
                    supplementRefreshDay(stockEmotionDayDTO);
                } catch (IllegalAccessException e) {
                    log.error(e.getLocalizedMessage(), e);
                }
            });

        }
    }


    public void quickSaveRedisData(StockEmotionDayDTO dto) throws ParseException, IllegalAccessException {
        //????????????
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        //??????????????????
        StockStaticTemplate stockStaticTemplate = stockVerifyService.verifyObjectSign(dto.getObjectEnumSign());
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);
        //???????????????
        if (StaticLatitudeEnum.day.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            //todo
        }
        //??????
        if (StaticLatitudeEnum.minuter.getCode().equals(stockStaticTemplate.getStaticLatitude())) {
            if (dto.getTimeInterval() != null) {
                //????????????????????????
                if (templateIdList == null || templateIdList.size() == 0) {
                    return;
                }
                for (String templateId : templateIdList) {
                    String key = templateId + "_" + dto.getDateStr();
                    if (redisTemplate.opsForValue().get(key) != null) {
                        String objectStaticArray = (String) redisTemplate.opsForValue().get(key);
                        StockMinuterEmotion defaultAddStockMinuterEmotion = getDefaultAddStockMinuterEmotion(dto.getDateStr(), dto.getObjectEnumSign(), templateId);
                        defaultAddStockMinuterEmotion.setObjectStaticArray(objectStaticArray);
                        stockMinuterEmotionMapper.deleteByDateAndObjectSignAndTemplateId(dto.getDateStr(),dto.getObjectEnumSign(),templateId);
                        stockMinuterEmotionMapper.insertSelective(defaultAddStockMinuterEmotion);
                    }

                }
            }

        }
    }
}
