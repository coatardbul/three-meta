package com.coatardbul.stock.service.statistic.uplimitAnalyze;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.exception.BusinessException;
import com.coatardbul.baseCommon.model.bo.LimitStrongWeakBO;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.DateTimeUtil;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.feign.RiverServerFeign;
import com.coatardbul.baseService.service.StockParseAndConvertService;
import com.coatardbul.baseService.service.UpLimitStrongWeakService;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.common.constants.Constant;
import com.coatardbul.stock.mapper.StockUpLimitValPriceMapper;
import com.coatardbul.stock.model.bo.UpLimitValPriceBO;
import com.coatardbul.stock.model.dto.StockUpLimitNumDTO;
import com.coatardbul.stock.model.dto.StockValPriceDTO;
import com.coatardbul.stock.model.entity.StockUpLimitValPrice;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.statistic.business.StockVerifyService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/4/4
 *
 * @author Su Xiaolei
 */
@Slf4j
@Service
public class StockUpLimitValPriceService {
    @Autowired
    BaseServerFeign baseServerFeign;
    @Autowired
    RiverRemoteService riverRemoteService;
    @Autowired
    StockStrategyService stockStrategyService;
    @Autowired
    RiverServerFeign riverServerFeign;
    @Autowired
    StockVerifyService stockVerifyService;
    @Autowired
    StockUpLimitValPriceMapper stockUpLimitValPriceMapper;

    @Autowired
    UpLimitStrongWeakService upLimitStrongWeakService;
    @Autowired
    StockParseAndConvertService stockParseAndConvertService;

