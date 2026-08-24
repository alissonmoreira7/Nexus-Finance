package com.dev.nexusfinance.repositories;

import com.dev.nexusfinance.models.MonthlyForecast;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyForecastRepository extends JpaRepository<MonthlyForecast, UUID> {
    @EntityGraph(attributePaths = "expenses")
    @Query("select forecast from MonthlyForecast forecast where forecast.user.idUser = :userId and forecast.referenceMonth = :month")
    Optional<MonthlyForecast> findByUserAndMonth(@Param("userId") UUID userId, @Param("month") LocalDate month);
}
