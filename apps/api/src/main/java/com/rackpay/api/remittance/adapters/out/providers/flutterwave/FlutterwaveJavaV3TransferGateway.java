package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
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
 * The upstream SDK stores credentials in JVM-wide system properties.
 * Serialize SDK configuration + invocation so concurrent RackPay requests
 * cannot overwrite the configured credentials during a transfer.
 */
@Component
public class FlutterwaveJavaV3TransferGateway implements FlutterwaveTransferGateway {

    private static final Object SDK_LOCK = new Object();

    private final String secretKey;
    private final String publicKey;
    private final String encryptionKey;
    private final String callbackUrl;

    public FlutterwaveJavaV3TransferGateway(
            @Value("${rackpay.payout.flutterwave.secret-key:}") String secretKey,
            @Value("${rackpay.payout.flutterwave.public-key:}") String publicKey,
            @Value("${rackpay.payout.flutterwave.encryption-key:}") String encryptionKey,
            @Value("${rackpay.payout.flutterwave.callback-url:}") String callbackUrl
    ) {
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.encryptionKey = encryptionKey;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public TransferResponse createTransfer(TransferCommand command) {
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("Flutterwave secret key is not configured");
        }
        Objects.requireNonNull(command, "transfer command is required");
        if (!StringUtils.hasText(command.beneficiaryName())) {
            throw new IllegalArgumentException("Flutterwave beneficiary name is required");
        }

        synchronized (SDK_LOCK) {
            try {
                Environment.setSecretKey(secretKey);
                Environment.setPublicKey(publicKey);
                Environment.setEncryptionKey(encryptionKey);

                TransferRequest request = new BeneficiaryTransferRequest(
                        StringUtils.hasText(callbackUrl) ? callbackUrl : null,
                        command.reference(),
                        command.debitCurrency(),
                        command.bankCode(),
                        command.accountNumber(),
                        command.narration(),
                        command.amount(),
                        command.currency(),
                        command.beneficiaryName(),
                        command.senderName(),
                        command.senderCountry(),
                        command.senderMobileNumber()
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

                return new TransferResponse(providerId, response.getStatus(), status, response.getMessage());
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

    /**
     * The upstream v3 SDK model omits beneficiary_name, which Flutterwave
     * requires for mobile-money payouts. Its request serializer uses this
     * runtime subclass's Jackson property when posting the transfer.
     */
    static final class BeneficiaryTransferRequest extends TransferRequest {
        @JsonProperty("beneficiary_name")
        private final String beneficiaryName;
        @JsonProperty("meta")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final SenderMeta meta;

        BeneficiaryTransferRequest(
                String callbackUrl,
                String reference,
                String debitCurrency,
                String accountBank,
                String accountNumber,
                String narration,
                java.math.BigDecimal amount,
                String currency,
                String beneficiaryName
        ) {
            this(callbackUrl, reference, debitCurrency, accountBank, accountNumber, narration,
                    amount, currency, beneficiaryName, null, null, null);
        }

        BeneficiaryTransferRequest(
                String callbackUrl,
                String reference,
                String debitCurrency,
                String accountBank,
                String accountNumber,
                String narration,
                java.math.BigDecimal amount,
                String currency,
                String beneficiaryName,
                String senderName,
                String senderCountry,
                String senderMobileNumber
        ) {
            super(callbackUrl, reference, debitCurrency, accountBank, accountNumber, narration, amount, currency);
            this.beneficiaryName = beneficiaryName;
            this.meta = StringUtils.hasText(senderName) ? new SenderMeta(senderName, senderCountry, senderMobileNumber) : null;
        }
    }

    static final class SenderMeta {
        @JsonProperty("sender") private final String sender;
        @JsonProperty("sender_country") private final String senderCountry;
        @JsonProperty("mobile_number") private final String mobileNumber;

        SenderMeta(String sender, String senderCountry, String mobileNumber) {
            this.sender = sender;
            this.senderCountry = senderCountry;
            this.mobileNumber = mobileNumber;
        }
    }

    public static final class FlutterwaveTransferException extends RuntimeException {
        public FlutterwaveTransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
