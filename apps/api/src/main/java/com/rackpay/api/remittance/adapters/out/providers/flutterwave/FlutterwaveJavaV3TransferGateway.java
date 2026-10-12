package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.flutterwave.bean.Response;
import com.flutterwave.bean.TransferRequest;
import com.flutterwave.services.Transfer;
import com.flutterwave.utility.Environment;

/**
 * Adapter for Flutterwave's official Java-v3 SDK.
 *
 * The upstream SDK stores its secret key in a JVM-wide system property.
 * Serialize configuration + SDK invocation so concurrent RackPay requests
 * cannot overwrite the key between those operations.
 */
@Component
public class FlutterwaveJavaV3TransferGateway implements FlutterwaveTransferGateway {

    private static final Object SDK_LOCK = new Object();

    private final String secretKey;
    private final String callbackUrl;

    public FlutterwaveJavaV3TransferGateway(
            @Value("${rackpay.payout.flutterwave.secret-key:}") String secretKey,
            @Value("${rackpay.payout.flutterwave.callback-url:}") String callbackUrl
    ) {
        this.secretKey = secretKey;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public TransferResponse createTransfer(TransferCommand command) {
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("Flutterwave secret key is not configured");
        }
        Objects.requireNonNull(command, "transfer command is required");

        synchronized (SDK_LOCK) {
            try {
                Environment.setSecretKey(secretKey);
                TransferRequest request = new TransferRequest(
                        StringUtils.hasText(callbackUrl) ? callbackUrl : null,
                        command.reference(),
                        command.debitCurrency(),
                        command.bankCode(),
                        command.accountNumber(),
                        command.narration(),
                        command.amount(),
                        command.currency()
                );

                Response response = new Transfer().runTransaction(request);
                if (response == null) {
                    throw new IllegalStateException("Flutterwave SDK returned an empty transfer response");
                }

                String providerId = response.getData() == null
                        || response.getData().getId() <= 0
                        ? null
                        : Integer.toString(response.getData().getId());
                String status = response.getData() == null
                        ? null
                        : response.getData().getStatus();

                return new TransferResponse(providerId, status, response.getMessage());
            } catch (RuntimeException exception) {
                // Do not turn transport/SDK exceptions into a failed payout:
                // the provider may have accepted the transfer before the error.
                throw new FlutterwaveTransferException(
                        "Flutterwave transfer outcome is uncertain; reconcile by reference before retrying",
                        exception
                );
            }
        }
    }

    public static final class FlutterwaveTransferException extends RuntimeException {
        public FlutterwaveTransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
