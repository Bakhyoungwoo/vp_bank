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
    news_url     VARCHAR(1000) NOT NULL,
    title        VARCHAR(255),
    press        VARCHAR(255),
    published_at VARCHAR(255),
    saved_at     DATETIME(6)
);
