package com.coatardbul.stock.service.statistic;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.api.CommonResult;
import com.coatardbul.baseCommon.constants.AiStrategyEnum;
import com.coatardbul.baseCommon.constants.BuySellQuartzStrategySignEnum;
import com.coatardbul.baseCommon.constants.StockTemplateEnum;
import com.coatardbul.baseCommon.model.bo.Chip;
import com.coatardbul.baseCommon.model.bo.ChipPosition;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.DateTimeUtil;
import com.coatardbul.baseCommon.util.DongCaiUtil;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.entity.bo.PreQuartzTradeDetail;
import com.coatardbul.baseService.entity.bo.StockTemplatePredict;
import com.coatardbul.baseService.entity.bo.TickInfo;
import com.coatardbul.baseService.entity.dto.StockCronRefreshDTO;
import com.coatardbul.baseService.feign.SailServerFeign;
import com.coatardbul.baseService.feign.TickServerFeign;
import com.coatardbul.baseService.service.AiStrategyService;
import com.coatardbul.baseService.service.ChipService;
import com.coatardbul.baseService.service.CronRefreshService;
import com.coatardbul.baseService.service.DataServiceBridge;
import com.coatardbul.baseService.service.DongFangCommonService;
import com.coatardbul.baseService.service.HttpPoolService;
import com.coatardbul.baseService.service.SnowFlakeService;
import com.coatardbul.baseService.service.StockStrategyCommonService;
import com.coatardbul.baseService.service.StockUpLimitAnalyzeCommonService;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.baseService.utils.RedisKeyUtils;
import com.coatardbul.stock.common.constants.Constant;
import com.coatardbul.stock.mapper.StockTemplatePredictMapper;
import com.coatardbul.stock.mapper.StockWarnLogMapper;
import com.coatardbul.stock.model.dto.DongFangPlateDTO;
import com.coatardbul.stock.model.dto.StockCronStrategyTabDTO;
import com.coatardbul.stock.model.entity.StockWarnLog;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.script.Invocable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * Note:  ???????????????????????????????????????????????????redis?????????redis?????????????????????????????????
 * <p>
 * Date: 2022/10/23
 *
 * @author Su Xiaolei
 */
@Service
@Slf4j
public class StockCronRefreshService {
    @Resource
    DataFactory dataFactory;
    @Resource
    DongFangPlateService dongFangPlateService;

    @Autowired
    ChipService chipService;
    @Resource
    public CronRefreshService cronRefreshService;
    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    DongFangCommonService dongFangCommonService;
    @Autowired
    RiverRemoteService riverRemoteService;
    @Autowired
    HttpPoolService HttpPoolService;
    @Autowired
    AiStrategyService aiStrategyService;
    @Autowired
    StockCronRefreshService stockCronRefreshService;
    @Autowired
    SnowFlakeService snowFlakeService;
    @Autowired
    StockStrategyCommonService stockStrategyCommonService;
    @Autowired
    StockWarnLogMapper stockWarnLogMapper;
    @Resource
    TickServerFeign tickServerFeign;

    @Autowired
    StockTemplatePredictMapper stockTemplatePredictMapper;
    @Autowired
    SailServerFeign sailServerFeign;
    @Autowired
    StockUpLimitAnalyzeCommonService stockUpLimitAnalyzeCommonService;
    @Autowired
    StockStrategyService stockStrategyService;


