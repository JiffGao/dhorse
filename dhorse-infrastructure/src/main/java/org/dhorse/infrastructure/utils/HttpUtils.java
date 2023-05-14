package org.dhorse.infrastructure.utils;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.dhorse.api.enums.MessageCodeEnum;
import org.dhorse.infrastructure.component.ComponentConstants;
import org.dhorse.infrastructure.component.SpringBeanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class HttpUtils {

	private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
	
	public static boolean pingDHorseServer(String ip) {
		ComponentConstants componentConstants = SpringBeanContext.getBean(ComponentConstants.class);
		String pingUrl = "http://" + ip + ":" + componentConstants.getServerPort() + "/health/ping";
		return get(pingUrl) == 200;
	}
	
	public static int get(String url) {
		return get(url, null);
	}
	
	public static int get(String url, Map<String, String> cookies) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(5000)
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .build();
        HttpGet method = new HttpGet(url);
        method.setConfig(requestConfig);
        method.setHeader("Content-Type", "application/json;charset=UTF-8");
        if(!CollectionUtils.isEmpty(cookies)) {
        	String cookieStr = "";
        	for(Entry<String, String> c : cookies.entrySet()) {
        		cookieStr = cookieStr + c.getKey() + "=" + c.getValue() + ";";
        	}
        	method.setHeader("Cookie", cookieStr);
        }
        try (CloseableHttpResponse response = createHttpClient(url).execute(method)){
        	return response.getStatusLine().getStatusCode();
        } catch (IOException e) {
        	LogUtils.throwException(logger, MessageCodeEnum.HTT_GET_FAILURE);
        }
        return -1;
	}
	
	public static int post(String url, String jsonParam) {
		return post(url, jsonParam, null);
	}
	
	public static int post(String url, String jsonParam, Map<String, String> cookies) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(5000)
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .build();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
        if(!CollectionUtils.isEmpty(cookies)) {
        	String cookieStr = "";
        	for(Entry<String, String> c : cookies.entrySet()) {
        		cookieStr = cookieStr + c.getKey() + "=" + c.getValue() + ";";
        	}
        	httpPost.setHeader("Cookie", cookieStr);
        }
        httpPost.setEntity(new StringEntity(jsonParam, "UTF-8"));
        try (CloseableHttpResponse response = createHttpClient(url).execute(httpPost)){
        	return response.getStatusLine().getStatusCode();
        } catch (IOException e) {
        	LogUtils.throwException(logger, MessageCodeEnum.HTT_POST_FAILURE);
        }
        return -1;
	}
	
	public static CloseableHttpClient createHttpClient(String url) {
		if(!url.startsWith("https")) {
			return HttpClients.createDefault();
		}
		try {
			SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
				// 信任所有
				public boolean isTrusted(X509Certificate[] chain, String authType) {
					return true;
				}
			}).build();
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
			return HttpClients.custom().setSSLSocketFactory(sslsf).build();
		} catch (Exception e) {
			LogUtils.throwException(logger, e, MessageCodeEnum.SSL_CLIENT_FAILURE);
		}
		
		return HttpClients.createDefault();
	}
}
