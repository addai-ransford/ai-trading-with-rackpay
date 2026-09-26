package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PayoutProviderRegistry {
    private final Map<PayoutProviderType, PayoutProvider> providers;

    public PayoutProviderRegistry(List<PayoutProvider> providers) {
        EnumMap<PayoutProviderType, PayoutProvider> map = new EnumMap<>(PayoutProviderType.class);
        for (PayoutProvider provider : providers) {
            if (map.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Duplicate payout provider: " + provider.type());
            }
        }
        this.providers = Map.copyOf(map);
    }

    public PayoutProvider require(PayoutProviderType type) {
        PayoutProvider provider = providers.get(type);
        if (provider == null) throw new IllegalStateException("Payout provider is not installed: " + type);
        return provider;
    }

    public PayoutProvider requireRecipientVerificationProvider(String countryCode, Currency currency, PayoutMethod payoutMethod) {
        return providers.values().stream()
            .filter(provider -> provider.supportsRecipientVerification(countryCode, currency, payoutMethod))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No payout provider can verify this recipient"));
    }
}
