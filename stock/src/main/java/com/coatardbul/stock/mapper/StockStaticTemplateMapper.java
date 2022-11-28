package com.coatardbul.stock.mapper;

import com.coatardbul.stock.model.entity.StockStaticTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockStaticTemplateMapper {
    int deleteByPrimaryKey(String id);

    int insert(StockStaticTemplate record);

    int insertSelective(StockStaticTemplate record);

    StockStaticTemplate selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(StockStaticTemplate record);

    int updateByPrimaryKey(StockStaticTemplate record);

    List<StockStaticTemplate> selectAll();

    List<StockStaticTemplate> selectAllByStaticLatitudeAndObjectSignAndRemarkLike(@Param("staticLatitude")String staticLatitude,@Param("objectSign")String objectSign,@Param("likeRemark")String likeRemark);



    List<StockStaticTemplate> selectAllByObjectSign(@Param("objectSign") String objectSign);

}