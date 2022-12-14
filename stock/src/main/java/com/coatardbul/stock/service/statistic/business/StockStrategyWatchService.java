package com.coatardbul.stock.service.statistic.business;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.constants.StockTemplateEnum;
import com.coatardbul.baseCommon.constants.StockTradeBuyTypeEnum;
import com.coatardbul.baseCommon.constants.StockWatchTypeEnum;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.DateTimeUtil;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.service.StockParseAndConvertService;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.mapper.StockStrategyWatchMapper;
import com.coatardbul.stock.mapper.StockTradeBuyConfigMapper;
import com.coatardbul.stock.model.bo.trade.StockTradeBO;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.model.entity.StockStrategyWatch;
import com.coatardbul.stock.model.entity.StockTradeBuyConfig;
import com.coatardbul.stock.service.base.EmailService;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.statistic.StockWarnLogService;
import com.coatardbul.stock.service.statistic.trade.StockTradeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/3/5
 *
 * @author Su Xiaolei
 */
@Slf4j
@Service
public class StockStrategyWatchService {

    @Autowired
    StockStrategyWatchMapper stockStrategyWatchMapper;
    @Autowired
    StockStrategyService stockStrategyService;
    @Autowired
    StockWarnLogService stockWarnLogService;
    @Autowired
    BaseServerFeign baseServerFeign;
    @Autowired
    RiverRemoteService riverRemoteService;
    @Autowired
    StockVerifyService stockVerifyService;
    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    EmailService emailService;
    @Autowired
    StockTradeBuyConfigMapper stockTradeBuyConfigMapper;
    @Autowired
    StockParseAndConvertService stockParseAndConvertService;
    @Autowired
    StockTradeService stockTradeService;

    //????????????????????????
    public void simulateHistoryStrategyWatch(StockEmotionDayDTO dto) throws ParseException {
        strategyWatch(dto, false);
    }

    //??????????????????
    public void strategyNowWatch(StockEmotionDayDTO dto) throws ParseException {
        strategyWatch(dto, true);
    }

