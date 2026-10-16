package com.rackpay.api.remittance.core.service;

import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkEntity;
import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCorridorEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCorridorJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryJpaRepository;
import com.rackpay.api.shared.core.money.Currency;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RemittanceAdminConfigurationService {
    private final RemittanceCountryJpaRepository countries;
    private final MobileMoneyNetworkJpaRepository networks;
    private final RemittanceCorridorJpaRepository corridors;

    public RemittanceAdminConfigurationService(RemittanceCountryJpaRepository countries,
        MobileMoneyNetworkJpaRepository networks, RemittanceCorridorJpaRepository corridors) {
        this.countries = countries;
        this.networks = networks;
        this.corridors = corridors;
    }

    @Transactional(readOnly = true)
    public List<CountryAdminResponse> listCountries() {
        return countries.findAll().stream().sorted(Comparator.comparing(RemittanceCountryEntity::getName))
            .map(this::countryResponse).toList();
    }

    @Transactional
    public CountryAdminResponse saveCountry(CountryRequest request) {
        String code = required(request.code(), "code").toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) throw new IllegalArgumentException("code must be a two-letter country code");
        String name = maxText(request.name(), "name", 100);
        String dialCode = required(request.dialCode(), "dialCode");
        if (!dialCode.matches("\\+[0-9]{1,7}")) throw new IllegalArgumentException("dialCode must look like +233");
        Currency currency = currency(request.currency());
        RemittanceCountryEntity country = countries.findById(code).orElseGet(() ->
            new RemittanceCountryEntity(code, name, dialCode, currency.name(), request.enabled(), request.sendEnabled(), request.receiveEnabled()));
        if (!country.getCurrencyCode().equalsIgnoreCase(currency.name()) && corridors.findAll().stream().anyMatch(c ->
            c.getSourceCountryCode().equalsIgnoreCase(code) || c.getDestinationCountryCode().equalsIgnoreCase(code))) {
            throw new IllegalArgumentException("Country currency cannot change after remittance routes have been configured");
        }
        country.update(name, dialCode, currency.name(), request.enabled(), request.sendEnabled(), request.receiveEnabled());
        return countryResponse(countries.save(country));
    }

    @Transactional
    public NetworkAdminResponse addNetwork(String countryCode, NetworkRequest request) {
        String country = requireCountry(countryCode);
        if (!countries.existsById(country)) throw new IllegalArgumentException("Country is not configured");
        String code = required(request.code(), "code").toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{2,30}")) throw new IllegalArgumentException("code must contain 2 to 30 letters, digits, _ or -");
        if (networks.existsByCountryCodeIgnoreCaseAndCodeIgnoreCase(country, code)) {
            throw new IllegalArgumentException("This network code already exists for the country");
        }
        var network = new MobileMoneyNetworkEntity(UUID.randomUUID(), country, code,
            maxText(request.name(), "name", 100), optionalCode(request.flutterwaveCode()), optionalCode(request.paystackCode()));
        network.update(network.getName(), network.getFlutterwaveCode(), network.getPaystackCode(), request.enabled());
        return networkResponse(networks.save(network));
    }

    @Transactional
    public NetworkAdminResponse updateNetwork(String countryCode, UUID id, NetworkRequest request) {
        String country = requireCountry(countryCode);
        var network = networks.findByIdAndCountryCodeIgnoreCase(id, country)
            .orElseThrow(() -> new IllegalArgumentException("Mobile money network not found"));
        network.update(maxText(request.name(), "name", 100), optionalCode(request.flutterwaveCode()),
            optionalCode(request.paystackCode()), request.enabled());
        return networkResponse(networks.save(network));
    }

    @Transactional(readOnly = true)
    public List<CorridorAdminResponse> listCorridors() {
        return corridors.findAll().stream()
            .sorted(Comparator.comparing(RemittanceCorridorEntity::getSourceCountryCode)
                .thenComparing(RemittanceCorridorEntity::getDestinationCountryCode))
            .map(this::corridorResponse).toList();
    }

    @Transactional
    public CorridorAdminResponse saveCorridor(CorridorRequest request) {
        String source = requireCountry(request.sourceCountryCode());
        String destination = requireCountry(request.destinationCountryCode());
        if (!countries.existsById(source) || !countries.existsById(destination)) {
            throw new IllegalArgumentException("Both countries must be configured before adding a corridor");
        }
        Currency sourceCurrency = currency(request.sourceCurrency());
        Currency destinationCurrency = currency(request.destinationCurrency());
        Currency feeCurrency = currency(request.feeCurrency());
        if (feeCurrency != sourceCurrency) throw new IllegalArgumentException("feeCurrency must match sourceCurrency");
        var sourceCountryConfig = countries.findById(source).orElseThrow();
        var destinationCountryConfig = countries.findById(destination).orElseThrow();
        if (!sourceCurrency.name().equalsIgnoreCase(sourceCountryConfig.getCurrencyCode())) {
            throw new IllegalArgumentException("sourceCurrency must match the source country's configured currency");
        }
        if (!destinationCurrency.name().equalsIgnoreCase(destinationCountryConfig.getCurrencyCode())) {
            throw new IllegalArgumentException("destinationCurrency must match the destination country's configured currency");
        }
        if (request.enabled() && (!sourceCountryConfig.isEnabled() || !sourceCountryConfig.isSendEnabled()
            || !destinationCountryConfig.isEnabled() || !destinationCountryConfig.isReceiveEnabled())) {
            throw new IllegalArgumentException("Enabled routes require an enabled sending country and receiving country");
        }
        if (request.enabled() && networks.findAllByCountryCodeIgnoreCaseAndEnabledTrue(destination).stream()
            .noneMatch(network -> hasText(network.getFlutterwaveCode()) || hasText(network.getPaystackCode()))) {
            throw new IllegalArgumentException("An enabled destination network needs a Flutterwave or Paystack code");
        }
        BigDecimal min = positive(request.minAmount(), "minAmount");
        BigDecimal max = request.maxAmount();
        if (max != null && max.compareTo(min) < 0) throw new IllegalArgumentException("maxAmount must be at least minAmount");
        BigDecimal fixedFee = request.feeFixedAmount() == null ? BigDecimal.ZERO : request.feeFixedAmount();
        if (fixedFee.signum() < 0) throw new IllegalArgumentException("feeFixedAmount cannot be negative");
        if (request.feeBps() < 0 || request.feeBps() > 10_000) throw new IllegalArgumentException("feeBps must be between 0 and 10000");
        BigDecimal fxRate = request.fxRate() == null ? null : positive(request.fxRate(), "fxRate");
        if (request.enabled() && fxRate == null) {
            throw new IllegalArgumentException("fxRate is required before enabling a route");
        }

        RemittanceCorridorEntity corridor = corridors
            .findBySourceCountryCodeIgnoreCaseAndDestinationCountryCodeIgnoreCaseAndSourceCurrencyCodeIgnoreCaseAndDestinationCurrencyCodeIgnoreCase(
                source, destination, sourceCurrency.name(), destinationCurrency.name())
            .orElseGet(() -> new RemittanceCorridorEntity(UUID.randomUUID(), source, destination,
                sourceCurrency.name(), destinationCurrency.name(), request.enabled(), min, max, fixedFee,
                feeCurrency.name(), request.feeBps()));
        corridor.update(request.enabled(), min, max, fixedFee, request.feeBps(), fxRate);
        return corridorResponse(corridors.save(corridor));
    }

    private CountryAdminResponse countryResponse(RemittanceCountryEntity country) {
        List<NetworkAdminResponse> countryNetworks = networks.findAll().stream()
            .filter(n -> n.getCountryCode().equalsIgnoreCase(country.getCode()))
            .sorted(Comparator.comparing(MobileMoneyNetworkEntity::getName)).map(this::networkResponse).toList();
        return new CountryAdminResponse(country.getCode(), country.getName(), country.getDialCode(),
            country.getCurrencyCode(), country.isEnabled(), country.isSendEnabled(), country.isReceiveEnabled(), countryNetworks);
    }

    private NetworkAdminResponse networkResponse(MobileMoneyNetworkEntity network) {
        return new NetworkAdminResponse(network.getId(), network.getCountryCode(), network.getCode(), network.getName(),
            network.getFlutterwaveCode(), network.getPaystackCode(), network.isEnabled());
    }

    private CorridorAdminResponse corridorResponse(RemittanceCorridorEntity c) {
        return new CorridorAdminResponse(c.getId(), c.getSourceCountryCode(), c.getDestinationCountryCode(),
            c.getSourceCurrencyCode(), c.getDestinationCurrencyCode(), c.isEnabled(), c.getMinAmount(),
            c.getMaxAmount(), c.getFeeFixedAmount(), c.getFeeCurrencyCode(), c.getFeeBps(), c.getFxRate());
    }

    private static String requireCountry(String value) { return required(value, "countryCode").toUpperCase(Locale.ROOT); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String maxText(String value, String field, int max) {
        String text = required(value, field);
        if (text.length() > max) throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        return text;
    }
    private static String optionalCode(String value) {
        if (value == null || value.isBlank()) return null;
        String code = value.trim();
        if (code.length() > 50) throw new IllegalArgumentException("provider network code must be 50 characters or fewer");
        return code;
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static Currency currency(String value) {
        try { return Currency.valueOf(required(value, "currency").toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("currency must be supported by RackPay", e); }
    }
    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return value;
    }

    public record CountryRequest(@NotBlank @Size(min=2,max=2) String code, @NotBlank String name,
        @NotBlank String dialCode, @NotBlank String currency, boolean enabled, boolean sendEnabled, boolean receiveEnabled) {}
    public record NetworkRequest(@NotBlank String name, String flutterwaveCode, String paystackCode, boolean enabled,
        @NotBlank @Size(min=2,max=30) String code) {}
    public record CorridorRequest(@NotBlank String sourceCountryCode, @NotBlank String destinationCountryCode,
        @NotBlank String sourceCurrency, @NotBlank String destinationCurrency, @NotNull BigDecimal minAmount,
        BigDecimal maxAmount, BigDecimal feeFixedAmount, @NotBlank String feeCurrency, @Min(0) @Max(10000) int feeBps,
        boolean enabled, BigDecimal fxRate) {}
    public record CountryAdminResponse(String code, String name, String dialCode, String currency, boolean enabled,
        boolean sendEnabled, boolean receiveEnabled, List<NetworkAdminResponse> networks) {}
    public record NetworkAdminResponse(UUID id, String countryCode, String code, String name,
        String flutterwaveCode, String paystackCode, boolean enabled) {}
    public record CorridorAdminResponse(UUID id, String sourceCountryCode, String destinationCountryCode,
        String sourceCurrency, String destinationCurrency, boolean enabled, BigDecimal minAmount,
        BigDecimal maxAmount, BigDecimal feeFixedAmount, String feeCurrency, int feeBps, BigDecimal fxRate) {}
}
