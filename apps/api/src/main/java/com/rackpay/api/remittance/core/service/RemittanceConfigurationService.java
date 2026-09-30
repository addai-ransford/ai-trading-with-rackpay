package com.rackpay.api.remittance.core.service;

import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkEntity;
import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryJpaRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RemittanceConfigurationService {
    private final RemittanceCountryJpaRepository countries;
    private final MobileMoneyNetworkJpaRepository networks;

    public RemittanceConfigurationService(
        RemittanceCountryJpaRepository countries,
        MobileMoneyNetworkJpaRepository networks
    ) {
        this.countries = countries;
        this.networks = networks;
    }

    public List<CountryResponse> countries(Direction direction) {
        return countries.findAll().stream()
            .filter(RemittanceCountryEntity::isEnabled)
            .filter(country -> direction == Direction.SEND
                ? country.isSendEnabled()
                : country.isReceiveEnabled())
            .sorted(Comparator.comparing(RemittanceCountryEntity::getName))
            .map(country -> new CountryResponse(
                country.getCode(),
                country.getName(),
                country.getDialCode(),
                country.getCurrencyCode(),
                country.isSendEnabled(),
                country.isReceiveEnabled()
            ))
            .toList();
    }

    public List<NetworkResponse> networks(String countryCode) {
        String normalized = requireCountryCode(countryCode);

        RemittanceCountryEntity country = countries
            .findByCodeIgnoreCaseAndEnabledTrue(normalized)
            .orElseThrow(() -> new IllegalArgumentException("Destination country is not enabled"));

        if (!country.isReceiveEnabled()) {
            throw new IllegalArgumentException("Destination country does not accept remittances");
        }

        return networks.findAll().stream()
            .filter(network -> network.isEnabled()
                && network.getCountryCode().equalsIgnoreCase(normalized))
            .sorted(Comparator.comparing(MobileMoneyNetworkEntity::getName))
            .map(network -> new NetworkResponse(
                network.getId(),
                network.getCountryCode(),
                network.getCode(),
                network.getName()
            ))
            .toList();
    }

    private static String requireCountryCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("countryCode is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public enum Direction {
        SEND,
        RECEIVE
    }

    public record CountryResponse(
        String code,
        String name,
        String dialCode,
        String currency,
        boolean sendEnabled,
        boolean receiveEnabled
    ) {}

    public record NetworkResponse(
        java.util.UUID id,
        String countryCode,
        String code,
        String name
    ) {}
}
