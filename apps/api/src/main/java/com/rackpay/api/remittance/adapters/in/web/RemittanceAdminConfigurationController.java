package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.core.service.RemittanceAdminConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/remittance")
public class RemittanceAdminConfigurationController {
    private final RemittanceAdminConfigurationService service;

    public RemittanceAdminConfigurationController(RemittanceAdminConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/countries")
    public List<RemittanceAdminConfigurationService.CountryAdminResponse> countries() {
        return service.listCountries();
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public RemittanceAdminConfigurationService.CountryAdminResponse saveCountry(
        @Valid @RequestBody RemittanceAdminConfigurationService.CountryRequest request) {
        return service.saveCountry(request);
    }

    @PostMapping("/countries/{countryCode}/networks")
    @ResponseStatus(HttpStatus.CREATED)
    public RemittanceAdminConfigurationService.NetworkAdminResponse addNetwork(
        @PathVariable String countryCode,
        @Valid @RequestBody RemittanceAdminConfigurationService.NetworkRequest request) {
        return service.addNetwork(countryCode, request);
    }

    @PutMapping("/countries/{countryCode}/networks/{networkId}")
    public RemittanceAdminConfigurationService.NetworkAdminResponse updateNetwork(
        @PathVariable String countryCode, @PathVariable UUID networkId,
        @Valid @RequestBody RemittanceAdminConfigurationService.NetworkRequest request) {
        return service.updateNetwork(countryCode, networkId, request);
    }

    @GetMapping("/corridors")
    public List<RemittanceAdminConfigurationService.CorridorAdminResponse> corridors() {
        return service.listCorridors();
    }

    @PostMapping("/corridors")
    @ResponseStatus(HttpStatus.CREATED)
    public RemittanceAdminConfigurationService.CorridorAdminResponse saveCorridor(
        @Valid @RequestBody RemittanceAdminConfigurationService.CorridorRequest request) {
        return service.saveCorridor(request);
    }
}
