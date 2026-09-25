package com.rackpay.api.service;

import com.rackpay.api.payment.PaymentProviderRegistry;
import com.rackpay.api.payment.PaymentProviderType;
import com.rackpay.api.persistence.payment.PaymentProviderConfigEntity;
import com.rackpay.api.persistence.payment.PaymentProviderConfigJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentProviderService {
    private final PaymentProviderConfigJpaRepository configs;
    private final PaymentProviderRegistry registry;
    public PaymentProviderService(PaymentProviderConfigJpaRepository configs, PaymentProviderRegistry registry){this.configs=configs;this.registry=registry;}
    @Transactional(readOnly=true) public List<PaymentProviderConfigEntity> list(){return configs.findAllByOrderByProviderAsc();}
    @Transactional(readOnly=true) public PaymentProviderType requireActiveProvider(){
        PaymentProviderConfigEntity c=configs.findByActiveTrue().orElseThrow(()->new IllegalStateException("No active payment provider is configured"));
        if(!c.isEnabled())throw new IllegalStateException("Active payment provider is disabled: "+c.getProvider());
        if(!registry.supports(c.getProvider()))throw new IllegalStateException("Payment provider is not installed: "+c.getProvider());
        return c.getProvider();
    }
    @Transactional public PaymentProviderConfigEntity activate(PaymentProviderType provider,String actor){
        if(!registry.supports(provider))throw new IllegalArgumentException("Payment provider is not installed: "+provider);
        Instant now=Instant.now();
        for(PaymentProviderConfigEntity c:configs.findAll())c.deactivate(now,actor);
        PaymentProviderConfigEntity target=configs.findByProvider(provider).orElseThrow(()->new IllegalArgumentException("Payment provider configuration not found: "+provider));
        target.activate(now,actor);return configs.save(target);
    }
    @Transactional public PaymentProviderConfigEntity setEnabled(UUID id,boolean enabled,String actor){
        PaymentProviderConfigEntity c=configs.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Payment provider configuration not found"));
        if(!enabled&&c.isActive())throw new IllegalStateException("Cannot disable the active payment provider; activate another provider first");
        c.setEnabled(enabled,Instant.now(),actor);return c;
    }
}
