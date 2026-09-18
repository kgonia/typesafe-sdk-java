package io.github.kgonia.typesafe.errors;

import java.net.http.HttpHeaders;

/** HTTP 400: the request was invalid. */
public class BadRequestException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception; see {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)}. */
    public BadRequestException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(statusCode, body, headers, endpoint, detail);
    }
}
