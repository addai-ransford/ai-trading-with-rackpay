package com.rackpay.api.wallet;

import com.rackpay.api.auth.CurrentUserService;
import com.rackpay.api.persistence.transaction.FinancialTransactionJpaRepository;
import com.rackpay.api.persistence.wallet.WalletEntity;
import com.rackpay.api.persistence.wallet.WalletJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/transactions")
public class WalletTransactionController {
    private final CurrentUserService currentUser;
    private final WalletJpaRepository wallets;
    private final FinancialTransactionJpaRepository transactions;

    public WalletTransactionController(CurrentUserService currentUser,
                                       WalletJpaRepository wallets,
                                       FinancialTransactionJpaRepository transactions) {
        this.currentUser = currentUser;
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @GetMapping
    public Page<WalletTransactionResponse> list(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        UUID userId = currentUser.requireUserId(authentication);
        WalletEntity wallet = wallets.findByOwnerId(userId)
            .orElseThrow(() -> new IllegalStateException("RackPay wallet is not provisioned"));

        return transactions.findAllByWalletIdOrderByCreatedAtDesc(
                wallet.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            )
            .map(WalletTransactionResponse::from);
    }
}
