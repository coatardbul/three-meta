package com.coatardbul.stock.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.coatardbul.baseCommon.api.CommonResult;
import com.coatardbul.baseCommon.constants.AiStrategyEnum;
import com.coatardbul.baseCommon.constants.StockTemplateEnum;
import com.coatardbul.baseCommon.model.bo.Chip;
import com.coatardbul.baseCommon.model.bo.StrategyBO;
import com.coatardbul.baseCommon.model.dto.StockStrategyQueryDTO;
import com.coatardbul.baseCommon.util.JsonUtil;
import com.coatardbul.baseService.entity.bo.StockTemplatePredict;
import com.coatardbul.baseService.feign.BaseServerFeign;
import com.coatardbul.baseService.service.DongFangCommonService;
import com.coatardbul.baseService.service.HttpPoolService;
import com.coatardbul.baseService.service.SnowFlakeService;
import com.coatardbul.baseService.service.StockStrategyCommonService;
import com.coatardbul.baseService.service.StockUpLimitAnalyzeCommonService;
import com.coatardbul.baseService.service.romote.RiverRemoteService;
import com.coatardbul.stock.common.annotation.WebLog;
import com.coatardbul.stock.mapper.StockTemplatePredictMapper;
import com.coatardbul.stock.service.base.CosService;
import com.coatardbul.stock.service.base.EmailService;
import com.coatardbul.stock.service.statistic.RedisService;
import com.coatardbul.stock.service.statistic.StockCronRefreshService;
import com.coatardbul.stock.service.statistic.StockQuartzService;
import com.coatardbul.stock.service.statistic.StockSpecialStrategyService;
import com.coatardbul.stock.service.statistic.trade.StockTradeService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.Bucket;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.CreateBucketRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import io.swagger.annotations.Api;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/2/18
 *
 * @author Su Xiaolei
 */
@Slf4j
@RestController
@Api(tags = "")
@RequestMapping("/test")
public class TestController {
    @Autowired
    StockSpecialStrategyService stockSpecialStrategyService;
    @Autowired
    SnowFlakeService snowFlakeService;
    @Autowired
    StockTemplatePredictMapper stockTemplatePredictMapper;
    @Autowired
    StockStrategyCommonService stockStrategyCommonService;
    @Autowired
    RedisService redisService;
    @Autowired
    EmailService emailService;
    @Autowired
    CosService cosService;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    RiverRemoteService riverRemoteService;
    @Autowired
    StockUpLimitAnalyzeCommonService stockUpLimitAnalyzeCommonService;

    @Autowired
    StockTradeService stockTradeService;

    @Autowired
    HttpPoolService httpService;
    @Autowired
    StockQuartzService stockQuartzService;
    @Autowired
    BaseServerFeign baseServerFeign;
    @Autowired

    StockCronRefreshService stockCronRefreshService;

    @Autowired
    DongFangCommonService dongFangCommonService;

