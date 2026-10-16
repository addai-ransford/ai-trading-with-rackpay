package com.rackpay.api.remittance;

import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkEntity;
import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCorridorJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryJpaRepository;
import com.rackpay.api.remittance.core.service.RemittanceAdminConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RemittanceAdminConfigurationServiceTest {

    private final RemittanceCountryJpaRepository countries = mock(RemittanceCountryJpaRepository.class);
    private final MobileMoneyNetworkJpaRepository networks = mock(MobileMoneyNetworkJpaRepository.class);
    private final RemittanceCorridorJpaRepository corridors = mock(RemittanceCorridorJpaRepository.class);
    private final RemittanceAdminConfigurationService service =
        new RemittanceAdminConfigurationService(countries, networks, corridors);

    @Test
    void createsCountryWithNormalizedCodeFromAdminInput() {
        when(countries.findById("UG")).thenReturn(Optional.empty());
        when(countries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.saveCountry(new RemittanceAdminConfigurationService.CountryRequest(
            "ug", "Uganda", "+256", "ugx", true, true, true
        ));

        assertThat(response.code()).isEqualTo("UG");
        assertThat(response.currency()).isEqualTo("UGX");
        assertThat(response.sendEnabled()).isTrue();
        assertThat(response.receiveEnabled()).isTrue();
    }

    @Test
    void storesProviderNetworkCodesEnteredInAdminConfiguration() {
        when(countries.existsById("UG")).thenReturn(true);
        when(networks.existsByCountryCodeIgnoreCaseAndCodeIgnoreCase("UG", "AIRTEL"))
            .thenReturn(false);
        when(networks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.addNetwork("ug", new RemittanceAdminConfigurationService.NetworkRequest(
            "Airtel Money", "AIRTEL", "AIRTEL_UG", true, "AIRTEL"
        ));

        assertThat(response.countryCode()).isEqualTo("UG");
        assertThat(response.code()).isEqualTo("AIRTEL");
        assertThat(response.flutterwaveCode()).isEqualTo("AIRTEL");
        assertThat(response.paystackCode()).isEqualTo("AIRTEL_UG");
        verify(networks).save(any(MobileMoneyNetworkEntity.class));
    }

    @Test
    void requiresPositiveFxRateBeforeAnAdminCanEnableARoute() {
        when(countries.existsById("GH")).thenReturn(true);
        when(countries.existsById("KE")).thenReturn(true);
        when(countries.findById("GH")).thenReturn(Optional.of(new RemittanceCountryEntity(
            "GH", "Ghana", "+233", "GHS", true, true, true)));
        when(countries.findById("KE")).thenReturn(Optional.of(new RemittanceCountryEntity(
            "KE", "Kenya", "+254", "KES", true, true, true)));
        when(networks.findAllByCountryCodeIgnoreCaseAndEnabledTrue("KE")).thenReturn(java.util.List.of(
            new MobileMoneyNetworkEntity(java.util.UUID.randomUUID(), "KE", "MPESA", "M-PESA", "MPS", null)));

        var request = new RemittanceAdminConfigurationService.CorridorRequest(
            "GH", "KE", "GHS", "KES", new BigDecimal("1"), null, BigDecimal.ZERO,
            "GHS", 250, true, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveCorridor(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("fxRate is required before enabling a route");
        verify(corridors, never()).save(any());
    }
}
