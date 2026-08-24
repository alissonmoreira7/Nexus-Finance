CREATE TABLE tb_users (
    id_user BINARY(16) NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL,
    plan ENUM('BASIC', 'PREMIUM') NOT NULL,
    role ENUM('USER') NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id_user),
    CONSTRAINT uk_users_cpf UNIQUE (cpf),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tb_accounts (
    id_account BINARY(16) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    id_user BINARY(16) NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id_account),
    INDEX idx_accounts_user (id_user),
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (id_user) REFERENCES tb_users (id_user)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tb_categories (
    id_category BIGINT NOT NULL AUTO_INCREMENT,
    keywords_category VARCHAR(255) NOT NULL,
    name_category VARCHAR(255) NOT NULL,
    type_category ENUM('EXPENSE', 'INCOME') NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id_category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tb_transactions (
    id_transaction BINARY(16) NOT NULL,
    amount DECIMAL(38, 2) NOT NULL,
    clean_description VARCHAR(255) NOT NULL,
    raw_description VARCHAR(255) NOT NULL,
    transaction_date DATE NOT NULL,
    id_account BINARY(16) NOT NULL,
    id_category BIGINT NOT NULL,
    transaction_source ENUM('CSV', 'MANUAL') NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id_transaction),
    INDEX idx_transactions_account (id_account),
    INDEX idx_transactions_category (id_category),
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (id_account) REFERENCES tb_accounts (id_account)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (id_category) REFERENCES tb_categories (id_category)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