    //??????????????????
    public void emailStrategyWatch(StockEmotionDayDTO dto) throws ParseException {
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        if (stockVerifyService.isIllegalDateTimeStr(dto.getDateStr(),dto.getTimeStr())) {
            return;
        }
        //??????????????????
        List<StockStrategyWatch> stockStrategyWatches = stockStrategyWatchMapper.selectAllByType(StockWatchTypeEnum.EMAIL.getType());
        //???????????????????????????
        if (stockStrategyWatches == null || stockStrategyWatches.size() == 0) {
            return;
        }
        //???????????????????????????
        List<StockStrategyWatch> willExecuteStrategy = stockStrategyWatches.stream().filter(o1 -> filter(o1, dto.getTimeStr())).collect(Collectors.toList());
        if (willExecuteStrategy == null || willExecuteStrategy.size() == 0) {
            return;
        }

        for (StockStrategyWatch executeStrategy : willExecuteStrategy) {
            StockStrategyQueryDTO query = new StockStrategyQueryDTO();
            query.setRiverStockTemplateId(executeStrategy.getTemplatedId());
            query.setDateStr(dto.getDateStr());
            query.setTimeStr(dto.getTimeStr());
            try {
                StrategyBO strategy = stockStrategyService.strategy(query);
                //????????????
                if (strategy.getTotalNum() > 0) {
                    JSONArray data = strategy.getData();
                    for (int i = 0; i < data.size(); i++) {
                        JSONObject jsonObject = data.getJSONObject(i);
                        String code = jsonObject.getString("code");
                        String name = jsonObject.getString("????????????");
                        String key = executeStrategy.getTemplatedId() + "_" + code;
                        if (redisTemplate.opsForValue().get(key) == null) {
                            //????????????
                            process(executeStrategy.getTemplatedId(), key, jsonObject, name, code);
                        }
                    }

                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param templateId
     * @param key
     * @param jsonObject
     * @param name
     * @param code
     */
    private void process(String templateId, String key, JSONObject jsonObject, String name, String code) {
        redisTemplate.opsForValue().set(key, JsonUtil.toJson(jsonObject), 20, TimeUnit.HOURS);

        //?????????????????????????????????
        StockTradeBuyConfig stockTradeBuyConfig = stockTradeBuyConfigMapper.selectAllByTemplateId(templateId);
        //????????????
        if (StockTradeBuyTypeEnum.DIRECT.getType().equals(stockTradeBuyConfig.getType())) {

            directBuy(stockTradeBuyConfig, code, name);
        }
        //????????????
        if (StockTradeBuyTypeEnum.EMAIL.getType().equals(stockTradeBuyConfig.getType())) {
        }

        //????????????
        try {
            emailService.sendProcess("sxl14459048@163.com",
                    riverRemoteService.getTemplateNameById(templateId) + "-" + name,
                    emailInfo(jsonObject)
            );
        } catch (Exception e) {

            log.error(e.getMessage(), e);
        }

    }


    /**
     * ????????????
     *
     * @param stockTradeBuyConfig
     * @param code
     */
    private void directBuy(StockTradeBuyConfig stockTradeBuyConfig, String code, String name) {
        //????????????
        if (stockTradeBuyConfig.getSubNum() <= 0) {
            return;
        }
        //????????????,????????????
        StockStrategyQueryDTO dto = new StockStrategyQueryDTO();
        dto.setRiverStockTemplateSign(StockTemplateEnum.STOCK_DETAIL.getSign());
        dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
        dto.setStockCode(code);
        StrategyBO strategy = null;
        try {
            strategy = stockStrategyService.strategy(dto);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (strategy == null || strategy.getTotalNum() == 0) {
            return;
        }
        JSONObject jsonObject = strategy.getData().getJSONObject(0);

        BigDecimal closePrice = null;
        //??????
        String dateFormat = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYYMMDD);
        for (String key : jsonObject.keySet()) {
            if (key.contains("?????????") && !key.contains(dateFormat)) {
                closePrice = stockParseAndConvertService.convert(jsonObject.get(key));
            }
        }
        if (closePrice != null) {
            //?????????
            BigDecimal upLimit = stockParseAndConvertService.getUpLimit(closePrice);
            //????????????????????????
            BigDecimal userMoney = stockTradeBuyConfig.getSubMoney().divide(new BigDecimal(stockTradeBuyConfig.getSubNum()), 2, BigDecimal.ROUND_HALF_UP);
            //???????????????,1 ,2,
            BigDecimal buyNum = userMoney.divide(upLimit.multiply(new BigDecimal(100)), 0, BigDecimal.ROUND_DOWN);
            if (buyNum.compareTo(BigDecimal.ONE) < 0) {
                //???????????????????????????
                return;
            } else {
                //?????????????????????
                StockTradeBO stockTradeBO=new StockTradeBO();
                stockTradeBO.setStockCode(code);
                stockTradeBO.setPrice(upLimit.toString());
                stockTradeBO.setAmount(buyNum.multiply(new BigDecimal(100)).toString());
                stockTradeBO.setZqmc(name);
                String response = stockTradeService.buy(stockTradeBO);
                //??????
                if(StringUtils.isNotBlank(response)){
                    //????????????
                    stockTradeBuyConfig.setSubNum(stockTradeBuyConfig.getSubNum() - 1);
                    //????????????
                    BigDecimal subtractMoney = stockTradeBuyConfig.getSubMoney().subtract(upLimit.multiply(buyNum.multiply(new BigDecimal(100))));
                    stockTradeBuyConfig.setSubMoney(subtractMoney);
                    stockTradeBuyConfigMapper.updateByPrimaryKeySelective(stockTradeBuyConfig);
                }
            }
        }
    }


    private String emailInfo(JSONObject jsonObject) {
        StringBuffer sb = new StringBuffer();
        sb.append("???????????????");
        sb.append(jsonObject.getString("code"));
        sb.append("\n");
        sb.append("???????????????");
        sb.append(jsonObject.getString("????????????"));
        sb.append("\n");
        sb.append(JsonUtil.toJson(jsonObject));
        return sb.toString();

    }

    public void strategyWatch(StockEmotionDayDTO dto, List<StockStrategyWatch> stockStrategyWatches) throws ParseException {
        //???????????????????????????
        List<StockStrategyWatch> willExecuteStrategy = stockStrategyWatches.stream().filter(o1 -> filter(o1, dto.getTimeStr())).collect(Collectors.toList());
        if (willExecuteStrategy == null || willExecuteStrategy.size() == 0) {
            return;
        }
        for (StockStrategyWatch executeStrategy : willExecuteStrategy) {
            StockStrategyQueryDTO query = new StockStrategyQueryDTO();
            query.setRiverStockTemplateId(executeStrategy.getTemplatedId());
            query.setDateStr(dto.getDateStr());
            query.setTimeStr(dto.getTimeStr());
            try {
                StrategyBO strategy = stockStrategyService.strategy(query);
                //?????????????????????
                if (strategy.getTotalNum() > 0) {
                    stockWarnLogService.insertFilterHistory(strategy, query);
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }


    public void strategyWatch(StockEmotionDayDTO dto, boolean isNow) throws ParseException {
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        if (stockVerifyService.isIllegalDateTimeStr(dto.getDateStr(),dto.getTimeStr())) {
            return;
        }
        //todo ?????????????????????????????????????????????
        List<StockStrategyWatch> stockStrategyWatches = stockStrategyWatchMapper.selectAllByType(StockWatchTypeEnum.CRON_WATCH.getType());
        //???????????????????????????
        if (stockStrategyWatches == null || stockStrategyWatches.size() == 0) {
            return;
        }
        //???????????????????????????
        List<StockStrategyWatch> willExecuteStrategy = stockStrategyWatches.stream().filter(o1 -> filter(o1, dto.getTimeStr())).collect(Collectors.toList());
        if (willExecuteStrategy == null || willExecuteStrategy.size() == 0) {
            return;
        }
        for (StockStrategyWatch executeStrategy : willExecuteStrategy) {
            StockStrategyQueryDTO query = new StockStrategyQueryDTO();
            query.setRiverStockTemplateId(executeStrategy.getTemplatedId());
            query.setDateStr(dto.getDateStr());
            query.setTimeStr(dto.getTimeStr());
            try {
                StrategyBO strategy = stockStrategyService.strategy(query);
                //?????????????????????
                if (strategy.getTotalNum() > 0) {
                    if (isNow) {
                        stockWarnLogService.insertFilterNow(strategy, query);
                    } else {
                        stockWarnLogService.insertFilterHistory(strategy, query);
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }


    /**
     * 1.?????????????????????????????????????????????
     * 2.???????????????????????????true
     *
     * @param stockStrategyWatch ??????????????????
     * @param cronTime           ???????????????????????????????????????
     * @return
     */
    private boolean filter(StockStrategyWatch stockStrategyWatch, String cronTime) {
        if (StringUtils.isNotBlank(stockStrategyWatch.getEndTime())) {
            return stockStrategyWatch.getEndTime().compareTo(cronTime) >= 0;
        } else if(StringUtils.isNotBlank(stockStrategyWatch.getBeginTime())){
            return stockStrategyWatch.getBeginTime().compareTo(cronTime) <= 0;
        }else {
            return true;
        }
    }

    public void add(StockStrategyWatch dto) {
        dto.setId(baseServerFeign.getSnowflakeId());
        stockStrategyWatchMapper.insert(dto);
    }

    public void modify(StockStrategyWatch dto) {
        stockStrategyWatchMapper.updateByPrimaryKeySelective(dto);
    }

    public void delete(StockStrategyWatch dto) {
        stockStrategyWatchMapper.deleteByPrimaryKey(dto.getId());

    }

    public List<StockStrategyWatch> findAll() {
        List<StockStrategyWatch> stockStrategyWatches = stockStrategyWatchMapper.selectByAll(null);
        if (stockStrategyWatches == null || stockStrategyWatches.size() == 0) {
            return stockStrategyWatches;
        }
        return stockStrategyWatches.stream().map(this::setTemplatedName).collect(Collectors.toList());
    }

    private StockStrategyWatch setTemplatedName(StockStrategyWatch dto) {
        dto.setTemplatedName(riverRemoteService.getTemplateNameById(dto.getTemplatedId()));
        return dto;
    }

    public void hisSimulate(StockEmotionDayDTO dto) throws ParseException {
        //????????????
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        //todo ?????????????????????????????????????????????
        List<StockStrategyWatch> stockStrategyWatches = stockStrategyWatchMapper.selectAllByType(StockWatchTypeEnum.CRON_WATCH.getType());
        //???????????????????????????
        if (stockStrategyWatches == null || stockStrategyWatches.size() == 0) {
            return;
        }

        //???????????????????????????
        List<String> timeIntervalListData = stockVerifyService.getRemoteTimeInterval(dto.getTimeInterval());


        for (String timeIntervalStr : timeIntervalListData) {
            dto.setTimeStr(timeIntervalStr);
            strategyWatch(dto, stockStrategyWatches);
        }


    }
}
