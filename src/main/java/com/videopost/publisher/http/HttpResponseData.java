package com.videopost.publisher.http;

import java.util.Collections;
import java.util.Map;

/**
 * Resposta HTTP simplificada para adaptadores de API.
 */
public record HttpResponseData(
        int statusCode,
        String body,
        Map<String, String> headers
) {
    public HttpResponseData {
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
