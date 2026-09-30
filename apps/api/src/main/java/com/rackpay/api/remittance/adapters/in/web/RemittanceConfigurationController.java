package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.core.service.RemittanceConfigurationService;
import com.rackpay.api.remittance.core.service.RemittanceConfigurationService.Direction;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/remittances/config")
public class RemittanceConfigurationController {
    private final RemittanceConfigurationService configuration;

    public RemittanceConfigurationController(RemittanceConfigurationService configuration) {
        this.configuration = configuration;
    }

    @GetMapping("/countries")
    public List<RemittanceConfigurationService.CountryResponse> countries(
        Authentication authentication,
        @RequestParam(defaultValue = "RECEIVE") Direction direction
    ) {
        return configuration.countries(direction);
    }

    @GetMapping("/countries/{countryCode}/networks")
    public List<RemittanceConfigurationService.NetworkResponse> networks(
        Authentication authentication,
        @PathVariable String countryCode
    ) {
        return configuration.networks(countryCode);
    }
}
