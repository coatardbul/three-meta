
package com.coatardbul.stock.feign.fallback;


import com.coatardbul.stock.common.api.CommonResult;
import com.coatardbul.stock.model.feign.CalendarDateDTO;
import com.coatardbul.stock.model.feign.CalendarSpecialDTO;
import com.coatardbul.stock.model.feign.StockTemplateDto;
import com.coatardbul.stock.model.feign.StockTemplateQueryDTO;
import com.coatardbul.stock.model.feign.StockTimeInterval;
import com.coatardbul.stock.feign.RiverServerFeign;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author: suxiaolei
 * @date: 2019/7/23
 */
@Slf4j
@Component
public class RiverServerFeignFallback implements FallbackFactory<RiverServerFeign> {


    @Override
    public RiverServerFeign create(Throwable throwable) {
        return new RiverServerFeign() {
            @Override
            public CommonResult<String> getQuery(StockTemplateQueryDTO dto) {
                log.error("调用失败", throwable);
                return null;
            }

            /**
             * 间隔数据
             *
             * @param dto
             * @return
             */
            @Override
            public CommonResult<List<String>> getTimeIntervalList(StockTimeInterval dto) {
                log.error("调用失败", throwable);
                return null;
            }

            @Override
            public CommonResult<List<String>> getDate(CalendarDateDTO dto) {
                log.error("调用失败", throwable);
                return null;
            }

            @Override
            public CommonResult<StockTemplateDto> findOne(StockTemplateDto dto) {
                log.error("调用失败", throwable);
                return null;
            }

            @Override
            public CommonResult<String> getSpecialDay(CalendarSpecialDTO dto) {
                log.error("调用失败", throwable);
                return null;            }
        };
    }
}
