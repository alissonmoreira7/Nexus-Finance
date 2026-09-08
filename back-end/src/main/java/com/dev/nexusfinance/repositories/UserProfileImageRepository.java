package com.dev.nexusfinance.repositories;

import com.dev.nexusfinance.models.UserProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserProfileImageRepository extends JpaRepository<UserProfileImage, UUID> {}