    @WebLog(value = "")
    @RequestMapping(path = "/test", method = RequestMethod.GET)
    public CommonResult dayStatic() throws Exception {
        String response = null;
        int retryNum = 10;
        while (retryNum > 0) {
            response = dongFangCommonService.getDayKlineChip("002579");
            if (StringUtils.isNotBlank(response)) {
                break;
            } else {
                retryNum--;
            }
        }
        if (!StringUtils.isNotBlank(response)) {
            return null;
        }

        int beginIndex = response.indexOf("(");
        int endIndex = response.lastIndexOf(")");
        response = response.substring(beginIndex + 1, endIndex);
        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONObject data = jsonObject.getJSONObject("data");
        JSONArray klines = data.getJSONArray("klines");
//        return CommonResult.success(klines);

        List<List<String>> helloList = new ArrayList<List<String>>();


        for (int i = 0; i < klines.size(); i++) {
            Object o = klines.get(i);
            if (o instanceof String) {
                List<String> item = new ArrayList<String>();
                String o1 = (String) o;
                String[] split = o1.split(",");
                item.add(split[0]);
                item.add(split[1]);
                item.add(split[2]);
                item.add(split[3]);
                item.add(split[4]);
                item.add(split[5]);
                item.add(split[6]);
                item.add(split[7] + "%");
                item.add(split[10]);
                item.add(split[8]);
                item.add(split[9]);
                helloList.add(item);
            }
        }

//        List<Object> collect = klines.stream().collect(Collectors.toList());
        String result = "";
        // ??????JS????????????
        ScriptEngine se = new ScriptEngineManager().getEngineByName("javascript");
        String userPath = System.getProperty("user.dir");
        FileReader fileReader = new FileReader(userPath + "/stock/js/chip.js");

        se.eval(fileReader);
        // ???????????????
        if (se instanceof Invocable) {
            Invocable in = (Invocable) se;

            String s = JsonUtil.toJson(helloList);

            Object ccc = in.invokeFunction("calcChip", helloList.size() - 1, 150, 120, s);
            //????????????
            BigDecimal benefitPart = new BigDecimal(((ScriptObjectMirror) ccc).get("benefitPart").toString()).multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
            //????????????
            BigDecimal avgCost = new BigDecimal(((ScriptObjectMirror) ccc).get("avgCost").toString()).setScale(2, BigDecimal.ROUND_HALF_UP);
            //?????????
            Object percentChips = ((ScriptObjectMirror) ccc).get("percentChips");
            Object ninePrecent = ((ScriptObjectMirror) percentChips).get("90");
            String concentration = ((ScriptObjectMirror) ninePrecent).get("concentration").toString();
            BigDecimal bigDecimal = new BigDecimal(concentration).multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP);

            List<Double> x = getCoordArr("x", ccc);
            List<Double> y = getCoordArr("y", ccc);

            ((ScriptObjectMirror) ccc).get("x");
            Chip reslult = new Chip();
            reslult.setBenefitPart(benefitPart);
            reslult.setAvgCost(avgCost);
            reslult.setConcentration(bigDecimal);
            reslult.setXcoord(x);
            reslult.setYcoord(y);
            return CommonResult.success(reslult);
        }

        return CommonResult.success(null);

//        stockQuartzService.xxx();
//        List<Header> headerList = new ArrayList<>();
//        Header cookie = httpService.getHead("Referer", "https://finance.sina.com.cn/realstock/company/sz002866/nc.shtml");
//        headerList.add(cookie);
//
//        String s = httpService.doGet("https://hq.sinajs.cn/rn=1666375012860&list=sz002866,sz002866_i,bk_new_qtxy", headerList, false);
//        System.out.println(s);
    }


    private List<Double> getCoordArr(String key, Object ccc) {
        List<Double> result = new ArrayList<Double>();
        Object x = ((ScriptObjectMirror) ccc).get(key);
        for (int i = 0; i < 150; i++) {
            Object o = ((ScriptObjectMirror) x).get(String.valueOf(i));
            result.add((Double) o);
        }
        return result;
    }

    @RequestMapping(path = "/test1", method = RequestMethod.POST)
    public String cosUpload(MultipartFile file) throws Exception {
        // 1 ??????????????????????????????secretId, secretKey??????
// SECRETID???SECRETKEY?????????????????????????????? https://console.cloud.tencent.com/cam/capi ?????????????????????
        String secretId = "AKIDc4JL8gy1oNhmbAUYNwGK9i26vWyB9uPf";
        String secretKey = "3qOhkX5OREl36uUrzrUryqSovnPlriKK";
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);

// 2 ?????? bucketName ?????????, COS ???????????????????????? https://cloud.tencent.com/document/product/436/6224
// clientConfig ?????????????????? region, https(?????? http), ??????, ????????? set ??????, ??????????????????????????????????????? Java SDK ?????????
        Region region = new Region("ap-nanjing");
        ClientConfig clientConfig = new ClientConfig(region);
