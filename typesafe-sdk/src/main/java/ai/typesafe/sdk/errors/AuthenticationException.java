package ai.typesafe.sdk.errors;

import java.net.http.HttpHeaders;

/** HTTP 401: the API key is missing or invalid. */
public class AuthenticationException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception; see {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)}. */
    public AuthenticationException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(statusCode, body, headers, endpoint, detail);
    }
}
