package com.rackpay.api.persistence.remittance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface RemittanceQuoteJpaRepository extends JpaRepository<RemittanceQuoteEntity,UUID>{}
