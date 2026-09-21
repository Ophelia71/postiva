
CREATE EXTENSION IF NOT EXISTS pgcrypto;


DROP TABLE IF EXISTS ai_generation_logs CASCADE;
DROP TABLE IF EXISTS publish_logs CASCADE;
DROP TABLE IF EXISTS scheduled_posts CASCADE;
DROP TABLE IF EXISTS social_accounts CASCADE;
DROP TABLE IF EXISTS uploaded_files CASCADE;
DROP TABLE IF EXISTS calendar_items CASCADE;
DROP TABLE IF EXISTS content_calendars CASCADE;
DROP TABLE IF EXISTS post_scores CASCADE;
DROP TABLE IF EXISTS generated_posts CASCADE;
DROP TABLE IF EXISTS product_briefs CASCADE;
DROP TABLE IF EXISTS industry_templates CASCADE;
DROP TABLE IF EXISTS business_profiles CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS pending_registrations CASCADE;


-- 1. USERS

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       full_name VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash TEXT NOT NULL,
                       role VARCHAR(50) NOT NULL DEFAULT 'USER',
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT chk_users_role
                           CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE pending_registrations (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       full_name VARCHAR(150) NOT NULL,
                                       email VARCHAR(255) NOT NULL UNIQUE,
                                       password_hash TEXT NOT NULL,
                                       otp_hash TEXT NOT NULL,
                                       otp_expires_at TIMESTAMP NOT NULL,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 2. BUSINESS PROFILES
-- Store small business/shop profile of each user.
-- =========================================================

CREATE TABLE business_profiles (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   user_id UUID NOT NULL,
                                   business_name VARCHAR(255) NOT NULL,
                                   industry_code VARCHAR(100),
                                   target_customer TEXT,
                                   default_tone VARCHAR(100),
                                   description TEXT,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_business_profiles_user
                                       FOREIGN KEY (user_id)
                                           REFERENCES users(id)
                                           ON DELETE CASCADE,
                                   CONSTRAINT uq_business_profiles_user_id
                                       UNIQUE (user_id)
);

-- =========================================================
-- 3. INDUSTRY TEMPLATES
-- Used by feature 3: templates by industry.
-- JSONB keeps templates flexible for each business type.
-- =========================================================

CREATE TABLE industry_templates (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    industry_code VARCHAR(100) NOT NULL UNIQUE,
                                    industry_name VARCHAR(255) NOT NULL,
                                    post_types JSONB NOT NULL DEFAULT '[]'::jsonb,
                                    cta_examples JSONB NOT NULL DEFAULT '[]'::jsonb,
                                    hashtag_examples JSONB NOT NULL DEFAULT '[]'::jsonb,
                                    content_angles JSONB NOT NULL DEFAULT '[]'::jsonb,
                                    tone_suggestions JSONB NOT NULL DEFAULT '[]'::jsonb,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 4. PRODUCT BRIEFS
-- Used by feature 1: user input for AI post generation.
-- Price is VARCHAR because real marketing prices can be "from 99k",
-- "combo 149k", or "contact us".
-- =========================================================

CREATE TABLE product_briefs (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id UUID,
                                business_id UUID,
                                product_name VARCHAR(255) NOT NULL,
                                price VARCHAR(100),
                                industry_code VARCHAR(100),
                                target_audience TEXT,
                                promotion TEXT,
                                goal VARCHAR(255),
                                tone VARCHAR(100),
                                platforms JSONB NOT NULL DEFAULT '[]'::jsonb,
                                additional_info TEXT,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_product_briefs_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE SET NULL,

                                CONSTRAINT fk_product_briefs_business
                                    FOREIGN KEY (business_id)
                                        REFERENCES business_profiles(id)
                                        ON DELETE SET NULL
);

-- =========================================================
-- 5. GENERATED POSTS
-- Used by feature 1 and feature 4.
-- platform_contents stores Facebook, Instagram, Threads outputs.
-- =========================================================

CREATE TABLE generated_posts (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 brief_id UUID NOT NULL,
                                 user_id UUID,
                                 platform_contents JSONB NOT NULL DEFAULT '{}'::jsonb,
                                 hashtags JSONB NOT NULL DEFAULT '[]'::jsonb,
                                 cta TEXT,
                                 image_suggestion TEXT,
                                 image_url TEXT,
                                 status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_generated_posts_brief
                                     FOREIGN KEY (brief_id)
                                         REFERENCES product_briefs(id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_generated_posts_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES users(id)
                                         ON DELETE SET NULL,

                                 CONSTRAINT chk_generated_posts_status
                                     CHECK (status IN ('DRAFT', 'PLANNED', 'SCHEDULED', 'PUBLISHED', 'FAILED'))
);

-- =========================================================
-- 6. POST SCORES
-- Used by feature 2: rule-based marketing score.
-- =========================================================

CREATE TABLE post_scores (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             post_id UUID NOT NULL,
                             score NUMERIC(5,2) NOT NULL,
                             strengths JSONB NOT NULL DEFAULT '[]'::jsonb,
                             weaknesses JSONB NOT NULL DEFAULT '[]'::jsonb,
                             suggestions JSONB NOT NULL DEFAULT '[]'::jsonb,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_post_scores_post
                                 FOREIGN KEY (post_id)
                                     REFERENCES generated_posts(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT chk_post_scores_score
                                 CHECK (score >= 0 AND score <= 100)
);

-- =========================================================
-- 7. CONTENT CALENDARS
-- Used by feature 4: 7-day content calendar.
-- =========================================================

CREATE TABLE content_calendars (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   user_id UUID,
                                   business_id UUID,
                                   industry_code VARCHAR(100),
                                   goal VARCHAR(255),
                                   start_date DATE NOT NULL,
                                   end_date DATE NOT NULL,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_content_calendars_user
                                       FOREIGN KEY (user_id)
                                           REFERENCES users(id)
                                           ON DELETE SET NULL,

                                   CONSTRAINT fk_content_calendars_business
                                       FOREIGN KEY (business_id)
                                           REFERENCES business_profiles(id)
                                           ON DELETE SET NULL,

                                   CONSTRAINT chk_content_calendars_dates
                                       CHECK (end_date >= start_date)
);

-- =========================================================
-- 8. CALENDAR ITEMS
-- Stores each content idea in a 7-day calendar.
-- =========================================================

CREATE TABLE calendar_items (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                calendar_id UUID NOT NULL,
                                generated_post_id UUID,
                                planned_date DATE NOT NULL,
                                platform VARCHAR(50) NOT NULL,
                                post_type VARCHAR(100),
                                topic TEXT,
                                status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_calendar_items_calendar
                                    FOREIGN KEY (calendar_id)
                                        REFERENCES content_calendars(id)
                                        ON DELETE CASCADE,

                                CONSTRAINT fk_calendar_items_generated_post
                                    FOREIGN KEY (generated_post_id)
                                        REFERENCES generated_posts(id)
                                        ON DELETE SET NULL,

                                CONSTRAINT chk_calendar_items_platform
                                    CHECK (platform IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),

                                CONSTRAINT chk_calendar_items_status
                                    CHECK (status IN ('PLANNED', 'DRAFT', 'SCHEDULED', 'PUBLISHED', 'FAILED', 'CANCELLED'))
);

-- =========================================================
-- 9. UPLOADED FILES
-- Stores product images uploaded by users.
-- In MVP, images are only attached/previewed, not analyzed by AI.
-- =========================================================

CREATE TABLE uploaded_files (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id UUID,
                                brief_id UUID,
                                post_id UUID,
                                file_url TEXT NOT NULL,
                                file_name VARCHAR(255),
                                mime_type VARCHAR(100),
                                file_size BIGINT,
                                public_id VARCHAR(255),
                                storage_provider VARCHAR(100),
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_uploaded_files_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE SET NULL,

                                CONSTRAINT fk_uploaded_files_brief
                                    FOREIGN KEY (brief_id)
                                        REFERENCES product_briefs(id)
                                        ON DELETE SET NULL,

                                CONSTRAINT fk_uploaded_files_post
                                    FOREIGN KEY (post_id)
                                        REFERENCES generated_posts(id)
                                        ON DELETE SET NULL
);

-- =========================================================
-- 10. SOCIAL ACCOUNTS
-- Used by feature 5: Meta connection.
-- Stores connected Facebook Page, Instagram, or Threads account.
-- Token must be encrypted before saving.
-- =========================================================

CREATE TABLE social_accounts (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 user_id UUID NOT NULL,
                                 provider VARCHAR(50) NOT NULL,
                                 provider_account_id VARCHAR(255),
                                 page_id VARCHAR(255),
                                 page_name VARCHAR(255),
                                 access_token_encrypted TEXT,
                                 token_expires_at TIMESTAMP,
                                 scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
                                 account_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                                 connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 disconnected_at TIMESTAMP,

                                 CONSTRAINT fk_social_accounts_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES users(id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT chk_social_accounts_provider
                                     CHECK (provider IN ('FACEBOOK', 'INSTAGRAM', 'THREADS'))
);

CREATE UNIQUE INDEX uq_social_accounts_user_provider_page
    ON social_accounts(user_id, provider, COALESCE(page_id, provider_account_id));

-- =========================================================
-- 11. SCHEDULED POSTS
-- Used by feature 4 and feature 5.
-- Can work as internal schedule first, then publish through Meta later.
-- =========================================================

CREATE TABLE scheduled_posts (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 post_id UUID NOT NULL,
                                 social_account_id UUID,
                                 platform VARCHAR(50) NOT NULL,
                                 scheduled_time TIMESTAMP NOT NULL,
                                 status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
                                 attempt_count INT NOT NULL DEFAULT 0,
                                 last_error TEXT,
                                 published_at TIMESTAMP,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_scheduled_posts_post
                                     FOREIGN KEY (post_id)
                                         REFERENCES generated_posts(id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_scheduled_posts_social_account
                                     FOREIGN KEY (social_account_id)
                                         REFERENCES social_accounts(id)
                                         ON DELETE SET NULL,

                                 CONSTRAINT chk_scheduled_posts_platform
                                     CHECK (platform IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),

                                 CONSTRAINT chk_scheduled_posts_status
                                     CHECK (status IN ('SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'CANCELLED'))
);

-- =========================================================
-- 12. PUBLISH LOGS
-- Stores success/failure logs when publishing to Meta.
-- =========================================================

CREATE TABLE publish_logs (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              scheduled_post_id UUID NOT NULL,
                              provider VARCHAR(50),
                              external_post_id VARCHAR(255),
                              status VARCHAR(50) NOT NULL,
                              error_message TEXT,
                              published_at TIMESTAMP,

                              CONSTRAINT fk_publish_logs_scheduled_post
                                  FOREIGN KEY (scheduled_post_id)
                                      REFERENCES scheduled_posts(id)
                                      ON DELETE CASCADE
);

-- =========================================================
-- 13. AI GENERATION LOGS
-- Stores OpenAI calls for debugging, cost tracking, and audit.
-- =========================================================

CREATE TABLE ai_generation_logs (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id UUID,
                                    brief_id UUID,
                                    post_id UUID,
                                    provider VARCHAR(50) NOT NULL DEFAULT 'OPENAI',
                                    model VARCHAR(100),
                                    prompt TEXT,
                                    response JSONB,
                                    input_tokens INT,
                                    output_tokens INT,
                                    total_tokens INT,
                                    status VARCHAR(50) NOT NULL,
                                    error_message TEXT,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_ai_generation_logs_user
                                        FOREIGN KEY (user_id)
                                            REFERENCES users(id)
                                            ON DELETE SET NULL,

                                    CONSTRAINT fk_ai_generation_logs_brief
                                        FOREIGN KEY (brief_id)
                                            REFERENCES product_briefs(id)
                                            ON DELETE SET NULL,

                                    CONSTRAINT fk_ai_generation_logs_post
                                        FOREIGN KEY (post_id)
                                            REFERENCES generated_posts(id)
                                            ON DELETE SET NULL,

                                    CONSTRAINT chk_ai_generation_logs_status
                                        CHECK (status IN ('SUCCESS', 'FAILED'))
);

-- =========================================================
-- UPDATED_AT TRIGGER
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_pending_registrations_updated_at
    BEFORE UPDATE ON pending_registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_business_profiles_updated_at
    BEFORE UPDATE ON business_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_industry_templates_updated_at
    BEFORE UPDATE ON industry_templates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_generated_posts_updated_at
    BEFORE UPDATE ON generated_posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_calendar_items_updated_at
    BEFORE UPDATE ON calendar_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_scheduled_posts_updated_at
    BEFORE UPDATE ON scheduled_posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- INDEXES
-- =========================================================

CREATE INDEX idx_product_briefs_user_id
    ON product_briefs(user_id);

CREATE INDEX idx_product_briefs_business_id
    ON product_briefs(business_id);

CREATE INDEX idx_generated_posts_brief_id
    ON generated_posts(brief_id);

CREATE INDEX idx_generated_posts_user_id
    ON generated_posts(user_id);

CREATE INDEX idx_generated_posts_status
    ON generated_posts(status);

CREATE INDEX idx_post_scores_post_id
    ON post_scores(post_id);

CREATE INDEX idx_content_calendars_user_id
    ON content_calendars(user_id);

CREATE INDEX idx_content_calendars_business_id
    ON content_calendars(business_id);

CREATE INDEX idx_calendar_items_calendar_id
    ON calendar_items(calendar_id);

CREATE INDEX idx_calendar_items_planned_date
    ON calendar_items(planned_date);

CREATE INDEX idx_uploaded_files_user_id
    ON uploaded_files(user_id);

CREATE INDEX idx_uploaded_files_brief_id
    ON uploaded_files(brief_id);

CREATE INDEX idx_uploaded_files_post_id
    ON uploaded_files(post_id);

CREATE INDEX idx_social_accounts_user_id
    ON social_accounts(user_id);

CREATE INDEX idx_scheduled_posts_post_id
    ON scheduled_posts(post_id);

CREATE INDEX idx_scheduled_posts_social_account_id
    ON scheduled_posts(social_account_id);

CREATE INDEX idx_scheduled_posts_due
    ON scheduled_posts(status, scheduled_time);

CREATE INDEX idx_publish_logs_scheduled_post_id
    ON publish_logs(scheduled_post_id);

CREATE INDEX idx_ai_generation_logs_user_id
    ON ai_generation_logs(user_id);

CREATE INDEX idx_ai_generation_logs_brief_id
    ON ai_generation_logs(brief_id);

CREATE INDEX idx_ai_generation_logs_post_id
    ON ai_generation_logs(post_id);

-- =========================================================
-- SEED DATA
-- =========================================================

INSERT INTO industry_templates (
    industry_code,
    industry_name,
    post_types,
    cta_examples,
    hashtag_examples,
    content_angles,
    tone_suggestions
) VALUES
      (
          'cafe',
          'Café / Đồ uống',
          '["Giới thiệu món mới", "Ưu đãi khung giờ vàng", "Feedback khách hàng", "Hậu trường pha chế", "Combo đồ uống"]'::jsonb,
          '["Ghé quán hôm nay", "Nhắn tin để đặt trước", "Đặt ngay để giữ ưu đãi"]'::jsonb,
          '["#cafe", "#douong", "#tradao", "#uudai"]'::jsonb,
          '["giá tốt", "vị ngon", "khung giờ vàng", "địa điểm gần khách hàng"]'::jsonb,
          '["trẻ trung", "thân thiện", "gần gũi"]'::jsonb
      ),
      (
          'fashion',
          'Thời trang',
          '["Hàng mới về", "Gợi ý phối đồ", "Sale cuối tuần", "Feedback khách hàng", "Sản phẩm bán chạy"]'::jsonb,
          '["Nhắn tin để được tư vấn size", "Đặt hàng hôm nay", "Inbox shop để giữ mẫu"]'::jsonb,
          '["#thoitrang", "#outfit", "#sale", "#hangmoive"]'::jsonb,
          '["phong cách", "chất liệu", "ưu đãi", "số lượng có hạn"]'::jsonb,
          '["năng động", "sang trọng", "tự tin"]'::jsonb
      ),
      (
          'beauty',
          'Mỹ phẩm / Làm đẹp',
          '["Công dụng sản phẩm", "Cách sử dụng", "Review khách hàng", "Ưu đãi combo", "Before after"]'::jsonb,
          '["Nhắn tin để được tư vấn", "Đặt lịch tư vấn ngay", "Inbox để nhận ưu đãi"]'::jsonb,
          '["#mypham", "#lamdep", "#skincare", "#beauty"]'::jsonb,
          '["hiệu quả", "thành phần", "cách dùng", "feedback"]'::jsonb,
          '["tin cậy", "chuyên nghiệp", "nhẹ nhàng"]'::jsonb
      ),
      (
          'education',
          'Khóa học / Giáo dục',
          '["Lợi ích khóa học", "Lịch khai giảng", "Feedback học viên", "Ưu đãi đăng ký sớm", "Giới thiệu giảng viên"]'::jsonb,
          '["Đăng ký tư vấn miễn phí", "Nhắn tin để nhận lộ trình", "Giữ chỗ hôm nay"]'::jsonb,
          '["#khoahoc", "#hoctap", "#kynang", "#giaoduc"]'::jsonb,
          '["kết quả học tập", "lộ trình", "giảng viên", "ưu đãi"]'::jsonb,
          '["rõ ràng", "truyền cảm hứng", "chuyên nghiệp"]'::jsonb
      );

-- NOTE ABOUT TABLE OWNERSHIP
-- Spring/Hibernate can only ALTER existing tables when the configured
-- POSTIVA_DB_USERNAME owns those tables, or has enough privileges.
-- If you see: "must be owner of table ...", connect as a PostgreSQL superuser
-- and transfer ownership to the same role used by the backend, for example:
--
--   REASSIGN OWNED BY old_owner TO postgres;
--
-- Or recreate the database/schema while connected as POSTIVA_DB_USERNAME.
