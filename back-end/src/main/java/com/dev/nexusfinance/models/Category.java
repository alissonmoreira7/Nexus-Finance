package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_categories")
@Getter @Setter
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_category", updatable = false, nullable = false)
    private Long idCategory;

    @Column(name = "name_category", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_category", nullable = false, length = 10)
    private CategoryType type;

    @Column(name = "keywords_category", nullable = false)
    private String keywords;
}
