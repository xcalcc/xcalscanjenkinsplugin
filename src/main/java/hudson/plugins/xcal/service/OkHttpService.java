package hudson.plugins.xcal.service;

import com.squareup.okhttp.*;
import hudson.plugins.xcal.util.CommonUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class OkHttpService {
    public static final Logger log = LoggerFactory.getLogger(OkHttpService.class);

    private final OkHttpClient okHttpClient;

    public OkHttpService() {
        this.okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(10, TimeUnit.SECONDS);
    }

    /**
     * @param token token for the Authorization
     * @return Header contain "application/json" and contain Authorization
     */
    public static Headers buildDefaultJsonHeader(String token) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (!StringUtils.isEmpty(token)) {
            headers.put("Authorization", token);
        }
        return Headers.of(headers);
    }

    /**
     * HTTP GET call
     *
     * @param url   url of the http get address
     * @param token token for the http get method
     * @return response in json
     * @throws IOException for the response in null or http code not between 200 to 300.
     */
    public String get(String url, String token) throws IOException {
        Headers headers = OkHttpService.buildDefaultJsonHeader(token);
        Request request = new Request.Builder().url(url).headers(headers).build();
        Response response = okHttpClient.newCall(request).execute();
        if (response.isSuccessful()) {
            return response.body().string();
        } else {
            throw new IOException("Unexpected code " + response);
        }
    }

    /**
     * HTTP POST call
     *
     * @param url   url of the http post address
     * @param token token for the http post method
     * @param json  json string for http post
     * @return response in json
     * @throws IOException for the response in null or http code not between 200 to 300.
     */
    public String post(String url, String token, String json) throws IOException {
        log.debug("[post] url: {}, json: {}", url, json);
        Headers headers = buildDefaultJsonHeader(token);
        MediaType jsonMediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(jsonMediaType, json);
        Request request = new Request.Builder().url(url).post(body).headers(headers).build();
        Response response = okHttpClient.newCall(request).execute();
        log.debug("[post] response, successful: {}, body: {}", response.isSuccessful(), CommonUtil.writeObjectToJsonStringSilently(response.body()));
        if (response.isSuccessful()) {
            return response.body().string();
        } else {
            throw new IOException("Unexpected code " + response);
        }
    }

}
