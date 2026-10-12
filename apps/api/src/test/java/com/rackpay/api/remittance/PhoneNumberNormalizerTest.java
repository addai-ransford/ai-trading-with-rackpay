package com.rackpay.api.remittance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.rackpay.api.remittance.core.service.PhoneNumberNormalizer;

class PhoneNumberNormalizerTest {

    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();

    @Test
    void normalizesGhanaLocalNumberWithOptionalTrunkPrefix() {
        assertEquals("+233241234567", normalizer.normalize("+233", "024 123 4567"));
        assertEquals("+233241234567", normalizer.normalize("+233", "241234567"));
    }

    @Test
    void rejectsGhanaNumberWithTooManySubscriberDigits() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalize("+233", "22123456789")
        );

        assertEquals("Ghana mobile numbers must contain 9 digits after +233", exception.getMessage());
    }
}
