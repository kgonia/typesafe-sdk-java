package io.github.kgonia.typesafe.errors;

import java.net.http.HttpHeaders;

/** HTTP 404: the resource was not found. */
public class NotFoundException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception; see {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)}. */
    public NotFoundException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(statusCode, body, headers, endpoint, detail);
    }
}
