package com.tester;

import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
@Slf4j
public class QPSUtil {
    ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(100);
    ExecutorService showThread = Executors.newFixedThreadPool(1);

    //总响应时间
    private static AtomicLong totalResponseTime = new AtomicLong(0);
    //总请求数
    private static AtomicInteger totalRequest = new AtomicInteger(0);
    //最近一次响应时间
    private static AtomicLong recentResponseTime = new AtomicLong(0);
    private static AtomicLong beginTime = new AtomicLong(0);
    public void QPSTest(QPSContext qpsContext){

        int expectQps=qpsContext.getExpectQps();
        RestTemplate restTemplate = createRestTemplateForTest(expectQps);
        String url = qpsContext.getUrl();

        beginTime.set(System.currentTimeMillis());
        for (int i = 0; i < expectQps; i++) {
            scheduledThreadPool.scheduleAtFixedRate(()->{
                try {
                    long start = System.currentTimeMillis();
                    Object object = restTemplate.postForEntity(url, null, String.class);
                    //获取一个响应时间差，本次请求的响应时间
                    if(object!=null) {
                        long cost = System.currentTimeMillis() - start;
                        totalResponseTime.addAndGet(cost);
                        //每次自增
                        totalRequest.incrementAndGet();
                        recentResponseTime.set(cost);
                    }else {
                        log.error("请求失败!");
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }

            },0,1, TimeUnit.SECONDS);
        }

        showThread.submit(()->{
            while (true) {
                Thread.sleep(2000);
                long duration = System.currentTimeMillis() - beginTime.get();
                long totalResTime = totalResponseTime.get();
                if (duration != 0&&totalResTime!=0) {
                    System.out.println("QPS: " + 1000 * totalRequest.get() / duration + ", " + "平均响应时间: " + ((float) totalResTime) / totalRequest.get() + "ms."+
                            " 最近一次响应时间: "+recentResponseTime.get()+"ms");
//                    log.info("QPS: " + 1000 * totalRequest.get() / duration + ", " + "平均响应时间: " + ((float) totalResTime) / totalRequest.get() + "ms."+
//                            " 最近一次响应时间: "+recentResponseTime.get()+"ms");

                }
            }
        });
        Scanner scanner = new Scanner(System.in);
        while (!scanner.next().equals("ok")) {

        }

    }
    private ClientHttpRequestFactory getClientHttpRequestFactory(int expectQps) {
  int timeout=5000;
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout)
                .setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).build();
        SocketConfig socketConfig = SocketConfig.custom().setTcpNoDelay(true).setSoLinger(0).setSoKeepAlive(false).build();

        //默认就是支持httpS 的  无需配置  使用长连接  存活10秒
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(10, TimeUnit.SECONDS);
        connectionManager.setMaxTotal(expectQps*2);
        connectionManager.setDefaultMaxPerRoute(expectQps);

        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setDefaultSocketConfig(socketConfig)
                .setConnectionManager(connectionManager)
                .build();
        return new HttpComponentsClientHttpRequestFactory(client);
    }


    public RestTemplate createRestTemplateForTest(int expextQps) {

        RestTemplate restTemplate = new RestTemplate(getClientHttpRequestFactory(expextQps));
        setStringHttpMessageConverterCharset(restTemplate, "utf-8");
        return restTemplate;
    }
    public static void setStringHttpMessageConverterCharset(RestTemplate restTemplate, String charset) {
        // 找出并修改默认的StringHttpMessageConverter
        // 关闭Accept-Charset的输出（防止输出超长的编码列表）
        // 设置默认编码为UTF-8
        for (HttpMessageConverter httpMessageConverter : restTemplate.getMessageConverters()) {
            if (httpMessageConverter instanceof StringHttpMessageConverter) {
                //当对象为String类型时生效
                StringHttpMessageConverter stringHttpMessageConverter = (StringHttpMessageConverter) httpMessageConverter;
                stringHttpMessageConverter.setWriteAcceptCharset(false);
                stringHttpMessageConverter.setDefaultCharset(Charset.forName(charset));
                break;
            }
        }
        List<HttpMessageConverter<?>> messageConverters = restTemplate.getMessageConverters();
        FastJsonHttpMessageConverter fastConverter = new FastJsonHttpMessageConverter();
        messageConverters.add(fastConverter);
    }

}