    /**
     * ??????redis?????????????????????
     *
     * @return
     */
    public List getStockInfo(StockCronRefreshDTO dto) {
        Boolean isNow = false;
        List<Map<String, Object>> result = new ArrayList();
        DataServiceBridge dataServiceBridge = dataFactory.build();
        if (!StringUtils.isNotBlank(dto.getDateStr())) {
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
            if (DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.HH_MM_SS).compareTo("15:00:00") < 0) {
                isNow = true;
            }
        }
        //??????redis????????????????????????key
        Set keys = redisTemplate.keys(RedisKeyUtils.getStockInfoPattern(dto.getDateStr()));
        if (keys.size() > 0) {
            for (Object codeKey : keys) {
                if (codeKey instanceof String) {
                    String stockDetailStr = (String) redisTemplate.opsForValue().get(codeKey.toString());
                    Map stockMap = JsonUtil.readToValue(stockDetailStr, Map.class);
                    //????????????
                    if (!isNow) {
                        //??????????????????????????????tick??????????????????????????????
                        String key = RedisKeyUtils.getHisStockTickInfo(dto.getDateStr(), RedisKeyUtils.getCodeByStockInfoKey(codeKey.toString()));
                        String stockTickArrStr = (String) redisTemplate.opsForValue().get(key);
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(stockTickArrStr)) {
                            List<TickInfo> stockTickArr = JsonUtil.readToValue(stockTickArrStr, new TypeReference<List<TickInfo>>() {
                            });
                            //???????????????
                            if (stockTickArr.size() > 0 && StringUtils.isNotBlank(dto.getTimeStr())) {
                                stockTickArr = stockTickArr.stream().filter(item -> item.getTime().compareTo("09:25:00") >= 0 && item.getTime().compareTo(dto.getTimeStr()) <= 0).collect(Collectors.toList());
                                try {
                                    dataServiceBridge.updateTickInfoToStockInfo(stockTickArr, stockMap);
                                } catch (Exception e) {
                                    log.error(e.getMessage());
                                }
                            }
                        }
                    }

                    result.add(stockMap);
                }
            }
        }
        return result;
    }

    public Object getSimpleStockInfo(StockCronRefreshDTO dto) {
        List<Map<String, Object>> result = new ArrayList();
        if (!StringUtils.isNotBlank(dto.getDateStr())) {
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
        }
        //??????redis????????????????????????key
        Set keys = redisTemplate.keys(RedisKeyUtils.getStockInfoPattern(dto.getDateStr()));
        if (keys.size() > 0) {
            for (Object codeKey : keys) {
                if (codeKey instanceof String) {
                    String stockDetailStr = (String) redisTemplate.opsForValue().get(codeKey.toString());
                    Map stockMap = JsonUtil.readToValue(stockDetailStr, Map.class);
                    result.add(stockMap);
                }
            }
        }
        return result;
    }


    /**
     * ????????????redis???????????????
     *
     * @param
     * @return
     */
    public void refreshStockInfo(StockCronRefreshDTO dto) {
//        String dateFormat = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD);
//        List<Map<String, Object>> result = new ArrayList();
//        List<String> codeArr = new ArrayList<>();
//        for (String code : codes) {
//            String key = dateFormat + "_" + code;
//            Boolean hasKey = redisTemplate.hasKey(key);
//            if (hasKey) {
//                String stockDetailStr = (String) redisTemplate.opsForValue().get(key);
//                Map map = JsonUtil.readToValue(stockDetailStr, Map.class);
//                result.add(map);
//            } else {
//                Map<String, Object> map = new HashMap<>();
//                map.put("code", code);
//                result.add(map);
//            }
//        }
        String dateFormat = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD);

        if (dateFormat.equals(dto.getDateStr()) || !StringUtils.isNotBlank(dto.getDateStr())) {
            stockRefreshprocess(dto);
        } else {
            Constant.onceUpLimitThreadPool.submit(() -> {
                tickServerFeign.refreshTickInfo(dto);
            });
            //??????
            stockRefreshHisProcess(dto);

        }

