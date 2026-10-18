-- Initial schema for EMPTY databases only. Existing databases must be inspected
-- and explicitly baselined at version 1; this script is then skipped.
-- UUID generation uses PostgreSQL's built-in gen_random_uuid() (PostgreSQL 13+).

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pending_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    otp_expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE business_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(255) NOT NULL,
    industry_code VARCHAR(100),
    target_customer TEXT,
    default_tone VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_business_profiles_user_id UNIQUE (user_id)
);

CREATE TABLE industry_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    industry_code VARCHAR(255) NOT NULL UNIQUE,
    industry_name VARCHAR(255) NOT NULL,
    post_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    cta_examples JSONB NOT NULL DEFAULT '[]'::jsonb,
    hashtag_examples JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_angles JSONB NOT NULL DEFAULT '[]'::jsonb,
    tone_suggestions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_briefs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    business_id UUID REFERENCES business_profiles(id) ON DELETE SET NULL,
    product_name VARCHAR(255) NOT NULL,
    price VARCHAR(255),
    industry_code VARCHAR(255),
    target_audience TEXT,
    promotion TEXT,
    goal VARCHAR(255),
    tone VARCHAR(255),
    platforms JSONB NOT NULL DEFAULT '[]'::jsonb,
    additional_info TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE generated_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brief_id UUID NOT NULL REFERENCES product_briefs(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    platform_contents JSONB NOT NULL DEFAULT '{}'::jsonb,
    hashtags JSONB NOT NULL DEFAULT '[]'::jsonb,
    cta TEXT,
    image_suggestion TEXT,
    image_url TEXT,
    status VARCHAR(255) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PLANNED', 'SCHEDULED', 'PUBLISHED', 'FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE post_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES generated_posts(id) ON DELETE CASCADE,
    score INTEGER NOT NULL CHECK (score >= 0 AND score <= 100),
    strengths JSONB NOT NULL DEFAULT '[]'::jsonb,
    weaknesses JSONB NOT NULL DEFAULT '[]'::jsonb,
    suggestions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_calendars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    business_id UUID REFERENCES business_profiles(id) ON DELETE SET NULL,
    industry_code VARCHAR(255),
    goal VARCHAR(255),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (end_date >= start_date)
);

CREATE TABLE calendar_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id UUID NOT NULL REFERENCES content_calendars(id) ON DELETE CASCADE,
    generated_post_id UUID REFERENCES generated_posts(id) ON DELETE SET NULL,
    planned_date DATE NOT NULL,
    platform VARCHAR(255) NOT NULL CHECK (platform IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),
    post_type VARCHAR(255),
    topic TEXT,
    status VARCHAR(255) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('DRAFT', 'PLANNED', 'SCHEDULED', 'PUBLISHED', 'FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE uploaded_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    brief_id UUID REFERENCES product_briefs(id) ON DELETE SET NULL,
    post_id UUID REFERENCES generated_posts(id) ON DELETE SET NULL,
    file_url TEXT NOT NULL,
    file_name VARCHAR(255),
    mime_type VARCHAR(255),
    file_size BIGINT,
    public_id VARCHAR(255),
    storage_provider VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE social_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(255) NOT NULL CHECK (provider IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),
    provider_account_id VARCHAR(255),
    page_id VARCHAR(255),
    page_name VARCHAR(255),
    access_token_encrypted TEXT,
    token_expires_at TIMESTAMP,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    account_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP
);

CREATE TABLE scheduled_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES generated_posts(id) ON DELETE CASCADE,
    social_account_id UUID REFERENCES social_accounts(id) ON DELETE SET NULL,
    platform VARCHAR(255) NOT NULL CHECK (platform IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),
    scheduled_time TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE publish_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheduled_post_id UUID NOT NULL REFERENCES scheduled_posts(id) ON DELETE CASCADE,
    provider VARCHAR(255),
    external_post_id VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    error_message TEXT,
    published_at TIMESTAMP
);

CREATE TABLE ai_generation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    brief_id UUID REFERENCES product_briefs(id) ON DELETE SET NULL,
    post_id UUID REFERENCES generated_posts(id) ON DELETE SET NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'OPENAI',
    model VARCHAR(100),
    prompt TEXT,
    response JSONB,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    status VARCHAR(50) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE social_data_deletion_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    confirmation_code VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(255) NOT NULL CHECK (provider IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),
    status VARCHAR(255) NOT NULL,
    deleted_accounts INTEGER NOT NULL,
    completed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_product_briefs_user_id ON product_briefs(user_id);
CREATE INDEX idx_product_briefs_business_id ON product_briefs(business_id);
CREATE INDEX idx_generated_posts_brief_id ON generated_posts(brief_id);
CREATE INDEX idx_generated_posts_user_id ON generated_posts(user_id);
CREATE INDEX idx_generated_posts_status ON generated_posts(status);
CREATE INDEX idx_post_scores_post_id ON post_scores(post_id);
CREATE INDEX idx_content_calendars_user_id ON content_calendars(user_id);
CREATE INDEX idx_content_calendars_business_id ON content_calendars(business_id);
CREATE INDEX idx_calendar_items_calendar_id ON calendar_items(calendar_id);
CREATE INDEX idx_calendar_items_planned_date ON calendar_items(planned_date);
CREATE INDEX idx_uploaded_files_user_id ON uploaded_files(user_id);
CREATE INDEX idx_uploaded_files_brief_id ON uploaded_files(brief_id);
CREATE INDEX idx_uploaded_files_post_id ON uploaded_files(post_id);
CREATE INDEX idx_social_accounts_user_id ON social_accounts(user_id);
CREATE INDEX idx_scheduled_posts_post_id ON scheduled_posts(post_id);
CREATE INDEX idx_scheduled_posts_social_account_id ON scheduled_posts(social_account_id);
CREATE INDEX idx_scheduled_posts_due ON scheduled_posts(status, scheduled_time);
CREATE INDEX idx_publish_logs_scheduled_post_id ON publish_logs(scheduled_post_id);
CREATE INDEX idx_ai_generation_logs_user_id ON ai_generation_logs(user_id);
CREATE INDEX idx_ai_generation_logs_brief_id ON ai_generation_logs(brief_id);
CREATE INDEX idx_ai_generation_logs_post_id ON ai_generation_logs(post_id);

-- Preserve the four initial template defaults for fresh installations only.
-- Existing databases skip V1; their template data is not overwritten.
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
