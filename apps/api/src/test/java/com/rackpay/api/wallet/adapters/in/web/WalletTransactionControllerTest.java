package com.rackpay.api.wallet.adapters.in.web;

import com.rackpay.api.shared.adapters.in.web.ApiExceptionHandler;
import com.rackpay.api.transaction.adapters.out.persistence.FinancialTransactionJpaRepository;
import com.rackpay.api.user.adapters.out.persistence.CurrentUserService;
import com.rackpay.api.wallet.adapters.out.persistence.WalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletTransactionControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new WalletTransactionController(
            mock(CurrentUserService.class),
            mock(WalletJpaRepository.class),
            mock(FinancialTransactionJpaRepository.class)
        );

        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void rejectsNegativePageWithStableErrorContract() throws Exception {
        mvc.perform(get("/api/v1/wallet/transactions").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"))
            .andExpect(jsonPath("$.message").value("page must be zero or greater"))
            .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsOversizedPageWithStableErrorContract() throws Exception {
        mvc.perform(get("/api/v1/wallet/transactions").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"))
            .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }
}