    /**
     * ????????????
     *
     * @param dto
     */
    public void volPriceProcess(StockValPriceDTO dto) {
        //?????????????????????????????????
        String queryScript = getQueryScript();
        //????????????
        StockStrategyQueryDTO stockStrategyQueryDTO = buildStrategyInfo(queryScript, dto);
        try {
            StrategyBO strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
            //??????????????????
            if (strategy != null) {
                //????????????????????????
                parseStrategyProcess(strategy);
                //????????????
                strongWeakProcess(dto);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * ???????????????????????????
     *
     * @return
     */
    private String getQueryScript() {
        StringBuffer sb = new StringBuffer();
        sb.append("??????????????????{{stockCode}},");
        //???4???
        for (int i = 4; i > 0; i--) {
            String dateScript = "{{lastDay" + i + "}}";
            sb.append(getIndexQueryScript(dateScript));
        }
        //??????
        sb.append(getIndexQueryScript("{{today}}"));
        return sb.toString();
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     * ????????????????????????
     * ?????????????????????????????????
     *
     * @param strategy
     */
    private void parseStrategyProcess(StrategyBO strategy) {
        JSONArray data = strategy.getData();
        JSONObject jo = data.getJSONObject(0);
        //key YYYYMMDD  value???????????????????????????????????????
        Map<String, UpLimitValPriceBO> allDateMap = new HashMap<>();
        //??????http??????
        setUpLimitValPrice(jo, allDateMap);
        //?????????????????????
        String code = stockParseAndConvertService.getStockCode(jo);
        String stockName = stockParseAndConvertService.getStockName(jo);

        //?????????????????????,?????????????????????????????????????????????
        mergeTableUpLimitValPrice(code, allDateMap);

        //??????map???list?????????????????????
        List<UpLimitValPriceBO> allUpLimitValPriceList = allDateMap.values().stream().collect(Collectors.toList());
        allUpLimitValPriceList = allUpLimitValPriceList.stream().sorted(Comparator.comparing(UpLimitValPriceBO::getDateStr)).collect(Collectors.toList());

        //????????????
        List<UpLimitValPriceBO> filterUpLimitValPriceList = filterUpLimitValPrice(allUpLimitValPriceList);
        //????????????????????????
        reAddTableDate(code, stockName, filterUpLimitValPriceList);
    }

    /**
     * ????????????????????????
     *
     * @param code
     * @param stockName
     * @param filterUpLimitValPriceList ????????????????????????
     */
    private void reAddTableDate(String code, String stockName, List<UpLimitValPriceBO> filterUpLimitValPriceList) {
        StockUpLimitValPrice addInfo = new StockUpLimitValPrice();
        addInfo.setId(baseServerFeign.getSnowflakeId());
        addInfo.setCode(code);
        addInfo.setName(stockName);
        addInfo.setBeginDate(filterUpLimitValPriceList.get(0).getDateStr());
        addInfo.setEndDate(filterUpLimitValPriceList.get(filterUpLimitValPriceList.size() - 1).getDateStr());
        addInfo.setObjectArray(JsonUtil.toJson(filterUpLimitValPriceList));
        stockUpLimitValPriceMapper.deleteByCode(code);
        stockUpLimitValPriceMapper.insertSelective(addInfo);
    }


    /**
     * ????????????????????????????????????
     * 1.??????????????????????????????????????????????????????
     * 2.????????????????????????????????????????????????????????????????????????
     *
     * @param allUpLimitValPriceList
     * @return
     */
    private List<UpLimitValPriceBO> filterUpLimitValPrice(List<UpLimitValPriceBO> allUpLimitValPriceList) {
        int beginIndex = 0;
        int endIndex = 0;
        //??????????????????
        for (int i = 0; i < allUpLimitValPriceList.size(); i++) {
            if (new BigDecimal(9.5).compareTo(allUpLimitValPriceList.get(i).getIncreaseRate()) > 0) {
                if (i + 1 < allUpLimitValPriceList.size()) {
                    if (new BigDecimal(9.5).compareTo(allUpLimitValPriceList.get(i + 1).getIncreaseRate()) > 0) {
                        continue;
                    } else if (i - 1 >= 0 && new BigDecimal(9.5).compareTo(allUpLimitValPriceList.get(i - 1).getIncreaseRate()) > 0) {
                        beginIndex = i + 1;
                    }

                } else {
                    endIndex = allUpLimitValPriceList.size();
                }
            } else {
                if (i + 2 < allUpLimitValPriceList.size()) {
                    if (new BigDecimal(9.5).compareTo(allUpLimitValPriceList.get(i + 1).getIncreaseRate()) > 0
                            && new BigDecimal(9.5).compareTo(allUpLimitValPriceList.get(i + 2).getIncreaseRate()) > 0
                    ) {
                        endIndex = i + 1;
                    }
                    continue;

                } else {
                    endIndex = allUpLimitValPriceList.size();
                }
            }
        }
        List<UpLimitValPriceBO> filterUpLimitValPriceList = allUpLimitValPriceList.subList(beginIndex, endIndex);
        return filterUpLimitValPriceList;
    }


    /**
     * ???????????????????????????http?????????
     *
     * @param code
     * @param dateMap http???????????????????????????
     */
    private void mergeTableUpLimitValPrice(String code, Map<String, UpLimitValPriceBO> dateMap) {
        StockUpLimitValPrice stockUpLimitValPriceTemp = stockUpLimitValPriceMapper.selectAllByCode(code);
        if (stockUpLimitValPriceTemp != null) {
            Map<String, UpLimitValPriceBO> tableMap = new HashMap<>();
            List<UpLimitValPriceBO> upLimitValPriceTableList = JsonUtil.readToValue(stockUpLimitValPriceTemp.getObjectArray(), new TypeReference<List<UpLimitValPriceBO>>() {
            });
            if (upLimitValPriceTableList != null && upLimitValPriceTableList.size() > 0) {
                tableMap = upLimitValPriceTableList.stream().collect(Collectors.toMap(UpLimitValPriceBO::getDateStr, Function.identity(), (o1, o2) -> o1));
            }
            //??????????????????http??????????????????????????????????????????http???????????????
            for (Map.Entry<String, UpLimitValPriceBO> upLimitValPriceMap : dateMap.entrySet()) {
                if (tableMap.containsKey(upLimitValPriceMap.getKey())) {
                    tableMap.remove(upLimitValPriceMap.getKey());
                }
            }
            //????????????????????????????????????????????????
            if (tableMap.size() > 0) {
                dateMap.putAll(tableMap);
            }
        }
    }

    /**
     * ???http?????????????????????????????????map??????
     *
     * @param jo
     * @param dateMap key????????????value???????????????????????????????????????
     */
    private void setUpLimitValPrice(JSONObject jo, Map<String, UpLimitValPriceBO> dateMap) {
        Set<String> keys = jo.keySet();
        for (String key : keys) {
            if (key.contains("????????????")) {
                String dateInfo = key.substring(5, key.length() - 1);
                dateMap.put(dateInfo, new UpLimitValPriceBO());
            }
        }
        for (String key : keys) {
            if (key.contains("????????????")) {
                for (Map.Entry<String, UpLimitValPriceBO> map : dateMap.entrySet()) {
                    if (key.contains(map.getKey())) {
                        map.getValue().setCallAuctionTradeAmount(convert(jo.get(key)));
                    }
                }
            }
            if (key.contains("????????????")) {
                for (Map.Entry<String, UpLimitValPriceBO> map : dateMap.entrySet()) {
                    if (key.contains(map.getKey())) {
                        map.getValue().setCallAuctionIncreaseRate(convert(jo.get(key)));
                    }
                }
            }
            if (key.contains("???????????????")) {
                for (Map.Entry<String, UpLimitValPriceBO> map : dateMap.entrySet()) {
                    if (key.contains(map.getKey())) {
                        map.getValue().setCallAuctionTurnOverRate(convert(jo.get(key)));
                    }
                }
            }
            if (key.contains("?????????")) {
                for (Map.Entry<String, UpLimitValPriceBO> map : dateMap.entrySet()) {
                    if (key.contains(map.getKey())) {
                        map.getValue().setTradeAmount(convert(jo.get(key)));
                    }
                }
            }
            if (key.contains("?????????:?????????")) {
                for (Map.Entry<String, UpLimitValPriceBO> map : dateMap.entrySet()) {
                    if (key.contains(map.getKey())) {
                        map.getValue().setIncreaseRate(convert(jo.get(key)));
                    }
                }
            }
            if (key.contains("?????????") && key.length() == 13) {
                for (Map.Entry<String, UpLimitValPriceBO> map : dateMap.entrySet()) {
                    if (key.contains(map.getKey())) {
                        map.getValue().setTurnOverRate(convert(jo.get(key)));
                        map.getValue().setDateStr(map.getKey());
                    }
                }
            }
        }
    }

    /**
     * ????????????
     *
     * @param value
     * @return
     */
    private BigDecimal convert(Object value) {
        if (value instanceof Integer) {
            return new BigDecimal((Integer) value);
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof String) {
            return new BigDecimal((String) value);
        }
        return (BigDecimal) value;
    }


    /**
     * ?????????????????????????????????
     *
     * @param dateScript
     * @return
     */
    private String getIndexQueryScript(String dateScript) {
        StringBuffer sb = new StringBuffer();
        sb.append(dateScript).append("????????????");
        sb.append(dateScript).append("?????????");
        sb.append(dateScript).append("????????????");
        sb.append(dateScript).append("???????????????");
        sb.append(dateScript).append("???????????????");
        sb.append(dateScript).append("??????????????????");
        return sb.toString();
    }

    /**
     * ??????????????????????????????river??????????????????????????????
     *
     * @param queryScript ??????
     * @return
     */
    private StockStrategyQueryDTO buildStrategyInfo(String queryScript, StockValPriceDTO dto) {
        StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
        stockStrategyQueryDTO.setStockTemplateScript(queryScript);
        stockStrategyQueryDTO.setDateStr(dto.getDateStr());
        stockStrategyQueryDTO.setStockCode(dto.getCode());
        return stockStrategyQueryDTO;
    }

    public void delete(StockValPriceDTO dto) {
        stockUpLimitValPriceMapper.deleteByCode(dto.getCode());

    }

    public List<StockUpLimitValPrice> getAll(StockValPriceDTO dto) {
        if (StringUtils.isNotBlank(dto.getDateStr())) {
            return stockUpLimitValPriceMapper.selectAllByCodeAndBeginDateLessThanEqualAndEndDateGreaterThanEqual(dto.getCode(), dto.getDateStr().replace("-", ""));
        } else {
            return stockUpLimitValPriceMapper.selectAllByCodeAndBeginDateLessThanEqualAndEndDateGreaterThanEqual(dto.getCode(), null);

        }
    }

    /**
     * ??????????????????
     *
     * @param dto
     */
    public void strongWeakProcess(StockValPriceDTO dto) throws ParseException {
        StockUpLimitValPrice stockUpLimitValPrice = stockUpLimitValPriceMapper.selectAllByCode(dto.getCode());
        if (!StringUtils.isNotBlank(stockUpLimitValPrice.getBeginDate())) {
            throw new BusinessException("?????????????????????????????????");
        }
        if (!StringUtils.isNotBlank(stockUpLimitValPrice.getEndDate())) {
            throw new BusinessException("?????????????????????????????????");
        }
        String beginDateStr = DateTimeUtil.getDateFormat(DateTimeUtil.parseDateStr(stockUpLimitValPrice.getBeginDate(), DateTimeUtil.YYYYMMDD), DateTimeUtil.YYYY_MM_DD);
        String endDateStr = DateTimeUtil.getDateFormat(DateTimeUtil.parseDateStr(stockUpLimitValPrice.getEndDate(), DateTimeUtil.YYYYMMDD), DateTimeUtil.YYYY_MM_DD);
        List<String> dateIntervalList = riverRemoteService.getDateIntervalList(beginDateStr, endDateStr);
        //?????????????????????
        CountDownLatch countDownLatch = new CountDownLatch(dateIntervalList.size());
        List<LimitStrongWeakBO> strongWeakList = new ArrayList<>();
        for (String dateStr : dateIntervalList) {
            Constant.emotionIntervalByDateThreadPool.execute(() -> {
                StockStrategyQueryDTO strategyQueryDTO = new StockStrategyQueryDTO();
                strategyQueryDTO.setRiverStockTemplateId("1509349533765730304");
                strategyQueryDTO.setDateStr(dateStr);
                strategyQueryDTO.setStockCode(stockUpLimitValPrice.getCode());
                try {
                    StrategyBO strategy = stockStrategyService.strategy(strategyQueryDTO);
                    if (strategy.getTotalNum() > 0) {
                        LimitStrongWeakBO rebuild = rebuild(strategy);
                        strongWeakList.add(rebuild);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    //??????????????????
                    countDownLatch.countDown();
                }
            });
        }
        try {
            //?????????????????????
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
//        strongWeakList = strongWeakList.stream().sorted(Comparator.comparing(UpLimitStrongWeakBO::getDateStr)).collect(Collectors.toList());
        stockUpLimitValPrice.setStrongWeakArray(JsonUtil.toJson(strongWeakList));
        stockUpLimitValPriceMapper.updateByPrimaryKeySelective(stockUpLimitValPrice);
    }

    private LimitStrongWeakBO rebuild(StrategyBO strategy) {
        //????????????????????????
        JSONObject jo = (JSONObject) strategy.getData().get(0);
        return upLimitStrongWeakService.getLimitStrongWeak(jo, "??????????????????");

    }

    public String getDescribe(StockUpLimitNumDTO dto) {
        StockUpLimitValPrice stockUpLimitValPrice = stockUpLimitValPriceMapper.selectAllByName(dto.getName());
        if (stockUpLimitValPrice == null) {
            return null;
        }
        String strongWeakArray = stockUpLimitValPrice.getStrongWeakArray();

        if (StringUtils.isNotBlank(strongWeakArray)) {
            List<LimitStrongWeakBO> limitStrongWeakBOList = JsonUtil.readToValue(strongWeakArray, new TypeReference<List<LimitStrongWeakBO>>() {
            });
            limitStrongWeakBOList = limitStrongWeakBOList.stream().sorted(Comparator.comparing(LimitStrongWeakBO::getDateStr)).collect(Collectors.toList());
            StringBuffer sb = new StringBuffer();
            for (LimitStrongWeakBO up : limitStrongWeakBOList) {
                if (StringUtils.isNotBlank(dto.getDateStr()) && up.getDateStr().compareTo(dto.getDateStr()) >= 0) {
                    continue;
                }
                sb.append(upLimitStrongWeakService.getLimitStrongWeakDescribe(up));
                sb.append("-------------------------------------\n");
            }
            return sb.toString();
        }
        return null;
    }


    /**
     * ??????id???????????????code?????????????????????????????????
     *
     * @param dto
     */
    public void dayTwoAboveUpLimitVolPriceJobHandler(StockStrategyQueryDTO dto) throws ParseException {
        if (stockVerifyService.isIllegalDate(dto.getDateStr())) {
            return;
        }
        List<String> codeList = new ArrayList<>();
        //????????????
        try {
            StrategyBO strategy = stockStrategyService.strategy(dto);
            //???????????????code
            codeList = getStrategyCodeInfo(strategy);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        //????????????
        for (String code : codeList) {
            StockValPriceDTO stockValPriceDTO = new StockValPriceDTO();
            stockValPriceDTO.setCode(code);
            stockValPriceDTO.setDateStr(dto.getDateStr());
            volPriceProcess(stockValPriceDTO);
        }

    }

    private List<String> getStrategyCodeInfo(StrategyBO strategy) {
        List<String> codeList = new ArrayList<>();

        JSONArray data = strategy.getData();
        for (Object jo : data) {
            //?????????????????????
            String code = ((JSONObject) jo).getString("code");
            codeList.add(code);
        }
        return codeList;
    }

}
