package com.coatardbul.stock.mapper;

import com.coatardbul.stock.model.entity.StockWarnLog;import org.apache.ibatis.annotations.Param;import java.util.List;

public interface StockWarnLogMapper {
    int deleteByPrimaryKey(String id);

    int insert(StockWarnLog record);

    int insertSelective(StockWarnLog record);

    StockWarnLog selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(StockWarnLog record);

    int updateByPrimaryKey(StockWarnLog record);

    List<StockWarnLog> selectAllByTemplateIdAndDate(@Param("templateId") String templateId, @Param("date") String date);

    List<StockWarnLog> selectByAll(StockWarnLog stockWarnLog);

    int deleteByDate(@Param("date") String date);

    List<StockWarnLog> selectAllByDateAndTemplateId(@Param("date") String date, @Param("templateId") String templateId);
}