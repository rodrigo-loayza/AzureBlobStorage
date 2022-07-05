package com.example.azurestorage.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BlobData {

    // Nombre del archivo, es único y es el parámetro para identificarlo en la base
    private String fileName;

    // URL del archivo en tamaño original
    private String fileUrl;

    // URL del archivo en tamaño miniatura
    private String thumbnailUrl;

}
