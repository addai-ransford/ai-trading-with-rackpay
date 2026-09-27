package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.remittance.*;
import com.rackpay.api.persistence.user.CurrentUserService;
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
        RemittanceCorridorEntity corridor=corridors.findAll().stream()
            .filter(RemittanceCorridorEntity::isEnabled)
            .filter(c->c.getSourceCountryCode().equalsIgnoreCase(sourceCountry))
            .filter(c->c.getDestinationCountryCode().equalsIgnoreCase(destinationCountry))
            .filter(c->c.getSourceCurrencyCode().equalsIgnoreCase(sourceCurrency.name()))
            .filter(c->request.destinationCurrency()==null||c.getDestinationCurrencyCode().equalsIgnoreCase(request.destinationCurrency().name()))
            .findFirst()
            .orElseThrow(()->new IllegalArgumentException("No enabled remittance corridor exists for this route"));

        Currency destinationCurrency=parseCurrency(corridor.getDestinationCurrencyCode());
        if(request.sourceAmount().compareTo(corridor.getMinAmount())<0) throw new IllegalArgumentException("Amount is below the corridor minimum");
        if(corridor.getMaxAmount()!=null&&request.sourceAmount().compareTo(corridor.getMaxAmount())>0) throw new IllegalArgumentException("Amount exceeds the corridor maximum");

        Currency feeCurrency=parseCurrency(corridor.getFeeCurrencyCode());
        if(feeCurrency!=sourceCurrency) throw new IllegalStateException("Corridor fee currency must match source currency");

        BigDecimal sourceAmount=request.sourceAmount().setScale(sourceCurrency.minorUnits(),RoundingMode.HALF_EVEN);
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
