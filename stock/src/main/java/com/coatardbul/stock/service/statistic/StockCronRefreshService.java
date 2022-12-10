package com.coatardbul.stock.service.statistic;

import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.api.CommonResult;
import com.coatardbul.baseCommon.constants.AiStrategyEnum;
import com.coatardbul.baseCommon.constants.BuySellQuartzStrategySignEnum;
import com.coatardbul.baseCommon.constants.StockTemplateEnum;
import com.coatardbul.baseCommon.model.bo.CronRefreshConfigBo;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.DateTimeUtil;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.entity.bo.PreQuartzTradeDetail;
import com.coatardbul.baseService.entity.bo.TickInfo;
import com.coatardbul.baseService.service.AiStrategyService;
import com.coatardbul.baseService.service.CronRefreshService;
import com.coatardbul.baseService.service.DataServiceBridge;
import com.coatardbul.baseService.service.HttpPoolService;
import com.coatardbul.baseService.service.SnowFlakeService;
import com.coatardbul.baseService.service.StockStrategyCommonService;
import com.coatardbul.baseService.service.StockUpLimitAnalyzeCommonService;
import com.coatardbul.baseService.utils.RedisKeyUtils;
import com.coatardbul.stock.common.constants.Constant;
import com.coatardbul.stock.feign.SailServerFeign;
import com.coatardbul.stock.feign.TickServerFeign;
import com.coatardbul.stock.mapper.StockWarnLogMapper;
import com.coatardbul.stock.model.dto.StockCronRefreshDTO;
import com.coatardbul.stock.model.dto.StockCronStrategyTabDTO;
import com.coatardbul.stock.model.entity.StockWarnLog;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.romote.RiverRemoteService;
import com.fasterxml.jackson.core.type.TypeReference;
import gnu.trove.set.hash.THashSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
 * Note:  采用新浪财经数据，将股票信息同步到redis上，以redis为数据库，进行信息同步
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
    public CronRefreshService cronRefreshService;
    @Autowired
    RedisTemplate redisTemplate;
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
    SailServerFeign sailServerFeign;
    @Autowired
    StockUpLimitAnalyzeCommonService stockUpLimitAnalyzeCommonService;
    @Autowired
    StockStrategyService stockStrategyService;


    /**
     * 查询redis上所有股票信息
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
        //获取redis上所有当前时间的key
        Set keys = redisTemplate.keys(RedisKeyUtils.getStockInfoPattern(dto.getDateStr()));
        if (keys.size() > 0) {
            for (Object codeKey : keys) {
                if (codeKey instanceof String) {
                    String stockDetailStr = (String) redisTemplate.opsForValue().get(codeKey.toString());
                    Map stockMap = JsonUtil.readToValue(stockDetailStr, Map.class);
                    //是否当前
                    if (!isNow) {
                        //如果有时间，需要根据tick数据动态计算缺省数值
                        String key = RedisKeyUtils.getHisStockTickInfo(dto.getDateStr(), RedisKeyUtils.getCodeByStockInfoKey(codeKey.toString()));
                        String stockTickArrStr = (String) redisTemplate.opsForValue().get(key);
                        if (org.apache.commons.lang3.StringUtils.isNotBlank(stockTickArrStr)) {
                            List<TickInfo> stockTickArr = JsonUtil.readToValue(stockTickArrStr, new TypeReference<List<TickInfo>>() {
                            });
                            //有过滤时间
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
        //获取redis上所有当前时间的key
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

    public void addStrategyTab(StockCronStrategyTabDTO dto) {
        if (!StringUtils.isNotBlank(dto.getDateStr())) {
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
        }
        for (String code : dto.getCodeArr()) {
            if (code instanceof String) {
                String codeKey = RedisKeyUtils.getHisStockInfo(dto.getDateStr(), code);
                String stockDetailStr = (String) redisTemplate.opsForValue().get(codeKey.toString());
                Map stockMap = JsonUtil.readToValue(stockDetailStr, Map.class);
                stockMap.put("aiStrategySign", dto.getStrategySign());
                redisTemplate.opsForValue().set(codeKey, JsonUtil.toJson(stockMap), cronRefreshService.getCodeExistHour(), TimeUnit.HOURS);
            }

        }

    }

    /**
     * 获取刷新redis上股票信息
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
            //历史
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
     * 删除redis上股票信息
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
     * 定时刷新
     */
    public void cronRefresh() {
        String dateFormat = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD);
        //获取redis上所有当前时间的key
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
                    log.error("模拟历史异常" + e.getMessage(), e);
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
        //6天的数据，当天是否为交易日
        for (int i = 0; i <=6; i++) {
            String specialDay = null;
            if (i == 0) {
                specialDay = dto.getDateStr();
            } else {
                specialDay = riverRemoteService.getSpecialDay(dto.getDateStr(), 0 - i);
            }
            List<String> stockCodeArr = getStockCodeArr(specialDay, StockTemplateEnum.XXX.getSign());
            codeArr.addAll(stockCodeArr);

        }
        String specialDay = riverRemoteService.getSpecialDay(dto.getDateStr(), -1);
        List<String> stockCodeArr = getStockCodeArr(specialDay, StockTemplateEnum.TWO_UP_LIMIT_ABOVE.getSign());
        for (String code : stockCodeArr) {
            codeArr.remove(code);
        }
        redisTemplate.opsForValue().set(stockPoolKey, JsonUtil.toJson(codeArr), 24, TimeUnit.HOURS);

        //调用历史接口
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
                String code = jsonObject.getString("股票代码").substring(0, 6);
                result.add(code);
            }
        }
        return result;
    }

    public void addMonthStockPool(StockCronStrategyTabDTO dto) {
        String dateStr = dto.getDateStr();
        String beginDateStr = dateStr.substring(0,dateStr.length() - 2)+"01";
        String endDateStr = dateStr.substring(0,dateStr.length() - 2)+"31";

        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(beginDateStr, endDateStr);
        for(String currDateStr:dateIntervalList){
            StockCronStrategyTabDTO stockCronStrategyTabDTO=new StockCronStrategyTabDTO();
            stockCronStrategyTabDTO.setDateStr(currDateStr);
            stockCronStrategyTabDTO.setStrategySign(AiStrategyEnum.DU_GU_SWORD.getCode());
            addStockPool(stockCronStrategyTabDTO);
            simulateHis(stockCronStrategyTabDTO);
        }

    }
}
