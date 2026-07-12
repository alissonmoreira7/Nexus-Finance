package com.dev.nexusfinance.models;

import jakarta.persistence.*;

@Entity
@Table(name = "tb_categories")
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

    public Long getIdCategory() { return idCategory; }
    public void setIdCategory(Long idCategory) { this.idCategory = idCategory; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CategoryType getType() { return type; }
    public void setType(CategoryType type) { this.type = type; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
}
