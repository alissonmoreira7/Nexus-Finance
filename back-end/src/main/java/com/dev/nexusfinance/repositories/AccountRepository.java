package com.dev.nexusfinance.repositories;

import com.dev.nexusfinance.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>{}
