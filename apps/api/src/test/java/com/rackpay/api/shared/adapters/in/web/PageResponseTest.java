package com.rackpay.api.shared.adapters.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {

    @Test
    void convertsSpringPageToStableContract() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        var response = PageResponse.from(page);

        assertEquals(List.of("a", "b"), response.content());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }
}