// ???????????????????????? https ??????
// ??? 5.6.54 ?????????????????????????????? https
        clientConfig.setHttpProtocol(HttpProtocol.https);
// 3 ?????? cos ????????????
        COSClient cosClient = new COSClient(cred, clientConfig);


        String bucketName = "stable-up-1254163062"; //???????????????????????????BucketName-APPID
        CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName);
// ?????? bucketName ???????????? Private(????????????)?????????????????? PublicRead???????????????????????????PublicReadWrite??????????????????
        createBucketRequest.setCannedAcl(CannedAccessControlList.Private);
        List<Bucket> buckets = cosClient.listBuckets();
        for (Bucket bucketElement : buckets) {
            String bucketName1 = bucketElement.getName();
            String bucketLocation = bucketElement.getLocation();
            System.out.println(bucketName1);
        }
        // ????????????????????????
        File localFile = new File("/Users/coatardbul/Library/CloudStorage/OneDrive-??????/??????/??????/davp/????????????/????????????/555.jpg");
// ????????????????????????????????????
// ????????????????????? COS ????????????????????????????????????????????????folder/picture.jpg????????????????????? picture.jpg ????????? folder ?????????
        //????????????????????????
        String key = "images/444.jpg";
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, localFile);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        putObjectResult.getETag();


        // Bucket?????????????????? BucketName-APPID ???????????????????????????????????????????????????
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
        // ??????bucket??????
        listObjectsRequest.setBucketName(bucketName);
        // prefix???????????????object???key???prefix??????
        listObjectsRequest.setPrefix("images/");
        // deliter???????????????, ?????????/??????????????????????????????object, ?????????????????????????????????object
        listObjectsRequest.setDelimiter("/");
        // ????????????????????????????????????, ??????listobject????????????1000
        listObjectsRequest.setMaxKeys(1000);
        ObjectListing objectListing = null;
        do {
            try {
                objectListing = cosClient.listObjects(listObjectsRequest);
            } catch (CosServiceException e) {
                e.printStackTrace();
            } catch (CosClientException e) {
                e.printStackTrace();
            }
            // common prefix???????????????delimiter???????????????, ???delimter?????????/, common prefix?????????????????????????????????
            List<String> commonPrefixs = objectListing.getCommonPrefixes();

            // object summary?????????????????????object??????
            List<COSObjectSummary> cosObjectSummaries = objectListing.getObjectSummaries();
            for (COSObjectSummary cosObjectSummary : cosObjectSummaries) {
                // ???????????????key
                String key1 = cosObjectSummary.getKey();
                // ?????????etag
                String etag = cosObjectSummary.getETag();
                // ???????????????
                long fileSize = cosObjectSummary.getSize();
                // ?????????????????????
                String storageClasses = cosObjectSummary.getStorageClass();
            }

            String nextMarker = objectListing.getNextMarker();
            listObjectsRequest.setMarker(nextMarker);
        } while (objectListing.isTruncated());

        URL objectUrl = cosClient.getObjectUrl(bucketName, "555.jpg");
        objectUrl.getPath();
