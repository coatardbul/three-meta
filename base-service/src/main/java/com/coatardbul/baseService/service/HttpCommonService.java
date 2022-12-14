package com.coatardbul.baseService.service;

import com.coatardbul.baseCommon.model.bo.CronRefreshConfigBo;
import com.coatardbul.baseService.entity.bo.HttpConfigBo;
import com.coatardbul.baseService.entity.bo.HttpResponseInfo;
import com.google.common.net.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * Note:
 * <p>
 * Date: 2022/11/25
 *
 * @author Su Xiaolei
 */
@Slf4j
@Service
public abstract class HttpCommonService {

    public HttpRequestRetryHandler httpRequestRetryHandler;

    @Autowired
    CronRefreshService cronRefreshService;


    @Autowired
    ProxyIpService proxyIpService;


    public HttpResponseInfo executeHttpRequest(HttpConfigBo httpConfigBo, boolean isProxy) {
        HttpResponseInfo result = new HttpResponseInfo();

        CloseableHttpClient httpClient = httpConfigBo.getHttpClient();
        HttpRequestBase httpRequestBase = httpConfigBo.getHttpRequestBase();
        try {
            CloseableHttpResponse response = httpClient.execute(httpRequestBase);
            httpConfigBo.setCloseableHttpResponse(response);
            log.info("???????????????" + httpRequestBase.toString() + "???????????????:" + response.getStatusLine());
            //???????????????
            result.setHttpStatus(response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                //??????json??????
                String res = EntityUtils.toString(response.getEntity());
                result.setResponseStr(res);
            }
            //407 Proxy Authentication Required
//            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
//                //??????json??????
//                throw new  ConnectTimeoutException();
//            }
        } catch (ConnectTimeoutException | HttpHostConnectException e) {
            if (isProxy) {
                //????????????ip?????????
                proxyIpService.deleteByIp(httpConfigBo.getProxy().getHostName());
                log.error("????????????ip???" + httpConfigBo.getProxy().getHostName() + " ?????????" + httpConfigBo.getProxy().getPort());
            }
//            result.setHttpStatus(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);
            log.error("httpclient????????????" + e.getMessage());
        } catch (ClientProtocolException e) {
            log.error("ClientProtocolException" + e.getMessage());
        } catch (IOException e) {
            if (isProxy) {
                //????????????ip?????????
                proxyIpService.deleteByIp(httpConfigBo.getProxy().getHostName());
                log.error("????????????ip???" + httpConfigBo.getProxy().getHostName() + " ?????????" + httpConfigBo.getProxy().getPort());
            }
//            result.setHttpStatus(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);
            log.error("httpclient??????IO??????" + e.getMessage());
        } finally {
            closeStream(httpConfigBo);
        }
        return result;
    }


