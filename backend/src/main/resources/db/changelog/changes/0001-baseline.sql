CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(255),
    department VARCHAR(255),
    interest_category VARCHAR(255),
    created_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS news (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category     VARCHAR(30)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      MEDIUMTEXT,
    url          VARCHAR(500) NOT NULL UNIQUE,
    press        VARCHAR(100),
    published_at DATETIME(6),
    keywords     TEXT,
    created_at   DATETIME(6),
    INDEX idx_category (category),
    INDEX idx_published_at (published_at)
);

CREATE TABLE IF NOT EXISTS bookmark (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    news_id      BIGINT       NOT NULL,
    saved_at     DATETIME(6),
    UNIQUE KEY uk_bookmark_user_news (user_id, news_id),
    CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_bookmark_news FOREIGN KEY (news_id) REFERENCES news(id)
);

CREATE TABLE IF NOT EXISTS watchlist (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT      NOT NULL,
    symbol   VARCHAR(20) NOT NULL,
    added_at DATETIME(6),
    UNIQUE KEY uk_watchlist_user_symbol (user_id, symbol),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS briefing_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    job_id        VARCHAR(36),
    generated_at  DATETIME(6)  NOT NULL,
    symbols       VARCHAR(500) NOT NULL,
    status        VARCHAR(30),
    llm_narrative TEXT,
    INDEX idx_briefing_user (user_id, generated_at),
    UNIQUE KEY uk_briefing_history_job_id (job_id),
    CONSTRAINT fk_briefing_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS analysis_job (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id        VARCHAR(36)  NOT NULL,
    event_id      VARCHAR(36)  NOT NULL,
    user_id       BIGINT       NOT NULL,
    symbols       VARCHAR(500) NOT NULL,
    days          INT          NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    llm_narrative TEXT,
    error_message TEXT,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_analysis_job_job_id (job_id),
    UNIQUE KEY uk_analysis_job_event_id (event_id),
    INDEX idx_analysis_job_user_created (user_id, created_at),
    CONSTRAINT fk_analysis_job_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id    VARCHAR(36) NOT NULL PRIMARY KEY,
    processed_at DATETIME(6) NOT NULL
);
