package ai.typesafe.sdk.errors;

import java.net.http.HttpHeaders;

/** HTTP 403: the API key does not grant access to this resource. */
public class PermissionDeniedException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception; see {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)}. */
    public PermissionDeniedException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(statusCode, body, headers, endpoint, detail);
    }
}
