package ai.typesafe.sdk.errors;

import java.net.http.HttpHeaders;

/** HTTP 5xx: the server failed to handle the request. */
public class InternalServerException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception; see {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)}. */
    public InternalServerException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(statusCode, body, headers, endpoint, detail);
    }
}
