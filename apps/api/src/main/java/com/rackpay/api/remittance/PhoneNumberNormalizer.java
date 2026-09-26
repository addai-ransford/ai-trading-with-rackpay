package com.rackpay.api.remittance;

import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {
    public String normalize(String countryDialCode, String localPhoneNumber) {
        if (countryDialCode == null || countryDialCode.isBlank()) throw new IllegalArgumentException("country dial code is required");
        if (localPhoneNumber == null || localPhoneNumber.isBlank()) throw new IllegalArgumentException("phone number is required");
        String dial = countryDialCode.trim().replaceAll("[^0-9]", "");
        String digits = localPhoneNumber.trim().replaceAll("[^0-9]", "");
        if (dial.isBlank() || digits.isBlank()) throw new IllegalArgumentException("phone number is invalid");
        if (digits.startsWith("0")) digits = digits.replaceFirst("^0+", "");
        if (digits.isBlank()) throw new IllegalArgumentException("phone number is invalid");
        return "+" + dial + digits;
    }
}
