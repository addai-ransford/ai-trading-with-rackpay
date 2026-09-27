package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PayoutProviderRegistry {
    private final Map<PayoutProviderType, PayoutProvider> providers;
    private final List<PayoutProviderType> providerOrder;

    public PayoutProviderRegistry(List<PayoutProvider> providers, Environment environment) {
        EnumMap<PayoutProviderType, PayoutProvider> map = new EnumMap<>(PayoutProviderType.class);
        for (PayoutProvider provider : providers) {
            if (map.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Duplicate payout provider: " + provider.type());
            }
        }
        this.providers = Map.copyOf(map);

        String configured = environment.getProperty(
            "rackpay.payout.provider-order",
            "FLUTTERWAVE,PAYSTACK"
        );

        ArrayList<PayoutProviderType> ordered = new ArrayList<>();
        for (String value : configured.split(",")) {
            if (value.isBlank()) continue;
            PayoutProviderType type = PayoutProviderType.valueOf(
                value.trim().toUpperCase(java.util.Locale.ROOT)
            );
            if (map.containsKey(type) && !ordered.contains(type)) ordered.add(type);
        }
        for (PayoutProviderType type : PayoutProviderType.values()) {
            if (map.containsKey(type) && !ordered.contains(type)) ordered.add(type);
        }
        this.providerOrder = List.copyOf(ordered);
    }

    public PayoutProvider require(PayoutProviderType type) {
        PayoutProvider provider = providers.get(type);
        if (provider == null) throw new IllegalStateException("Payout provider is not installed: " + type);
        return provider;
    }

    public PayoutProvider requireRecipientVerificationProvider(String countryCode, Currency currency, PayoutMethod payoutMethod) {
        return providerOrder.stream()
            .map(providers::get)
            .filter(provider -> provider != null)
            .filter(provider -> provider.supportsRecipientVerification(countryCode, currency, payoutMethod))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No payout provider can verify this recipient"));
    }

    public PayoutProvider nextEligible(
        PayoutProviderType current,
        Set<PayoutProviderType> attempted,
        PayoutProvider.CreatePayoutCommand command
    ) {
        return providerOrder.stream()
            .filter(type -> type != current)
            .filter(type -> !attempted.contains(type))
            .map(providers::get)
            .filter(provider -> provider != null)
            .filter(provider -> provider.supportsPayout(command))
            .findFirst()
            .orElse(null);
    }
}
