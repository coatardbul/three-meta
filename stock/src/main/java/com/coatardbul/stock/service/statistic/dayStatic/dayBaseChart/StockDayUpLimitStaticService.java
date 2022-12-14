package com.coatardbul.stock.service.statistic.dayStatic.dayBaseChart;

import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseCommon.util.ReflexUtil;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.feign.RiverServerFeign;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.common.util.StockStaticModuleUtil;
import com.coatardbul.stock.mapper.StockDayEmotionMapper;
import com.coatardbul.stock.mapper.StockMinuterEmotionMapper;
import com.coatardbul.stock.mapper.StockStaticTemplateMapper;
import com.coatardbul.stock.model.bo.DayAxiosBaseBO;
import com.coatardbul.stock.model.bo.DayAxiosMiddleBaseBO;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.model.entity.StockDayEmotion;
import com.coatardbul.stock.model.entity.StockStaticTemplate;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.statistic.business.StockVerifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/2/13
 *
 * @author Su Xiaolei
 */
@Slf4j
@Service
public class StockDayUpLimitStaticService extends BaseChartDayAbstractService{
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
    StockVerifyService stockVerifyService;

    /**
     * ?????????????????????????????????
     *
     * @param dto
     * @throws IllegalAccessException
     */
    @Override
    public void refreshDayProcess(StockEmotionDayDTO dto, StockStaticTemplate stockStaticTemplate) throws IllegalAccessException, ParseException {
        //??????????????????????????????id??????,??????????????????id???????????????????????????
        List<String> templateIdList = stockStrategyService.getTemplateIdList(stockStaticTemplate);

        if (templateIdList != null && templateIdList.size() > 0) {
            StockDayEmotion addStockDayEmotion = new StockDayEmotion();
            addStockDayEmotion.setId(baseServerFeign.getSnowflakeId());
            addStockDayEmotion.setDate(dto.getDateStr());
            addStockDayEmotion.setObjectSign(dto.getObjectEnumSign());

            //???????????????????????????
            List<DayAxiosMiddleBaseBO> list = new ArrayList<>();
            for (String templateId : templateIdList) {
                StockStrategyQueryDTO stockStrategyQueryDTO = new StockStrategyQueryDTO();
                stockStrategyQueryDTO.setRiverStockTemplateId(templateId);
                stockStrategyQueryDTO.setDateStr(dto.getDateStr());
                StrategyBO strategy = null;
                try {
                    strategy = stockStrategyService.strategy(stockStrategyQueryDTO);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                DayAxiosMiddleBaseBO axiosBaseBo = new DayAxiosMiddleBaseBO();
                //todo
                axiosBaseBo.setId(templateId);
                axiosBaseBo.setValue(new BigDecimal(strategy.getTotalNum()));
                list.add(axiosBaseBo);
            }
            List<DayAxiosBaseBO> rebuild = rebuild(stockStaticTemplate, list);
            addStockDayEmotion.setObjectStaticArray(JsonUtil.toJson(rebuild));
            stockDayEmotionMapper.deleteByDateAndObjectSign(dto.getDateStr(), dto.getObjectEnumSign());
            stockDayEmotionMapper.insertSelective(addStockDayEmotion);
        }
    }


    private List<DayAxiosBaseBO> rebuild(StockStaticTemplate stockStaticTemplate, List<DayAxiosMiddleBaseBO> list) throws IllegalAccessException {
        Class classBySign = StockStaticModuleUtil.getClassBySign(stockStaticTemplate.getObjectSign());
        Object o = JsonUtil.readToValue(stockStaticTemplate.getObjectStr(), classBySign);
        List<String> specialIdList = new ArrayList<>();
        Map<String, DayAxiosMiddleBaseBO> specialMap = new HashMap<>();
        //todo ???????????????????????????????????????????????????
        String o1 = (String) ReflexUtil.readValueByName("riseId", o);
        specialIdList.add(o1);
        String o2 = (String) ReflexUtil.readValueByName("failId", o);
        specialIdList.add(o2);
        List<DayAxiosBaseBO> result = new ArrayList<>();
        for (DayAxiosMiddleBaseBO dayAxiosMiddleBaseBO : list) {
            if (specialIdList.contains(dayAxiosMiddleBaseBO.getId())) {
                specialMap.put(dayAxiosMiddleBaseBO.getId(), dayAxiosMiddleBaseBO);
                continue;
            }
            DayAxiosBaseBO dayAxiosBaseBO = new DayAxiosBaseBO();
            dayAxiosBaseBO.setName(riverRemoteService.getTemplateNameById(dayAxiosMiddleBaseBO.getId()));
            dayAxiosBaseBO.setValue(dayAxiosMiddleBaseBO.getValue());
            result.add(dayAxiosBaseBO);
        }
        DayAxiosBaseBO dayAxiosBaseBO = new DayAxiosBaseBO();
        BigDecimal subtract = specialMap.get(o1).getValue().subtract(specialMap.get(o2).getValue());
        dayAxiosBaseBO.setName("adjs");
        dayAxiosBaseBO.setValue(subtract);
        result.add(dayAxiosBaseBO);
        return result;
    }




}
