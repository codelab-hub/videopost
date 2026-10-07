package com.videopost.infrastructure.http;

import com.videopost.publisher.http.HttpResponseData;
import com.videopost.publisher.http.PlatformHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementação padrão de PlatformHttpClient usando java.net.http.HttpClient nativo do Java 21.
 */
public class JdkPlatformHttpClient implements PlatformHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JdkPlatformHttpClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;

    public JdkPlatformHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public JdkPlatformHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HttpResponseData get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .GET();

        applyHeaders(builder, headers);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toResponseData(response);
    }

    @Override
    public HttpResponseData postJson(String url, Map<String, String> headers, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        applyHeaders(builder, headers);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toResponseData(response);
    }

    @Override
    public HttpResponseData postMultipart(String url,
                                          Map<String, String> headers,
                                          Map<String, String> formFields,
                                          String fileFieldName,
                                          Path filePath) throws IOException, InterruptedException {
        String boundary = "----VideoPostBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipartBytes = buildMultipartBody(boundary, formFields, fileFieldName, filePath);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5)) // Upload de vídeo pode demorar mais
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBytes));

        applyHeaders(builder, headers);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toResponseData(response);
    }

    @Override
    public HttpResponseData putBinary(String url,
                                      Map<String, String> headers,
                                      Path filePath,
                                      String contentType) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(filePath));

        applyHeaders(builder, headers);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toResponseData(response);
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }

    private byte[] buildMultipartBody(String boundary,
                                      Map<String, String> formFields,
                                      String fileFieldName,
                                      Path filePath) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String lineBreak = "\r\n";

        if (formFields != null) {
            for (Map.Entry<String, String> entry : formFields.entrySet()) {
                out.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
                out.write((entry.getValue() + lineBreak).getBytes(StandardCharsets.UTF_8));
            }
        }

        if (fileFieldName != null && filePath != null && Files.exists(filePath)) {
            String filename = filePath.getFileName().toString();
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "video/mp4";
            }

            out.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fileFieldName + "\"; filename=\"" + filename + "\"" + lineBreak).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + contentType + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
            out.write(Files.readAllBytes(filePath));
            out.write(lineBreak.getBytes(StandardCharsets.UTF_8));
        }

        out.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponseData toResponseData(HttpResponse<String> response) {
        Map<String, String> singleHeaders = new HashMap<>();
        response.headers().map().forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                singleHeaders.put(k, v.getFirst());
            }
        });
        return new HttpResponseData(response.statusCode(), response.body(), singleHeaders);
    }
}
