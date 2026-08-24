CREATE TABLE tb_monthly_forecasts (
    id_forecast BINARY(16) NOT NULL,
    id_user BINARY(16) NOT NULL,
    reference_month DATE NOT NULL,
    expected_income DECIMAL(19, 2) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_monthly_forecasts PRIMARY KEY (id_forecast),
    CONSTRAINT uk_forecast_user_month UNIQUE (id_user, reference_month),
    CONSTRAINT fk_monthly_forecasts_user
        FOREIGN KEY (id_user) REFERENCES tb_users (id_user)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tb_planned_expenses (
    id_planned_expense BINARY(16) NOT NULL,
    id_forecast BINARY(16) NOT NULL,
    description VARCHAR(120) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    due_date DATE NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_planned_expenses PRIMARY KEY (id_planned_expense),
    INDEX idx_planned_expense_forecast_due (id_forecast, due_date),
    CONSTRAINT fk_planned_expenses_forecast
        FOREIGN KEY (id_forecast) REFERENCES tb_monthly_forecasts (id_forecast)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
