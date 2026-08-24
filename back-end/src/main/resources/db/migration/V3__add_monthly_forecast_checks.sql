ALTER TABLE tb_monthly_forecasts
    ADD CONSTRAINT chk_forecast_expected_income
        CHECK (expected_income >= 0);

ALTER TABLE tb_planned_expenses
    ADD CONSTRAINT chk_planned_expense_amount
        CHECK (amount > 0);
