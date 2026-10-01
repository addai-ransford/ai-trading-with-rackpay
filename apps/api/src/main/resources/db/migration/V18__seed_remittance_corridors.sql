INSERT INTO remittance_corridors (
    id,
    source_country_code,
    destination_country_code,
    source_currency_code,
    destination_currency_code,
    enabled,
    min_amount,
    max_amount,
    fee_fixed_amount,
    fee_currency_code,
    fee_bps
)
SELECT
    '20000000-0000-4000-8000-000000000001',
    'BE',
    'GH',
    'EUR',
    'GHS',
    FALSE,
    1,
    10000,
    0,
    'EUR',
    250
WHERE EXISTS (SELECT 1 FROM remittance_countries WHERE code = 'GH')
  AND EXISTS (SELECT 1 FROM remittance_countries WHERE code = 'BE')
ON CONFLICT (source_country_code,destination_country_code,source_currency_code,destination_currency_code) DO NOTHING;

INSERT INTO remittance_corridors (
    id, source_country_code, destination_country_code,
    source_currency_code, destination_currency_code, enabled,
    min_amount, max_amount, fee_fixed_amount, fee_currency_code, fee_bps
)
SELECT
    '20000000-0000-4000-8000-000000000002',
    'BE',
    'KE',
    'EUR',
    'KES',
    FALSE,
    1,
    10000,
    0,
    'EUR',
    250
WHERE EXISTS (SELECT 1 FROM remittance_countries WHERE code = 'KE')
  AND EXISTS (SELECT 1 FROM remittance_countries WHERE code = 'BE')
ON CONFLICT (source_country_code,destination_country_code,source_currency_code,destination_currency_code) DO NOTHING;
