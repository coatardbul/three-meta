package com.coatardbul.stock.service.statistic.dayStatic.scatter;

import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.feign.RiverServerFeign;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.mapper.StockDayEmotionMapper;
import com.coatardbul.stock.mapper.StockMinuterEmotionMapper;
import com.coatardbul.stock.mapper.StockScatterStaticMapper;
import com.coatardbul.stock.mapper.StockStaticTemplateMapper;
import com.coatardbul.stock.model.bo.StockLineInfoBo;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.model.entity.StockScatterStatic;
import com.coatardbul.stock.model.entity.StockStaticTemplate;
import com.coatardbul.stock.service.base.StockStrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/3/1
 *
 * @author Su Xiaolei
 */
@Service
@Slf4j
public class ScatterDayUpLimitMarketValueService extends ScatterDayAbstractService {
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
    StockDayEmotionMapper stockDayEmotionMapper;
    @Autowired
    StockScatterStaticMapper stockScatterStaticMapper;

    @Override
    public void refreshDayProcess(StockEmotionDayDTO dto, StockStaticTemplate stockStaticTemplate) throws IllegalAccessException {
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);

        if (templateIdList != null && templateIdList.size() > 0) {
            StockScatterStatic stockScatterStatic = new StockScatterStatic();
            stockScatterStatic.setId(baseServerFeign.getSnowflakeId());
            stockScatterStatic.setDate(dto.getDateStr());
//            stockScatterStatic.setObjectStaticArray();
            stockScatterStatic.setObjectSign(dto.getObjectEnumSign());


            //???????????????????????????
            String templateId = templateIdList.get(0);
            StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
            stockStrategyQueryDTO.setRiverStockTemplateId(templateId);
            stockStrategyQueryDTO.setDateStr(dto.getDateStr());
            StrategyBO strategy = null;
            try {
                strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            List<StockLineInfoBo> objectArray = new ArrayList<>();
            if (strategy != null && strategy.getTotalNum() > 0) {
                strategy.getData().forEach(item -> {
                    Set<Map.Entry<String, Object>> entries = ((JSONObject) item).entrySet();
                    StockLineInfoBo stockLineInfoBo = new StockLineInfoBo();
                    entries.forEach(stockLineInfo -> {
                        if (stockLineInfo.getKey().equals("code")) {
                            stockLineInfoBo.setCode((String) stockLineInfo.getValue());
                        }
                        if (stockLineInfo.getKey().contains("????????????")) {
                            stockLineInfoBo.setName((String) stockLineInfo.getValue());
                        }
                        if (stockLineInfo.getKey().contains("?????????")) {
                            stockLineInfoBo.setMarketValue(new BigDecimal(String.valueOf(stockLineInfo.getValue())));
                        }
                        if (stockLineInfo.getKey().contains("?????????")) {
                            stockLineInfoBo.setTradeMoney(new BigDecimal(String.valueOf(stockLineInfo.getValue())));
                        }
                        if (stockLineInfo.getKey().contains("?????????")) {
                            stockLineInfoBo.setTurnoverRate(new BigDecimal(String.valueOf(stockLineInfo.getValue())));

                        }
                    });
                    objectArray.add(stockLineInfoBo);
                });

            }
            stockScatterStatic.setObjectStaticArray(JsonUtil.toJson(objectArray));
            stockScatterStaticMapper.deleteByDateAndObjectSign(dto.getDateStr(), dto.getObjectEnumSign());
            stockScatterStaticMapper.insertSelective(stockScatterStatic);
        }
    }

}
