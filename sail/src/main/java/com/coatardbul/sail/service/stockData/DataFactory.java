package com.coatardbul.sail.service.stockData;

import com.coatardbul.baseCommon.constants.DataSourceEnum;
import com.coatardbul.baseService.service.CronRefreshService;
import com.coatardbul.baseService.service.DataServiceBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class DataFactory {

    @Resource
    XinlangService xinlangService;
    @Resource
    DongFangService dongFangService;
    @Resource
    CronRefreshService cronRefreshService;

    public DataServiceBridge build() {
        try {
            String dataSource = cronRefreshService.getDataSource();
            if (DataSourceEnum.XIN_LANG.getSign().equals(dataSource)) {
                return xinlangService;
            }else if (DataSourceEnum.DONG_FANG.getSign().equals(dataSource)) {
                return dongFangService;
            }else {
                return xinlangService;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return xinlangService;
        }

    }
}
