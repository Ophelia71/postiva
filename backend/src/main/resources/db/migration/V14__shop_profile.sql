-- Shop profile ("Hồ sơ shop"): set once after sign-up, editable later, and reused by every AI draft.
-- Extends the existing business_profiles table; rows created before this keep their business_name and
-- simply count as incomplete until the shop fills in the required fields.

ALTER TABLE business_profiles
    -- Free-text industry when the shop picks "Ngành khác" instead of one of industry_templates.
    ADD COLUMN industry_name VARCHAR(100),
    ADD COLUMN main_products TEXT,
    ADD COLUMN highlights TEXT,
    ADD COLUMN price_range VARCHAR(200),
    ADD COLUMN phone VARCHAR(50),
    ADD COLUMN address TEXT,
    ADD COLUMN opening_hours VARCHAR(200),
    ADD COLUMN website VARCHAR(500),
    ADD COLUMN policies TEXT,
    -- Appended to generated posts as written, so contact details are exact rather than AI-retyped.
    ADD COLUMN signature TEXT,
    ADD COLUMN brand_hashtags VARCHAR(300);
