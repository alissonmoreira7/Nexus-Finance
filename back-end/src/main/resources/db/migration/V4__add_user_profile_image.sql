CREATE TABLE tb_user_profile_images (
    id_user BINARY(16) NOT NULL,
    image_data MEDIUMBLOB NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    CONSTRAINT pk_user_profile_images PRIMARY KEY (id_user),
    CONSTRAINT fk_profile_image_user
        FOREIGN KEY (id_user) REFERENCES tb_users (id_user)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
