package com.retail.assistant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String category;

    private String subCategory;

    private String brand;

    private String color;

    private Double price;

    private Double rating;

    @Column(length = 1000)
    private String description;

    private String tags;

    private String imageUrl;

    private Integer totalSold;
}