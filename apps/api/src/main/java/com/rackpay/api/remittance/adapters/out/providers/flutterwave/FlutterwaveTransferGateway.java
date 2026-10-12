package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import java.math.BigDecimal;

/**
 * Narrow boundary around Flutterwave's transfer API. Keeping the SDK behind
 * this interface lets RackPay test payout decisions without network access.
 */
public interface FlutterwaveTransferGateway {

    TransferResponse createTransfer(TransferCommand command);

    record TransferCommand(
            String reference,
            BigDecimal amount,
            String currency,
            String debitCurrency,
            String bankCode,
            String accountNumber,
            String narration
    ) {}

    record TransferResponse(String providerTransferId, String responseStatus, String status, String message) {}
}
