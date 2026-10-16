ALTER TABLE mobile_money_networks
    ADD COLUMN flutterwave_code VARCHAR(50),
    ADD COLUMN paystack_code VARCHAR(50);

UPDATE mobile_money_networks
SET flutterwave_code = CASE code
        WHEN 'MTN' THEN 'MTN'
        WHEN 'AIRTELTIGO' THEN 'AIRTELTIGO'
        WHEN 'TELECEL' THEN 'VODAFONE'
        WHEN 'MPESA' THEN 'MPS'
        WHEN 'ORANGE' THEN 'ORANGE'
        WHEN 'WAVE' THEN 'WAVE'
        ELSE NULL
    END,
    paystack_code = CASE code
        WHEN 'MTN' THEN 'MTN'
        WHEN 'AIRTELTIGO' THEN 'ATL'
        WHEN 'TELECEL' THEN 'VOD'
        WHEN 'MPESA' THEN 'MPESA'
        ELSE NULL
    END;
