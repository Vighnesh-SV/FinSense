-- =====================================================================
-- FinSense database schema (MySQL 8.0.16+, InnoDB, utf8mb4)
--
-- Conventions
--   * Money is stored as BIGINT paise (Rs 1 = 100 paise) - no floating point.
--   * DATE for calendar dates, DATETIME for timestamps.
--   * ENUM columns restrict values to a fixed list (like Java enums).
--   * Every user-owned row carries user_id (directly or via its parent),
--     and ON DELETE CASCADE removes a user's data together with the user.
--   * Every statement is idempotent (IF NOT EXISTS) so running this script
--     on every start-up never destroys existing data.
--   * Tables are created parent-first, because MySQL requires a referenced
--     table to exist before a FOREIGN KEY can point to it.
-- =====================================================================

-- 1. users ------------------------------------------------------------
-- The default utf8mb4 collation is case-insensitive, so 'Priya' and 'priya'
-- count as the same username for the UNIQUE constraint.
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt          VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2. financial_profiles (one per user) -------------------------------
CREATE TABLE IF NOT EXISTS financial_profiles (
    user_id                BIGINT   NOT NULL PRIMARY KEY,
    monthly_income         BIGINT   NOT NULL DEFAULT 0 CHECK (monthly_income >= 0),
    fixed_expenses         BIGINT   NOT NULL DEFAULT 0 CHECK (fixed_expenses >= 0),
    variable_expenses      BIGINT   NOT NULL DEFAULT 0 CHECK (variable_expenses >= 0),
    current_savings        BIGINT   NOT NULL DEFAULT 0 CHECK (current_savings >= 0),
    monthly_savings_target BIGINT   NOT NULL DEFAULT 0 CHECK (monthly_savings_target >= 0),
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 3. commitments (EMIs, rent, loans ... recurring monthly obligations) -
CREATE TABLE IF NOT EXISTS commitments (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    type           ENUM ('EMI', 'LOAN', 'RENT', 'INSURANCE', 'OTHER') NOT NULL,
    monthly_amount BIGINT       NOT NULL CHECK (monthly_amount > 0),
    start_date     DATE         NOT NULL,
    end_date       DATE         NULL,                       -- NULL = open-ended
    CONSTRAINT chk_commitment_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT fk_commitment_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 4. categories (user_id NULL = built-in category shared by everyone) ---
CREATE TABLE IF NOT EXISTS categories (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(50) NOT NULL,
    type          ENUM ('INCOME', 'EXPENSE') NOT NULL,
    nature        ENUM ('ESSENTIAL', 'DISCRETIONARY') NOT NULL DEFAULT 'ESSENTIAL',
    is_commitment BOOLEAN     NOT NULL DEFAULT FALSE,
    user_id       BIGINT      NULL,
    CONSTRAINT ux_categories_name UNIQUE (name, type, user_id),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 5. purchases (an item the user asked FinSense about) -----------------
CREATE TABLE IF NOT EXISTS purchases (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    category_id  BIGINT       NOT NULL,
    price        BIGINT       NOT NULL CHECK (price > 0),
    description  VARCHAR(500) NULL,
    necessity    ENUM ('NEED', 'WANT') NOT NULL DEFAULT 'WANT',
    status       ENUM ('EVALUATED', 'PURCHASED', 'DROPPED') NOT NULL DEFAULT 'EVALUATED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_purchases_user (user_id, created_at),
    CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 6. purchase_history (every evaluation of a purchase, frozen in time) --
-- snapshot_* columns preserve the user's finances at evaluation time, so
-- history stays meaningful even after the profile changes.
CREATE TABLE IF NOT EXISTS purchase_history (
    id                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_id           BIGINT      NOT NULL,
    recommendation        ENUM ('BUY', 'CONSIDER', 'WAIT', 'AVOID') NOT NULL,
    score                 INT         NOT NULL CHECK (score BETWEEN 0 AND 100),
    explanation           TEXT        NOT NULL,
    warnings              TEXT        NULL,
    snapshot_income       BIGINT      NOT NULL,
    snapshot_expenses     BIGINT      NOT NULL,
    snapshot_disposable   BIGINT      NOT NULL,             -- may be negative
    snapshot_free_savings BIGINT      NOT NULL,
    snapshot_behaviour    VARCHAR(30) NOT NULL,
    evaluated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 7. transactions (unified INCOME / EXPENSE model) ---------------------
CREATE TABLE IF NOT EXISTS transactions (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    category_id BIGINT       NOT NULL,
    type        ENUM ('INCOME', 'EXPENSE') NOT NULL,
    amount      BIGINT       NOT NULL CHECK (amount > 0),
    txn_date    DATE         NOT NULL,
    description VARCHAR(255) NULL,
    purchase_id BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_transactions_user_date (user_id, txn_date),
    CONSTRAINT fk_txn_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_txn_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_txn_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 8. savings_goals ----------------------------------------------------
-- The saved amount is NOT stored here: it is always SUM(goal_contributions),
-- so a contribution can never be counted twice.
CREATE TABLE IF NOT EXISTS savings_goals (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    target_amount BIGINT       NOT NULL CHECK (target_amount > 0),
    deadline      DATE         NOT NULL,
    priority      ENUM ('HIGH', 'MEDIUM', 'LOW') NOT NULL DEFAULT 'MEDIUM',
    status        ENUM ('ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 9. goal_contributions -----------------------------------------------
CREATE TABLE IF NOT EXISTS goal_contributions (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    goal_id        BIGINT       NOT NULL,
    amount         BIGINT       NOT NULL CHECK (amount > 0),
    contributed_on DATE         NOT NULL,
    note           VARCHAR(255) NULL,
    CONSTRAINT fk_contribution_goal FOREIGN KEY (goal_id) REFERENCES savings_goals (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 10. questionnaires (one row per attempt) ------------------------------
CREATE TABLE IF NOT EXISTS questionnaires (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    answers          VARCHAR(100) NOT NULL,                 -- e.g. '3,4,2,5,1,...'
    impulse_score    INT         NOT NULL CHECK (impulse_score BETWEEN 0 AND 100),
    volume_score     INT         NOT NULL CHECK (volume_score BETWEEN 0 AND 100),
    discipline_score INT         NOT NULL CHECK (discipline_score BETWEEN 0 AND 100),
    taken_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_questionnaire_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 11. behaviour_profiles (classification history; latest row = current) -
CREATE TABLE IF NOT EXISTS behaviour_profiles (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    type             ENUM ('IMPULSE_BUYER', 'HEAVY_BUYER', 'SMART_SHOPPER') NOT NULL,
    impulse_score    INT          NOT NULL CHECK (impulse_score BETWEEN 0 AND 100),
    volume_score     INT          NOT NULL CHECK (volume_score BETWEEN 0 AND 100),
    discipline_score INT          NOT NULL CHECK (discipline_score BETWEEN 0 AND 100),
    source           ENUM ('QUESTIONNAIRE', 'SPENDING') NOT NULL,
    reason           VARCHAR(255) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_behaviour_user (user_id, created_at),
    CONSTRAINT fk_behaviour_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 12. purchase_feedback (how the user felt about a purchase afterwards) --
--     The learning signal: FinSense adjusts future advice from these ratings.
CREATE TABLE IF NOT EXISTS purchase_feedback (
    purchase_id BIGINT   NOT NULL PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    feedback    ENUM ('WORTH_IT', 'NEUTRAL', 'REGRET') NOT NULL,
    rated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_feedback_user (user_id),
    CONSTRAINT fk_feedback_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 13. category_budgets (monthly spending limit per expense category) -----
CREATE TABLE IF NOT EXISTS category_budgets (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    category_id   BIGINT NOT NULL,
    monthly_limit BIGINT NOT NULL CHECK (monthly_limit > 0),
    CONSTRAINT ux_budget_user_category UNIQUE (user_id, category_id),
    CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
