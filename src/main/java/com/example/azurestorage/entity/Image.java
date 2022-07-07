package com.example.azurestorage.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "images")
@Getter
@Setter
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "descripcion", length = 20)
    private String descripcion;

    @Column(name = "images")
    private String images;

    public Image() {

    }

    public Image(String descripcion, String images) {
        this.descripcion = descripcion;
        this.images = images;
    }
}