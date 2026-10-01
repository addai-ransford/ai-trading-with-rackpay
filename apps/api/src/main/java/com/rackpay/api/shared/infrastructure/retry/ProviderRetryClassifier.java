package com.rackpay.api.shared.infrastructure.retry;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

public final class ProviderRetryClassifier {
    private ProviderRetryClassifier() {}

    public static boolean isTransient(Throwable failure) {
        if (failure instanceof ResourceAccessException) return true;
        if (failure instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            return status == 408 || status == 429 || status == 500 ||
                   status == 502 || status == 503 || status == 504;
        }
        return false;
    }
}
