package com.coatardbul.stock.controller.predict;

import com.coatardbul.baseCommon.api.CommonResult;
import com.coatardbul.stock.common.annotation.WebLog;
import com.coatardbul.stock.model.dto.StockEmotionDayDTO;
import com.coatardbul.stock.model.entity.StockStrategyWatch;
import com.coatardbul.stock.service.statistic.business.StockStrategyWatchService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * Note:策略监控
 * <p>
 * Date: 2022/3/6
 *
 * @author Su Xiaolei
 */
@Slf4j
@RestController
@Api(tags = "")
@RequestMapping("/stockStrategyWatch")
public class StockStrategyWatchController {

    @Autowired
    StockStrategyWatchService stockStrategyWatchService;
    /**
     * 策略新增
     * @param dto 请求参数
     * @return
     */
    @WebLog(value = "")
    @RequestMapping(path = "/add", method = RequestMethod.POST)
    public CommonResult add(@Validated @RequestBody StockStrategyWatch dto) {
        stockStrategyWatchService.add(dto);
        return CommonResult.success(null);
    }
    /**
     * 策略修改
     * @param dto 请求参数
     * @return
     */
    @WebLog(value = "")
    @RequestMapping(path = "/modify", method = RequestMethod.POST)
    public CommonResult modify(@Validated @RequestBody StockStrategyWatch dto) {
        stockStrategyWatchService.modify(dto);
        return CommonResult.success(null);
    }
    /**
     * 策略删除
     * @param dto 请求参数
     * @return
     */
    @WebLog(value = "")
    @RequestMapping(path = "/delete", method = RequestMethod.POST)
    public CommonResult delete(@Validated @RequestBody StockStrategyWatch dto) {
        stockStrategyWatchService.delete(dto);
        return CommonResult.success(null);
    }

    /**
     * 全部策略
     * @param
     * @return
     */
    @RequestMapping(path = "/findAll", method = RequestMethod.POST)
    public CommonResult<List<StockStrategyWatch>> findAll() {
        return CommonResult.success(stockStrategyWatchService.findAll());
    }

    /**
     * 根据时间间隔，模拟历史
     */
    @WebLog(value = "")
    @RequestMapping(path = "/hisSimulate", method = RequestMethod.POST)
    public void hisSimulate(@Validated @RequestBody StockEmotionDayDTO dto) throws Exception {
        stockStrategyWatchService.hisSimulate(dto);
    }

    /**
     * 邮件测试
     */
    @WebLog(value = "")
    @RequestMapping(path = "/emailHisStrategy", method = RequestMethod.POST)
    public void emailHisStrategy(@Validated @RequestBody StockEmotionDayDTO dto) throws Exception {
        stockStrategyWatchService.emailStrategyWatch(dto);
    }


}