//        return result;
    }

    public void refreshStockMinuterInfo(List<String> codeArr) {
        stockMinuterRefreshprocess(codeArr);
    }

    private void stockMinuterRefreshprocess(List<String> codes) {
        List<String> codeArr = new ArrayList<>();
        for (String code : codes) {
            codeArr.add(code);
            if (codeArr.size() == cronRefreshService.getBatchNum()) {
                List<String> finalCodeArr = codeArr;
                Constant.minuterThreadPool.submit(() -> {
                    StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
                    stockCronRefreshDTO.setCodeArr(finalCodeArr);
                    sailServerFeign.refreshStockMinuterInfo(stockCronRefreshDTO);
                });
                codeArr = new ArrayList<>();
            }
        }
        List<String> finalCodeArr = codeArr;
        if (finalCodeArr.size() > 0) {
            Constant.minuterThreadPool.submit(() -> {
                StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
                stockCronRefreshDTO.setCodeArr(finalCodeArr);
                sailServerFeign.refreshStockMinuterInfo(stockCronRefreshDTO);
            });
        }
    }

    public void stockRefreshHisProcess(StockCronRefreshDTO dto) {
        List<String> codes = dto.getCodeArr();
        List<String> codeArr = new ArrayList<>();
        for (String code : codes) {
            codeArr.add(code);
            if (codeArr.size() == cronRefreshService.getBatchNum()) {
                List<String> finalCodeArr = codeArr;
                Constant.immediateThreadPool.submit(() -> {
                    StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
                    stockCronRefreshDTO.setCodeArr(finalCodeArr);
                    stockCronRefreshDTO.setDateStr(dto.getDateStr());
                    sailServerFeign.refreshHisStockInfo(stockCronRefreshDTO);
                });
                codeArr = new ArrayList<>();
            }
        }
        List<String> finalCodeArr = codeArr;
        if (finalCodeArr.size() > 0) {
            Constant.immediateThreadPool.submit(() -> {
                StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
                stockCronRefreshDTO.setCodeArr(finalCodeArr);
                stockCronRefreshDTO.setDateStr(dto.getDateStr());
                sailServerFeign.refreshHisStockInfo(stockCronRefreshDTO);
            });
        }
    }

    private void stockRefreshprocess(StockCronRefreshDTO dto) {
        List<String> codes = dto.getCodeArr();
        List<String> codeArr = new ArrayList<>();
        for (String code : codes) {
            codeArr.add(code);
            if (codeArr.size() == cronRefreshService.getBatchNum()) {
                List<String> finalCodeArr = codeArr;
                Constant.immediateThreadPool.submit(() -> {
                    StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
                    stockCronRefreshDTO.setCodeArr(finalCodeArr);
                    sailServerFeign.refreshStockInfo(stockCronRefreshDTO);
                });
                codeArr = new ArrayList<>();
            }
        }
        List<String> finalCodeArr = codeArr;
        if (finalCodeArr.size() > 0) {
            Constant.immediateThreadPool.submit(() -> {
                StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
                stockCronRefreshDTO.setCodeArr(finalCodeArr);
                sailServerFeign.refreshStockInfo(stockCronRefreshDTO);
            });
        }
    }


    /**
     * ??????redis???????????????
     *
     * @param codes
     * @param dateStr
     * @return
     */
    public void deleteStockInfo(List<String> codes, String dateStr) {
        if (!StringUtils.isNotBlank(dateStr)) {
            dateStr = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD);
        }
        for (String code : codes) {
            String key = RedisKeyUtils.getHisStockInfo(dateStr, code);
            Boolean hasKey = redisTemplate.hasKey(key);
            if (hasKey) {
                redisTemplate.delete(key);
                redisTemplate.delete(RedisKeyUtils.getHisStockTickInfo(dateStr, code));
            }
        }
    }


    /**
     * ????????????
     */
    public void cronRefresh() {
        String dateFormat = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD);
        //??????redis????????????????????????key
        Set keys = redisTemplate.keys(RedisKeyUtils.getStockInfoPattern(dateFormat));

        if (keys.size() > 0) {
            List<String> codes = new ArrayList<String>();
            for (Object codeKey : keys) {
                if (codeKey instanceof String) {
                    String key = codeKey.toString();
                    String code = RedisKeyUtils.getCodeByStockInfoKey(key);
                    codes.add(code);
                }
            }
            StockCronRefreshDTO stockRefreshprocess = new StockCronRefreshDTO();
            stockRefreshprocess.setDateStr(dateFormat);
            stockRefreshprocess.setCodeArr(codes);
            stockRefreshprocess(stockRefreshprocess);
        }

    }


    public List getTickInfo(StockCronRefreshDTO dto) {
        String key = RedisKeyUtils.getHisStockTickInfo(dto.getDateStr(), dto.getCodeArr().get(0));
        String jsonStr = (String) redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(jsonStr)) {
            return JsonUtil.readToValue(jsonStr, new TypeReference<List<Map>>() {
            });
        }
        return new ArrayList();

    }

    public List getMinuterInfo(StockCronRefreshDTO dto) {

        String key = RedisKeyUtils.getHisStockMinuterInfo(dto.getDateStr(), dto.getCodeArr().get(0));
        String jsonStr = (String) redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(jsonStr)) {
            return JsonUtil.readToValue(jsonStr, new TypeReference<List<Map>>() {
            });
        }
        return new ArrayList();

    }


    public Object getDataThreadPoolConfig() {
        CommonResult threadPoolConfig = sailServerFeign.getThreadPoolConfig();
        return threadPoolConfig.getData();
    }


    public void refreshHisStockTickInfo(StockCronRefreshDTO dto) {
        tickServerFeign.refreshTickInfo(dto);
    }


    public void simulateHis(StockCronStrategyTabDTO dto) {
        Set keys = redisTemplate.keys(RedisKeyUtils.getStockInfoPattern(dto.getDateStr()));

        for (Object codeKey : keys) {
            if (codeKey instanceof String) {
                String key = codeKey.toString();
                String code = RedisKeyUtils.getCodeByStockInfoKey(key);
                try {
                    PreQuartzTradeDetail preQuartzTradeDetail = aiStrategyService.getPreQuartzTradeDetail(code, dto.getStrategySign(), dto.getDateStr());
                    if (preQuartzTradeDetail.getTradeFlag()) {
                        StockWarnLog stockWarnLog = new StockWarnLog();
                        stockWarnLog.setId(String.valueOf(snowFlakeService.getSnowId()));
                        stockWarnLog.setStockCode(preQuartzTradeDetail.getCode());
                        stockWarnLog.setStockName(preQuartzTradeDetail.getName());
                        stockWarnLog.setDate(preQuartzTradeDetail.getDate());
                        Date date = DateTimeUtil.parseDateStr(preQuartzTradeDetail.getDate() + preQuartzTradeDetail.getTime(), DateTimeUtil.YYYY_MM_DD + DateTimeUtil.HH_MM_SS);
                        stockWarnLog.setCreateTime(date);
                        stockWarnLog.setTemplateSign(preQuartzTradeDetail.getQuartzSign());
                        stockWarnLog.setTemplateName(BuySellQuartzStrategySignEnum.getDescByCode(preQuartzTradeDetail.getQuartzSign()));
                        stockWarnLogMapper.insert(stockWarnLog);
                    }
                } catch (Exception e) {
                    log.error("??????????????????" + e.getMessage(), e);
                }
            }
        }
    }

    public void addStockPool(StockCronStrategyTabDTO dto) {
        String stockPoolKey = RedisKeyUtils.getStockPool(dto.getDateStr());
        Set<String> codeArr = new HashSet<>();
        if (redisTemplate.hasKey(stockPoolKey)) {
            String stockPool = (String) redisTemplate.opsForValue().get(stockPoolKey);
            codeArr = JsonUtil.readToValue(stockPool, new TypeReference<Set<String>>() {
            });
        }

        List<String> stockCodeArr = getStockCodeArr(dto.getDateStr(), StockTemplateEnum.INCREASE_GREATE.getSign());
        codeArr.addAll(stockCodeArr);

        redisTemplate.opsForValue().set(stockPoolKey, JsonUtil.toJson(codeArr), 24, TimeUnit.HOURS);

        //??????????????????
        List<String> collect = codeArr.stream().collect(Collectors.toList());
        StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
        stockCronRefreshDTO.setDateStr(dto.getDateStr());
        stockCronRefreshDTO.setCodeArr(collect);
        refreshStockInfo(stockCronRefreshDTO);


    }

    private List<String> getStockCodeArr(String dateStr, String templateSign) {
        List<String> result = new ArrayList<>();
        StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
        stockStrategyQueryDTO.setRiverStockTemplateSign(templateSign);
        stockStrategyQueryDTO.setDateStr(dateStr);
        StrategyBO strategy = null;
        int retryNum = 5;
        while (retryNum > 0) {
            try {
                strategy = stockStrategyCommonService.strategy(stockStrategyQueryDTO);
                break;
            } catch (Exception e) {
                retryNum--;
                log.error(e.getMessage(), e);
            }
        }
        if (strategy != null && strategy.getTotalNum() > 0) {
            for (int k = 0; k < strategy.getData().size(); k++) {
                JSONObject jsonObject = strategy.getData().getJSONObject(k);
                String code = jsonObject.getString("????????????").substring(0, 6);
                result.add(code);
            }
        }
        return result;
    }

    public void addMonthStockPool(StockCronStrategyTabDTO dto) {
        String dateStr = dto.getDateStr();
        String beginDateStr = dateStr.substring(0, dateStr.length() - 2) + "01";
        String endDateStr = dateStr.substring(0, dateStr.length() - 2) + "31";

        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(beginDateStr, endDateStr);
        for (String currDateStr : dateIntervalList) {
            try {
                StockCronStrategyTabDTO stockCronStrategyTabDTO = new StockCronStrategyTabDTO();
                stockCronStrategyTabDTO.setDateStr(currDateStr);
                addStockPool(stockCronStrategyTabDTO);
                Thread.sleep(60 * 1000);
            } catch (Exception e) {
                log.error(currDateStr + "????????????????????????" + e.getMessage());
            }
        }

    }

    public void deleteMonthStockPool(StockCronStrategyTabDTO dto) {
        String dateStr = dto.getDateStr();
        String keyPatten = dateStr.substring(0, dateStr.length() - 2) + "*";
        Set keys = redisTemplate.keys(keyPatten);
        for (Object key : keys) {
            redisTemplate.delete(key);
        }
    }

    public void simulateHisMonthStockPool(StockCronStrategyTabDTO dto) {
        String dateStr = dto.getDateStr();
        String beginDateStr = dateStr.substring(0, dateStr.length() - 2) + "01";
        String endDateStr = dateStr.substring(0, dateStr.length() - 2) + "31";
        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(beginDateStr, endDateStr);
        for (String currDateStr : dateIntervalList) {
            StockCronStrategyTabDTO stockCronStrategyTabDTO = new StockCronStrategyTabDTO();
            stockCronStrategyTabDTO.setDateStr(currDateStr);
            stockCronStrategyTabDTO.setStrategySign(dto.getStrategySign());
            simulateHis(stockCronStrategyTabDTO);
        }
    }

    public void strategyBackTest(StockCronStrategyTabDTO dto) {
        Set keys = redisTemplate.keys(RedisKeyUtils.getStockInfoPattern(dto.getDateStr()));

        for (Object codeKey : keys) {
            if (codeKey instanceof String) {
                String key = codeKey.toString();
                String code = RedisKeyUtils.getCodeByStockInfoKey(key);
                try {
                    PreQuartzTradeDetail preQuartzTradeDetail = aiStrategyService.getPreQuartzTradeDetail(code, dto.getStrategySign(), dto.getDateStr());
                    if (preQuartzTradeDetail.getTradeFlag()) {
                        StockTemplatePredict stockTemplatePredict = new StockTemplatePredict();
                        stockTemplatePredict.setId(snowFlakeService.getSnowId());
                        stockTemplatePredict.setDate(preQuartzTradeDetail.getDate());
                        stockTemplatePredict.setTemplatedSign(dto.getStrategySign());
                        stockTemplatePredict.setTemplatedName(AiStrategyEnum.getDescByCode(dto.getStrategySign()));
                        stockTemplatePredict.setHoldDay(1);
                        stockTemplatePredict.setCode(preQuartzTradeDetail.getCode());
                        stockTemplatePredict.setName(preQuartzTradeDetail.getName());
                        stockTemplatePredict.setBuyPrice(preQuartzTradeDetail.getPrice());
                        stockTemplatePredict.setBuyTime(preQuartzTradeDetail.getTime());
                        stockTemplatePredict.setBuyIncreaseRate(preQuartzTradeDetail.getIncreaseRate());
                        stockTemplatePredict.setCloseIncreaseRate(preQuartzTradeDetail.getCloseIncreaseRate());
                        stockTemplatePredictMapper.insert(stockTemplatePredict);
                        calcSaleInfo(stockTemplatePredict);
                    }
                } catch (Exception e) {
                    log.error("??????????????????" + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * ??????????????????
     *
     * @param stockTemplatePredict
     */

    public void calcSaleInfo(StockTemplatePredict stockTemplatePredict) {
        chipService.calcDongCaiSaleInfo(stockTemplatePredict);
        String specialDay = riverRemoteService.getSpecialDay(stockTemplatePredict.getDate(), stockTemplatePredict.getHoldDay());
        try {
            Map stockDetailMap = dongFangCommonService.getStockDetailMap(stockTemplatePredict.getCode(), specialDay, "11:29");
            BigDecimal newPrice = new BigDecimal(stockDetailMap.get("newPrice").toString());
            stockTemplatePredict.setSalePrice(newPrice);
            stockTemplatePredict.setSaleTime("11:29");
            stockTemplatePredict.setDetail(stockDetailMap.get("thsIndustry").toString() + "\\n" + stockDetailMap.get("theirConcept").toString());

        } catch (Exception e) {
            log.error("????????????11.29???????????????" + e.getMessage());
        }
        stockTemplatePredictMapper.updateByPrimaryKey(stockTemplatePredict);
    }

    private void calcTongHuaSaleInfo(StockTemplatePredict stockTemplatePredict) {

        BigDecimal sum = BigDecimal.ZERO;
        int num = 5;
        for (int i = 1; i <= num; i++) {
            String lastSpecialDay = riverRemoteService.getSpecialDay(stockTemplatePredict.getDate(), 0 - i);
            Map lastStockDetailMap = dongFangCommonService.getStockDetailMap(stockTemplatePredict.getCode(), lastSpecialDay, null);
            BigDecimal concentrationRatio = new BigDecimal(lastStockDetailMap.get("concentrationRatio").toString());
            sum = sum.add(concentrationRatio);
        }
        stockTemplatePredict.setLastConcentrationRatio(sum.divide(new BigDecimal(num), 2, BigDecimal.ROUND_HALF_DOWN).toString());
        Map currStockDetailMap = dongFangCommonService.getStockDetailMap(stockTemplatePredict.getCode(), stockTemplatePredict.getDate(), null);
        stockTemplatePredict.setConcentrationRatio(currStockDetailMap.get("concentrationRatio").toString());
        stockTemplatePredict.setEarnProfit(currStockDetailMap.get("earnProfit").toString());
        stockTemplatePredict.setJettonCost(new BigDecimal(currStockDetailMap.get("jettonCost").toString()));
        stockTemplatePredict.setDetail(currStockDetailMap.get("thsIndustry").toString() + "\\n" + currStockDetailMap.get("theirConcept").toString());
    }


    public void strategyMonthBackTest(StockCronStrategyTabDTO dto) {
        String dateStr = dto.getDateStr();
        String beginDateStr = dateStr.substring(0, dateStr.length() - 2) + "01";
        String endDateStr = dateStr.substring(0, dateStr.length() - 2) + "31";
        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(beginDateStr, endDateStr);
        for (String currDateStr : dateIntervalList) {
            StockCronStrategyTabDTO stockCronStrategyTabDTO = new StockCronStrategyTabDTO();
            stockCronStrategyTabDTO.setDateStr(currDateStr);
            stockCronStrategyTabDTO.setStrategySign(dto.getStrategySign());
            strategyBackTest(stockCronStrategyTabDTO);
        }
    }

    public Chip queryChipDispatcher(StockTemplatePredict stockTemplatePredict) {
        String response = null;
        int retryNum = 10;
        while (retryNum > 0) {
            response = dongFangCommonService.getDayKlineChip(stockTemplatePredict.getCode());
            if (StringUtils.isNotBlank(response)) {
                break;
            } else {
                retryNum--;
            }
        }
        if (!StringUtils.isNotBlank(response)) {
            return null;
        }
        ChipPosition chipPosition = dongFangCommonService.rebuildDayKlineChip(response);
        List<List<String>> dayKlineList = chipPosition.getDayKlineList();
        String toJson = JsonUtil.toJson(dayKlineList);

        Invocable chipInvocable = null;
        try {
            chipInvocable = DongCaiUtil.getChipInvocable();
        } catch (Exception e) {
            log.error("??????????????????js??????" + e.getMessage());
            return null;
        }
        Integer position = chipPosition.getDatePositionMap().get(stockTemplatePredict.getDate());
        Object calcChip = null;
        try {
            calcChip = chipInvocable.invokeFunction("calcChip", position, 150, 120, toJson);

        } catch (Exception e) {
            log.error(e.getMessage());
        }
        Chip convert = DongCaiUtil.convert(calcChip);
        return convert;

    }


    public Object getStockDetail(StockTemplatePredict dto) {

        String hisStockInfoKey = RedisKeyUtils.getHisStockInfo(dto.getDate(), dto.getCode());

        if (redisTemplate.hasKey(hisStockInfoKey)) {
            String stockDetailStr = (String) redisTemplate.opsForValue().get(hisStockInfoKey);
            Map stockMap = JsonUtil.readToValue(stockDetailStr, Map.class);
            return stockMap;
        }
        return null;
    }

    public void dayAddStockJob(String dateStr) {

        List<String> stockCodeArr = getStockCodeArr(dateStr, StockTemplateEnum.INCREASE_GREATE.getSign());

        StockCronRefreshDTO stockCronRefreshDTO = new StockCronRefreshDTO();
        stockCronRefreshDTO.setDateStr(dateStr);
        stockCronRefreshDTO.setCodeArr(stockCodeArr);

        stockRefreshprocess(stockCronRefreshDTO);

        //???????????????7???????????????,?????????
        Object allPlate = dongFangPlateService.getAllPlate();
        DongFangPlateDTO dto = new DongFangPlateDTO();
        dto.setDateStr(dateStr);
        if (allPlate instanceof JSONArray) {
            JSONArray allPlateTemp = (JSONArray) allPlate;
            for (int i = 0; i < allPlateTemp.size(); i++) {
                String gname = allPlateTemp.getJSONObject(i).getString("gname");
                if (gname.contains("????????????")) {
                    String gid = allPlateTemp.getJSONObject(i).getString("gid");
                    dto.setGid(gid);
                }
            }
        }
        List<String> codeList = dongFangPlateService.getCodeUrlList(dto);
        for(String code:codeList){
            stockCodeArr.remove(code);
        }
        if(stockCodeArr.size()>0){
            dto.setCodeArr(stockCodeArr);
            dongFangPlateService.addPlateInfo(dto);
        }
    }
}
