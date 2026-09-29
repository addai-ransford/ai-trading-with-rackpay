package com.rackpay.api.remittance.core.service;

import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCorridorEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCorridorJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceQuoteEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceQuoteJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientJpaRepository;
import com.rackpay.api.remittance.ports.out.FxRateProvider;
import com.rackpay.api.shared.core.service.must;

import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.remittance.adapters.out.persistence.*;
import com.rackpay.api.user.adapters.out.persistence.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class RemittanceQuoteService {
    private final CurrentUserService currentUser;
    private final RemittanceRecipientJpaRepository recipients;
    private final RemittanceCorridorJpaRepository corridors;
    private final RemittanceQuoteJpaRepository quotes;
    private final FxRateProvider fxRates;
    private final Duration quoteLifetime;

    public RemittanceQuoteService(CurrentUserService currentUser, RemittanceRecipientJpaRepository recipients,
        RemittanceCorridorJpaRepository corridors, RemittanceQuoteJpaRepository quotes, FxRateProvider fxRates,
        @Value("${rackpay.remittance.quote-lifetime:PT2M}") Duration quoteLifetime) {
        this.currentUser=currentUser; this.recipients=recipients; this.corridors=corridors;
        this.quotes=quotes; this.fxRates=fxRates; this.quoteLifetime=quoteLifetime;
    }

    @Transactional
    public QuoteResponse createQuote(Authentication authentication, QuoteRequest request) {
        var user=currentUser.requireUser(authentication);
        if(request==null||request.recipientId()==null) throw new IllegalArgumentException("recipientId is required");
        if(request.sourceAmount()==null||request.sourceAmount().signum()<=0) throw new IllegalArgumentException("sourceAmount must be greater than zero");

        Currency sourceCurrency=requireCurrency(request.sourceCurrency());
        String sourceCountry=requireText(request.sourceCountryCode(),"sourceCountryCode").toUpperCase(Locale.ROOT);

        RemittanceRecipientEntity recipient=recipients.findById(request.recipientId())
            .filter(r->r.getUserId().equals(user.getId())&&r.isActive())
            .orElseThrow(()->new IllegalArgumentException("Recipient does not belong to the authenticated user"));

        String destinationCountry=recipient.getCountryCode().toUpperCase(Locale.ROOT);
        RemittanceCorridorEntity corridor;
        if (request.destinationCurrency() != null) {
            corridor = corridors
                .findBySourceCountryCodeIgnoreCaseAndDestinationCountryCodeIgnoreCaseAndSourceCurrencyCodeIgnoreCaseAndDestinationCurrencyCodeIgnoreCaseAndEnabledTrue(
                    sourceCountry,
                    destinationCountry,
                    sourceCurrency.name(),
                    request.destinationCurrency().name()
                )
                .orElseThrow(() -> new IllegalArgumentException("No enabled remittance corridor exists for this route"));
        } else {
            corridor = corridors
                .findAllBySourceCountryCodeIgnoreCaseAndDestinationCountryCodeIgnoreCaseAndSourceCurrencyCodeIgnoreCaseAndEnabledTrue(
                    sourceCountry,
                    destinationCountry,
                    sourceCurrency.name()
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enabled remittance corridor exists for this route"));
        }

        Currency destinationCurrency=parseCurrency(corridor.getDestinationCurrencyCode());
        if(request.sourceAmount().compareTo(corridor.getMinAmount())<0) throw new IllegalArgumentException("Amount is below the corridor minimum");
        if(corridor.getMaxAmount()!=null&&request.sourceAmount().compareTo(corridor.getMaxAmount())>0) throw new IllegalArgumentException("Amount exceeds the corridor maximum");

        Currency feeCurrency=parseCurrency(corridor.getFeeCurrencyCode());
        if(feeCurrency!=sourceCurrency) throw new IllegalStateException("Corridor fee currency must match source currency");

        int minorUnits = sourceCurrency.minorUnits();
        if (request.sourceAmount().scale() > minorUnits
            && request.sourceAmount().stripTrailingZeros().scale() > minorUnits) {
            throw new IllegalArgumentException(
                "sourceAmount has more decimal places than " + sourceCurrency.name() + " supports"
            );
        }
        BigDecimal sourceAmount=request.sourceAmount().setScale(minorUnits,RoundingMode.UNNECESSARY);
        BigDecimal fee=corridor.getFeeFixedAmount()
            .add(sourceAmount.multiply(BigDecimal.valueOf(corridor.getFeeBps()))
            .divide(BigDecimal.valueOf(10000),18,RoundingMode.HALF_EVEN))
            .setScale(sourceCurrency.minorUnits(),RoundingMode.HALF_EVEN);

        BigDecimal rate=fxRates.getRate(sourceCurrency,destinationCurrency);
        if(rate==null||rate.signum()<=0) throw new IllegalStateException("FX provider returned an invalid rate");

        BigDecimal destinationAmount=sourceAmount.multiply(rate)
            .setScale(destinationCurrency.minorUnits(),RoundingMode.HALF_EVEN);
        if(destinationAmount.signum()<=0) throw new IllegalStateException("Calculated destination amount is invalid");

        Instant now=Instant.now();
        RemittanceQuoteEntity quote=new RemittanceQuoteEntity(
            UUID.randomUUID(),user.getId(),corridor.getId(),recipient.getId(),sourceAmount,sourceCurrency,
            destinationAmount,destinationCurrency,fee,feeCurrency,rate,now.plus(quoteLifetime),now);
        quotes.save(quote);

        return new QuoteResponse(quote.getId(),recipient.getId(),sourceCountry,destinationCountry,
            sourceCurrency,sourceAmount,destinationCurrency,destinationAmount,fee,rate,quote.getExpiresAt());
    }

    private static Currency parseCurrency(String value) {
        try{return Currency.valueOf(value.toUpperCase(Locale.ROOT));}
        catch(Exception e){throw new IllegalStateException("Unsupported configured currency: "+value,e);}
    }
    private static Currency requireCurrency(Currency value){if(value==null)throw new IllegalArgumentException("sourceCurrency is required");return value;}
    private static String requireText(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}

    public record QuoteRequest(UUID recipientId,String sourceCountryCode,Currency sourceCurrency,Currency destinationCurrency,BigDecimal sourceAmount){}
    public record QuoteResponse(UUID quoteId,UUID recipientId,String sourceCountryCode,String destinationCountryCode,
        Currency sourceCurrency,BigDecimal sourceAmount,Currency destinationCurrency,BigDecimal destinationAmount,
        BigDecimal feeAmount,BigDecimal fxRate,Instant expiresAt){}
}
