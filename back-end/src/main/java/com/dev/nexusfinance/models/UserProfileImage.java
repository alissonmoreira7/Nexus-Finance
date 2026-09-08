package com.dev.nexusfinance.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "tb_user_profile_images")
@Getter @Setter
public class UserProfileImage {
    @Id
    @Column(name = "id_user", nullable = false)
    private UUID userId;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
}
