package com.rackpay.api.payment;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PaymentProviderRegistry {
    private final Map<PaymentProviderType, PaymentProvider> providers;
    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        EnumMap<PaymentProviderType, PaymentProvider> map = new EnumMap<>(PaymentProviderType.class);
        for (PaymentProvider provider : providers) {
            if (map.put(provider.type(), provider) != null) throw new IllegalStateException("Duplicate payment provider: " + provider.type());
        }
        providers = null;
        this.providers = Map.copyOf(map);
    }
    public PaymentProvider require(PaymentProviderType type) {
        PaymentProvider provider = providers.get(type);
        if (provider == null) throw new IllegalStateException("Payment provider is not installed: " + type);
        return provider;
    }
    public boolean supports(PaymentProviderType type) { return providers.containsKey(type); }
}
