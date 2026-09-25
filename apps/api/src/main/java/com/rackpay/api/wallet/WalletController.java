package com.rackpay.api.wallet;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.user.CurrentUserService;
import com.rackpay.api.persistence.wallet.WalletBalanceEntity;
import com.rackpay.api.persistence.wallet.WalletBalanceJpaRepository;
import com.rackpay.api.persistence.wallet.WalletEntity;
import com.rackpay.api.persistence.wallet.WalletJpaRepository;
import com.rackpay.api.service.WalletBalanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {
    private final CurrentUserService currentUser;
    private final WalletJpaRepository wallets;
    private final WalletBalanceJpaRepository balances;
    private final WalletBalanceService walletBalances;

    public WalletController(
        CurrentUserService currentUser,
        WalletJpaRepository wallets,
        WalletBalanceJpaRepository balances,
        WalletBalanceService walletBalances
    ) {
        this.currentUser = currentUser;
        this.wallets = wallets;
        this.balances = balances;
        this.walletBalances = walletBalances;
    }

    @GetMapping
    public WalletResponse getWallet(Authentication authentication) {
        WalletEntity wallet = requireWallet(authentication);
        List<WalletBalanceResponse> balanceResponses = balances
            .findAllByWalletIdOrderByCurrency(wallet.getId())
            .stream()
            .map(WalletBalanceResponse::from)
            .toList();

        return new WalletResponse(wallet.getId(), wallet.getOwnerId(), balanceResponses);
    }

    @PostMapping("/balances")
    public WalletBalanceResponse openBalance(
        Authentication authentication,
        @Valid @RequestBody OpenWalletBalanceRequest request
    ) {
        WalletEntity wallet = requireWallet(authentication);
        WalletBalanceEntity balance = walletBalances.openBalance(
            wallet.getId(),
            request.currency()
        );
        return WalletBalanceResponse.from(balance);
    }

    private WalletEntity requireWallet(Authentication authentication) {
        UUID userId = currentUser.requireUserId(authentication);
        return wallets.findByOwnerId(userId)
            .orElseThrow(() -> new IllegalStateException("RackPay wallet is not provisioned"));
    }
}
