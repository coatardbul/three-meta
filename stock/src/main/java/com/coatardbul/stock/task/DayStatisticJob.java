package com.coatardbul.stock.task;

import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.DateTimeUtil;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.service.base.StockStrategyService;
import com.coatardbul.stock.service.statistic.StockCronRefreshService;
import com.coatardbul.stock.service.statistic.StockSpecialStrategyService;
import com.coatardbul.stock.service.statistic.dayStatic.StockDayStaticService;
import com.coatardbul.stock.service.statistic.dayStatic.dayBaseChart.StockDayTrumpetCalcService;
import com.coatardbul.stock.service.statistic.dayStatic.scatter.ScatterDayUpLimitCallAuctionService;
import com.coatardbul.stock.service.statistic.dayStatic.scatter.StockScatterService;
import com.coatardbul.stock.service.statistic.trade.StockTradeUserService;
import com.coatardbul.stock.service.statistic.uplimitAnalyze.StockUpLimitValPriceService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/2/17
 *
 * @author Su Xiaolei
 */
@Slf4j
@Component
public class DayStatisticJob {
    @Autowired
    StockDayTrumpetCalcService stockDayTrumpetCalcService;

    @Autowired
    StockStrategyService stockStrategyService;
    @Autowired
    StockScatterService stockScatterService;
    @Autowired
    StockDayStaticService stockDayStaticService;

    @Autowired
    ScatterDayUpLimitCallAuctionService stockScatterUpLimitService;
    @Autowired
    StockUpLimitValPriceService stockUpLimitValPriceService;
    @Autowired
    StockTradeUserService stockTradeUserService;

    @Autowired
    StockCronRefreshService stockCronRefreshService;

    @Autowired
    StockSpecialStrategyService stockSpecialStrategyService;

    @XxlJob("dayUpDownJobHandler")
    public void dayUpDownJobHandler() throws IllegalAccessException, ParseException {
        String param = XxlJobHelper.getJobParam();
        log.info("????????????????????????????????????" + param);
        if (StringUtils.isNotBlank(param)) {
            StockEmotionDayDTO dto = JsonUtil.readToValue(param, StockEmotionDayDTO.class);
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
            log.info("????????????????????????????????????" + JsonUtil.toJson(dto));
            stockDayStaticService.refreshDay(dto);
        }
        log.info("????????????????????????????????????");
    }


    @XxlJob("dayTwoUpLimitJobHandler")
    public void dayTwoUpLimitJobHandler() throws IllegalAccessException, ParseException {
        String param = XxlJobHelper.getJobParam();
        log.info("??????????????????????????????????????????" + param);
        if (StringUtils.isNotBlank(param)) {
            StockEmotionDayDTO dto = JsonUtil.readToValue(param, StockEmotionDayDTO.class);
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
            log.info("??????????????????????????????????????????" + JsonUtil.toJson(dto));
            stockScatterUpLimitService.refreshDay(dto);
        }
        log.info("??????????????????????????????????????????");
    }


    @XxlJob("dayTwoAboveUpLimitVolPriceJobHandler")
    public void dayTwoAboveUpLimitVolPriceJobHandler() throws ParseException {
        String param = XxlJobHelper.getJobParam();
        log.info("????????????????????????????????????" + param);
        if (StringUtils.isNotBlank(param)) {
            StockStrategyQueryDTO dto = JsonUtil.readToValue(param, StockStrategyQueryDTO.class);
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
            log.info("????????????????????????????????????" + JsonUtil.toJson(dto));
            stockUpLimitValPriceService.dayTwoAboveUpLimitVolPriceJobHandler(dto);
        }
        log.info("????????????????????????????????????");
    }


    @XxlJob("dayMarketValueUpLimitJobHandler")
    public void dayMarketValueUpLimitJobHandler() throws IllegalAccessException, ParseException {
        String param = XxlJobHelper.getJobParam();
        log.info("????????????????????????????????????" + param);
        if (StringUtils.isNotBlank(param)) {
            StockEmotionDayDTO dto = JsonUtil.readToValue(param, StockEmotionDayDTO.class);
            dto.setDateStr(DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD));
            log.info("????????????????????????????????????" + JsonUtil.toJson(dto));
            stockScatterService.refreshDay(dto);
        }
        log.info("????????????????????????????????????");
    }


    /**
     * ????????????
     *
     * @throws IllegalAccessException
     * @throws ParseException
     */
    @XxlJob("dayAutoLoginJobHandler")
    public void dayAutoLoginJobHandler() {
        stockTradeUserService.autoLogin();
    }


    /**
     * ???????????????,????????????????????????
     *
     * @throws IllegalAccessException
     * @throws ParseException
     */
    @XxlJob("dayAddStockJobHandle")
    public void dayAddStockJobHandle() {
        String param = XxlJobHelper.getJobParam();

        String dateStr="";
        if (StringUtils.isNotBlank(param)) {
            StockEmotionDayDTO dto = JsonUtil.readToValue(param, StockEmotionDayDTO.class);
            dateStr=dto.getDateStr();
            if(!StringUtils.isNotBlank(dateStr)){
                dateStr=DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.YYYY_MM_DD);
            }
        }
        String timeStr = DateTimeUtil.getDateFormat(new Date(), DateTimeUtil.HH_MM);
        if(timeStr.compareTo("09:20")>=0 &&timeStr.compareTo("10:35")<=0){
            stockCronRefreshService.dayAddStockJob(dateStr);
        }
    }

}