//        // Bucket?????????????????? BucketName-APPID ???????????????????????????????????????????????????
//        String bucketName = "examplebucket-1250000000";
//// ??????????????????????????? COS ????????????????????????????????????????????????folder/picture.jpg???????????????????????? folder ?????????????????? picture.jpg
//        String key = "exampleobject";
//        cosClient.deleteObject(bucketName, key);


        return null;

    }


    @RequestMapping(path = "/test2", method = RequestMethod.POST)
    public String cosUpload() throws Exception {

        List<String> dateIntervalList = riverRemoteService.getDateIntervalList("2022-12-01", "2023-01-03");
        for (String dateFormat : dateIntervalList) {
            sfdfd(dateFormat);
        }


        return null;

    }


    private void sfdfd(String dateFormat) {
        StockStrategyQueryDTO dto = new StockStrategyQueryDTO();
        dto.setRiverStockTemplateSign(StockTemplateEnum.FIRST_UP_LIMIT.getSign());
        dto.setDateStr(dateFormat);
        StrategyBO strategy = null;
        try {
            strategy = stockStrategyCommonService.strategy(dto);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (strategy == null || strategy.getTotalNum() == 0) {
            return;
        }
        for(int jsonLen=0;jsonLen<strategy.getTotalNum();jsonLen++){
            JSONObject jsonObject = strategy.getData().getJSONObject(jsonLen);
            Map convert = stockUpLimitAnalyzeCommonService.convertFirstUpLimit(jsonObject, dateFormat);
            BigDecimal lastClosePrice = new BigDecimal(convert.get("lastClosePrice").toString());
            BigDecimal multiply = lastClosePrice.multiply(new BigDecimal(1.02));
            for (int i = 1; i < 4; i++) {
                Map nextInfo = getNextInfo(dateFormat, i, (String) convert.get("code"));
                if (new BigDecimal(nextInfo.get("minPrice").toString()).compareTo(multiply) < 0&&new BigDecimal(nextInfo.get("maxPrice").toString()).compareTo(multiply) > 0) {
                    //todo
                    StockTemplatePredict stockTemplatePredict = new StockTemplatePredict();
                    stockTemplatePredict.setId(snowFlakeService.getSnowId());
                    stockTemplatePredict.setDate(nextInfo.get("dateStr").toString());
                    stockTemplatePredict.setTemplatedSign(AiStrategyEnum.UPLIMIT_AMBUSH.getCode());
                    stockTemplatePredict.setTemplatedName(AiStrategyEnum.getDescByCode(stockTemplatePredict.getTemplatedSign()));
                    stockTemplatePredict.setHoldDay(2);
                    stockTemplatePredict.setCode(nextInfo.get("code").toString());
                    stockTemplatePredict.setName(nextInfo.get("name").toString());
                    stockTemplatePredict.setBuyPrice(multiply);
//                stockTemplatePredict.setBuyTime(preQuartzTradeDetail.getTime());
//                stockTemplatePredict.setBuyIncreaseRate(preQuartzTradeDetail.getIncreaseRate());
//                stockTemplatePredict.setCloseIncreaseRate(preQuartzTradeDetail.getCloseIncreaseRate());
                    stockTemplatePredictMapper.insert(stockTemplatePredict);
                    String specialDay = riverRemoteService.getSpecialDay(dateFormat, i+2);
                    try {
                        Map stockDetailMap = dongFangCommonService.getStockDetailMap(stockTemplatePredict.getCode(), specialDay, "11:29");
                        BigDecimal newPrice = new BigDecimal(stockDetailMap.get("newPrice").toString());
                        stockTemplatePredict.setSalePrice(newPrice);
                        stockTemplatePredict.setSaleTime("11:29");
                        stockTemplatePredict.setDetail(stockDetailMap.get("thsIndustry").toString() + "\\n" + stockDetailMap.get("theirConcept").toString());
                    } catch (Exception e) {
                        log.error("????????????11.29???????????????" + e.getMessage());
                    }
                    stockTemplatePredictMapper.updateByPrimaryKey(stockTemplatePredict);
                    break;
                }
            }
        }


    }


    private Map getNextInfo(String dateFormat, Integer num, String code) {

        String specialDay = riverRemoteService.getSpecialDay(dateFormat, num);

        StockStrategyQueryDTO dto = new StockStrategyQueryDTO();
        dto.setRiverStockTemplateSign(StockTemplateEnum.STOCK_DETAIL.getSign());
        dto.setDateStr(specialDay);
        dto.setStockCode(code);
        StrategyBO strategy = null;
        try {
            strategy = stockStrategyCommonService.strategy(dto);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (strategy == null || strategy.getTotalNum() == 0) {
            return null;
        }
        JSONObject jsonObject = strategy.getData().getJSONObject(0);
        Map convert = stockUpLimitAnalyzeCommonService.convert(jsonObject, specialDay);
        return convert;
    }

}

