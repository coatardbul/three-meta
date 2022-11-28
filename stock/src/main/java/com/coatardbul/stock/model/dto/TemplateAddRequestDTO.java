package com.coatardbul.stock.model.dto;

import io.swagger.annotations.ApiModel;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2021/12/26
 *
 * @author Su Xiaolei
 */
@Data
@ApiModel
public class TemplateAddRequestDTO {



    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;
    /**
     * 模板样例
     */
    @NotBlank(message = "模板样例不能为空")
    private String templateExample;
}
