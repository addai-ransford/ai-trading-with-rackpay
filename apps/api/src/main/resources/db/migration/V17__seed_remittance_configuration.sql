INSERT INTO remittance_countries (code,name,dial_code,currency_code,enabled,send_enabled,receive_enabled) VALUES
('GH','Ghana','+233','GHS',TRUE,TRUE,TRUE),
('KE','Kenya','+254','KES',TRUE,TRUE,TRUE),
('NG','Nigeria','+234','NGN',TRUE,TRUE,TRUE),
('UG','Uganda','+256','UGX',TRUE,TRUE,TRUE),
('CI','Cote d Ivoire','+225','XOF',TRUE,TRUE,TRUE),
('SN','Senegal','+221','XOF',TRUE,TRUE,TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO mobile_money_networks (id,country_code,code,name) VALUES
('10000000-0000-4000-8000-000000000001','GH','MTN','MTN Mobile Money'),
('10000000-0000-4000-8000-000000000002','GH','AIRTELTIGO','AirtelTigo Money'),
('10000000-0000-4000-8000-000000000003','GH','TELECEL','Telecel Cash'),
('10000000-0000-4000-8000-000000000004','KE','MPESA','M-PESA'),
('10000000-0000-4000-8000-000000000005','CI','ORANGE','Orange Money'),
('10000000-0000-4000-8000-000000000006','CI','WAVE','Wave')
ON CONFLICT (id) DO NOTHING;
