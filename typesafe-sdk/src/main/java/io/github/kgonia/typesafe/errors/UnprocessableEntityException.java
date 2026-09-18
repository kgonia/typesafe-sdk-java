package io.github.kgonia.typesafe.errors;

import java.net.http.HttpHeaders;

/** HTTP 422: the request body failed server-side validation. */
public class UnprocessableEntityException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception; see {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)}. */
    public UnprocessableEntityException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(statusCode, body, headers, endpoint, detail);
    }
}