    /**
     * ????????????????????????
     */
    public void setHttpRequestRetryHandler() {
        httpRequestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                if (executionCount >= 5) {// ?????????????????????5???????????????
                    return false;
                }
                if (exception instanceof NoHttpResponseException) {// ????????????????????????????????????????????????
                    return true;
                }
                if (exception instanceof SSLHandshakeException) {// ????????????SSL????????????
                    return false;
                }
                if (exception instanceof InterruptedIOException) {// ??????
                    return false;
                }
                if (exception instanceof UnknownHostException) {// ????????????????????????
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {// ???????????????
                    return false;
                }
                if (exception instanceof SSLException) {// SSL????????????
                    return false;
                }

                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                // ??????????????????????????????????????????
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }
                return false;
            }
        };

    }


    abstract void closeStream(HttpConfigBo httpConfigBo);


    public void defaultCloseStream(HttpConfigBo httpConfigBo) {
        CloseableHttpClient httpClient = httpConfigBo.getHttpClient();
        HttpRequestBase httpRequestBase = httpConfigBo.getHttpRequestBase();
        CloseableHttpResponse response = httpConfigBo.getCloseableHttpResponse();
        try {
            // ????????????
            if (httpClient != null) {
                httpClient.close();
            }
            if (httpRequestBase != null) {
                httpRequestBase.releaseConnection();
            }
            if (response != null) {
                HttpEntity entity = response.getEntity();
                EntityUtils.consumeQuietly(entity);
                EntityUtils.consume(entity);
                response.close();
            }
        } catch (IOException e) {
            log.error("httpclient?????????????????????");
        }
    }


    private HttpRequestBase setHttpGet(String url, List<Header> headerList, HttpConfigBo httpConfigBo) {
        HttpRequestBase httpGet = new HttpGet(url);
        setHeader(headerList, httpGet);
        httpConfigBo.setHttpRequestBase(httpGet);
        return httpGet;
    }

    private HttpRequestBase setHttpPost(String url, String jsonString, List<Header> headerList, HttpConfigBo httpConfigBo) {
        HttpRequestBase httpPost = setHttpPost(url, jsonString, headerList);
        httpConfigBo.setHttpRequestBase(httpPost);
        return httpPost;
    }


    /**
     * ??????Content-Type == application/x-www-form-urlencoded????????????????????????
     * @param url
     * @param jsonString
     * @param headerList
     * @return
     */
    public HttpPost setHttpPost(String url, String jsonString, List<Header> headerList) {
        HttpPost httpPost = new HttpPost(url);
        StringEntity entity = new StringEntity(jsonString, "UTF-8");
        // post???????????????????????????????????????????????????;?????????entity??????post????????????
        httpPost.setEntity(entity);
        if (headerList != null && headerList.size() > 0) {
            List<Header> collect = headerList.stream().filter(item -> item.getName().equals("Content-Type")).collect(Collectors.toList());
            if (collect.size() == 0) {
                httpPost.addHeader("Content-Type", "application/json;charset=utf8");
            }
        }
        setHeader(headerList, httpPost);
        return httpPost;
    }




    public void setPostMapEntity(HttpEntityEnclosingRequestBase request, Map<String, Object> params) {
        if (params != null) {
            List<BasicNameValuePair> parameters = params.entrySet().stream().map(entry ->
                    new BasicNameValuePair(entry.getKey(), String.valueOf(entry.getValue()))
            ).collect(Collectors.toList());
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(parameters, Consts.UTF_8);
            request.setEntity(entity);
        }

    }


    private void setHeader(List<Header> headerList, HttpRequestBase httpRequestBase) {
        if (headerList != null && headerList.size() > 0) {
            for (Header headerTemp : headerList) {
                httpRequestBase.addHeader(headerTemp);
            }
        }
        httpRequestBase.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36");
//        httpRequestBase.addHeader(HttpHeaders.CONNECTION, "close");
    }


    public HttpConfigBo getHttpConfig(boolean isProxy) {
        HttpConfigBo httpConfigBo = new HttpConfigBo();
        //?????????????????????????????????????????????
        setRequestConfig(httpConfigBo, isProxy);
        //???????????????
        setCloseableHttpClient(httpConfigBo, isProxy);

        return httpConfigBo;

    }

    abstract void setCloseableHttpClient(HttpConfigBo httpConfigBo, boolean isProxy);

    public void setRequestConfig(HttpConfigBo httpConfigBo, boolean isProxy) {
        CronRefreshConfigBo cronRefreshConfigBo = cronRefreshService.getCronRefreshConfigBo();
        Integer sockTimeout = cronRefreshConfigBo.getSockTimeout();
        RequestConfig defaultRequestConfig = null;
        if (isProxy) {
            HttpHost proxy = proxyIpService.getNewProxyHttpHost();
            httpConfigBo.setProxy(proxy);
            defaultRequestConfig = RequestConfig.custom().setConnectTimeout(sockTimeout).setConnectionRequestTimeout(sockTimeout).setSocketTimeout(sockTimeout).setProxy(proxy).build();
        } else {
            defaultRequestConfig = RequestConfig.custom().setConnectTimeout(sockTimeout).setConnectionRequestTimeout(sockTimeout).setSocketTimeout(sockTimeout).build();
        }
        httpConfigBo.setDefaultRequestConfig(defaultRequestConfig);
    }


    /**
     * ?????????????????????????????????????????????????????????????????????
     *
     * @param proxy
     * @param isProxy
     * @return
     */
    public CloseableHttpClient getProxyHttpClient(HttpHost proxy, boolean isProxy) {
        Integer sockTimeout = cronRefreshService.getSockTimeout();
        CloseableHttpClient httpClient = null;
        if (isProxy) {
            RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectTimeout(sockTimeout)
                    .setConnectionRequestTimeout(sockTimeout)
                    .setSocketTimeout(sockTimeout)
                    .setProxy(proxy).build();
            httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).setRetryHandler(httpRequestRetryHandler).build();
        } else {
            // ??????Http?????????(???????????????:???????????????????????????;??????:?????????HttpClient???????????????????????????)
            RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectTimeout(sockTimeout)
                    .setConnectionRequestTimeout(sockTimeout)
                    .setSocketTimeout(sockTimeout)
                    .build();
            httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).setRetryHandler(httpRequestRetryHandler).build();
        }
        return httpClient;
    }

    public CloseableHttpClient getProxyHttpClient(CookieStore cookieStore) {
        Integer sockTimeout = cronRefreshService.getSockTimeout();
        CloseableHttpClient httpClient = null;

        // ??????Http?????????(???????????????:???????????????????????????;??????:?????????HttpClient???????????????????????????)
        RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectTimeout(sockTimeout)
                .setConnectionRequestTimeout(sockTimeout)
                .setSocketTimeout(sockTimeout)
                .build();
        httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).setDefaultCookieStore(cookieStore).setRetryHandler(httpRequestRetryHandler).build();

        return httpClient;
    }


    /**
     * ???????????????
     *
     * @param param
     * @param value
     * @return
     */
    public Header getHead(String param, String value) {
        return new BasicHeader(param, value);
    }


    public String doGet(String url) throws ConnectTimeoutException {
        return doGet(url, null, false);
    }

    public String doGet(String url, List<Header> headerList, boolean isProxy) throws ConnectTimeoutException {
        //??????HttpClient??????
        HttpConfigBo httpConfigBo = getHttpConfig(isProxy);
        //???????????????
        setHttpGet(url, headerList, httpConfigBo);
        //????????????
        HttpResponseInfo httpResponseInfo = executeHttpRequest(httpConfigBo, isProxy);

        if (HttpStatus.SC_OK == httpResponseInfo.getHttpStatus()) {
            return httpResponseInfo.getResponseStr();
        } else {
            return null;
        }
    }

    /**
     * ??????????????????json
     *
     * @param url
     * @param jsonString
     * @param headerList
     * @return
     */
    public String doPost(String url, String jsonString, List<Header> headerList) throws ConnectTimeoutException {
        return doPost(url, jsonString, headerList, true);
    }

    public String doPost(String url, String jsonString, List<Header> headerList, boolean isProxy) throws ConnectTimeoutException {
        //??????HttpClient??????
        HttpConfigBo httpConfigBo = getHttpConfig(isProxy);
        //???????????????
        setHttpPost(url, jsonString, headerList, httpConfigBo);

        HttpResponseInfo httpResponseInfo = executeHttpRequest(httpConfigBo, isProxy);
        //????????????
        if (HttpStatus.SC_OK == httpResponseInfo.getHttpStatus()) {
            return httpResponseInfo.getResponseStr();
        } else {
            return null;
        }
        //??????json???????????????

    }
}
