package com.videopost.publisher.http;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Contrato para chamadas HTTP das plataformas, facilitando testes e desacoplamento.
 */
public interface PlatformHttpClient {

    HttpResponseData get(String url, Map<String, String> headers) throws IOException, InterruptedException;

    HttpResponseData postJson(String url, Map<String, String> headers, String jsonBody) throws IOException, InterruptedException;

    HttpResponseData postMultipart(String url, Map<String, String> headers, Map<String, String> formFields, String fileFieldName, Path filePath) throws IOException, InterruptedException;

    HttpResponseData putBinary(String url, Map<String, String> headers, Path filePath, String contentType) throws IOException, InterruptedException;
}
