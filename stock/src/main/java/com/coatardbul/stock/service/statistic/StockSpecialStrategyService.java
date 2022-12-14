package com.coatardbul.stock.service.statistic;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.model.bo.LimitStrongWeakBO;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.service.StockParseAndConvertService;
import com.coatardbul.baseService.service.UpLimitStrongWeakService;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.common.constants.Constant;
import com.coatardbul.stock.model.bo.LimitBaseInfoBO;
import com.coatardbul.stock.model.bo.StockUpLimitInfoBO;
import com.coatardbul.stock.model.bo.StockUpLimitNameBO;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.statistic.uplimitAnalyze.StockUpLimitValPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/4/9
 *
 * @author Su Xiaolei
 */
@Service
@Slf4j
public class StockSpecialStrategyService {

    @Autowired
    StockStrategyService stockStrategyService;
    @Autowired
    StockUpLimitValPriceService stockUpLimitValPriceService;
    @Autowired
    UpLimitStrongWeakService upLimitStrongWeakService;
    @Autowired
    RiverRemoteService riverRemoteService;
    @Autowired
    BaseServerFeign baseServerFeign;

    @Autowired
    StockParseAndConvertService stockParseAndConvertService;

    /**
     * ???????????????????????????????????????8???
     *
     * @param dto
     * @return
     */
    public List<StockUpLimitNameBO> getTwoAboveUpLimitInfo(StockEmotionDayDTO dto) {

        List<StockUpLimitNameBO> result = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(7);
        for (int i = 2; i < 9; i++) {
            final int num = i;
            Constant.countDownThreadPool.execute(() -> {
                //??????????????????
                String upLimitNumScript = getUpLimitNumScript(num);
                StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
                stockStrategyQueryDTO.setDateStr(dto.getDateStr());
                stockStrategyQueryDTO.setStockTemplateScript(upLimitNumScript);
                StrategyBO strategy = null;
                try {
                    //????????????
                    strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
                    JSONArray data = strategy.getData();
                    List<String> nameList = new ArrayList<>();
                    for (int j = 0; j < data.size(); j++) {
                        nameList.add(stockParseAndConvertService.getStockName(data.getJSONObject(j)));
                    }
                    StockUpLimitNameBO stockUpLimitNameBO = new StockUpLimitNameBO();
                    stockUpLimitNameBO.setUpLimitNum(num + "???");
                    stockUpLimitNameBO.setNameList(nameList);
                    if (stockUpLimitNameBO.getNameList().size() > 0) {
                        result.add(stockUpLimitNameBO);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    countDownLatch.countDown();
                }

            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        return result.stream().sorted(Comparator.comparing(StockUpLimitNameBO::getUpLimitNum).reversed()).collect(Collectors.toList());
    }

    public Object getUpLimitSign(StockEmotionDayDTO dto) {
        dto.setDateStr(riverRemoteService.getSpecialDay(dto.getDateStr(),-1));
        List<StockUpLimitNameBO> result = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(7);
        for (int i = 2; i < 9; i++) {
            final int num = i;
            Constant.countDownThreadPool.execute(() -> {
                //??????????????????
                String upLimitNumScript = getUpLimitNumScript(num);
                StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
                stockStrategyQueryDTO.setDateStr(dto.getDateStr());
                stockStrategyQueryDTO.setStockTemplateScript(upLimitNumScript);
                StrategyBO strategy = null;
                try {
                    //????????????
                    strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
                    JSONArray data = strategy.getData();
                    List<String> codeList = new ArrayList<>();
                    for (int j = 0; j < data.size(); j++) {
                        codeList.add(stockParseAndConvertService.getStockCode(data.getJSONObject(j)));
                    }
                    StockUpLimitNameBO stockUpLimitNameBO = new StockUpLimitNameBO();
                    stockUpLimitNameBO.setUpLimitNum((num+1) + "???");
                    stockUpLimitNameBO.setCodeList(codeList);
                    if (stockUpLimitNameBO.getCodeList().size() > 0) {
                        result.add(stockUpLimitNameBO);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    countDownLatch.countDown();
                }

            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        return result.stream().sorted(Comparator.comparing(StockUpLimitNameBO::getUpLimitNum).reversed()).collect(Collectors.toList());
    }


    /**
     * ??????????????????st?????????{{lastDay3}}????????????{{lastDay2}}?????????{{lastDay1}}?????????{{today}}?????????
     * ???????????????????????????
     *
     * @param num
     * @return
     */
    private String getUpLimitNumScript(int num) {
        StringBuffer sb = new StringBuffer();
        sb.append(" ??????????????????st?????????");
        sb.append("{{lastDay" + (num) + "}}????????????");
        for (int i = num - 1; i > 0; i--) {
            sb.append("{{lastDay" + i + "}}?????????");
        }
        sb.append("{{today}}?????????");

        return sb.toString();
    }


    /**
     * ????????????
     *
     * @param dto
     * @return
     * @throws NoSuchMethodException
     * @throws ScriptException
     * @throws FileNotFoundException
     */
    public List<StockUpLimitInfoBO> getUpLimitTheme(StockStrategyQueryDTO dto) throws NoSuchMethodException, ScriptException, FileNotFoundException {
        StrategyBO strategy = stockStrategyService.strategy(dto);
        if (strategy.getTotalNum() > 0) {
            JSONArray data = strategy.getData();
            return rebuildThemeInfo(data);
        }
        return null;
    }

    /**
     * ????????????
     *
     * @param data
     * @return
     */
    public List<StockUpLimitInfoBO> rebuildThemeInfo(JSONArray data) {
        //key ??????????????? value???????????????
        Map<String, List<String>> themeMap = new HashMap<>();

        //key ??????????????? value???????????????
        Map<String, LimitBaseInfoBO> nameMap = new HashMap<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject jo = data.getJSONObject(i);
            //????????????????????????
            Set<String> keys = jo.keySet();
            String stockName = stockParseAndConvertService.getStockName(jo);
            LimitStrongWeakBO upLimitStrongWeak = upLimitStrongWeakService.getLimitStrongWeak(jo, "??????????????????");
            LimitBaseInfoBO upLimitBaseInfoBO = new LimitBaseInfoBO();
            BeanUtils.copyProperties(upLimitStrongWeak, upLimitBaseInfoBO);
            upLimitBaseInfoBO.setName(stockName);
            upLimitBaseInfoBO.setCode(jo.getString("code"));

            nameMap.put(stockName, upLimitBaseInfoBO);

            for (String key : keys) {
                if (key.contains("??????????????????")) {
                    String themeStr = (String) jo.get(key);
                    if (themeStr.contains("+")) {
                        for (String str : themeStr.split("\\+")) {
                            if (themeMap.containsKey(str)) {
                                themeMap.get(str).add(stockName);
                            } else {
                                List<String> name = new ArrayList<>();
                                name.add(stockName);
                                themeMap.put(str, name);
                            }
                        }
                    } else {
                        if (themeMap.containsKey(themeStr)) {
                            themeMap.get(themeStr).add(stockName);
                        } else {
                            List<String> name = new ArrayList<>();
                            name.add(stockName);
                            themeMap.put(themeStr, name);
                        }
                    }
                }
            }
        }
        List<StockUpLimitInfoBO> result = themeMap.entrySet().stream().map(o1 -> convert(o1, nameMap)).collect(Collectors.toList());
        result = result.stream().sorted(Comparator.comparing(StockUpLimitInfoBO::getNum)).collect(Collectors.toList());
        return result;
    }

    private StockUpLimitInfoBO convert(Map.Entry<String, List<String>> map, Map<String, LimitBaseInfoBO> nameMap) {
        StockUpLimitInfoBO stockUpLimitInfoBO = new StockUpLimitInfoBO();
        stockUpLimitInfoBO.setThemeName(map.getKey());
        List<LimitBaseInfoBO> nameList = new ArrayList<>();
        for (String name : map.getValue()) {
            nameList.add(nameMap.get(name));
        }
        stockUpLimitInfoBO.setNameList(nameList);
        stockUpLimitInfoBO.setNum(map.getValue().size());
        return stockUpLimitInfoBO;
    }







    /**
     * ????????????code??????
     * @param dateStr
     * @param riverStockTemplateId
     * @return
     */
    private List<String> getCodeListByStrategy(String dateStr, String riverStockTemplateId) {
        List<String> codeList = new ArrayList<>();
        StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
        stockStrategyQueryDTO.setDateStr(dateStr);
        stockStrategyQueryDTO.setRiverStockTemplateId(riverStockTemplateId);
        StrategyBO strategy = null;
        try {
            strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
            JSONArray data = strategy.getData();
            for (Object jo : data) {
                codeList.add(((String) ((JSONObject) jo).get("code")));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return codeList;
    }


















}
