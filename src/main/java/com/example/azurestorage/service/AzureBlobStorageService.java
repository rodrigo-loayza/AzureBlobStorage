package com.example.azurestorage.service;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AzureBlobStorageService {

    // Parametros de conexion, los obtiene del application.properties
    @Value("${spring.cloud.azure.storage.blob.connection-string}")
    private String connectStr;

    @Value("${spring.cloud.azure.storage.blob.container-name}")
    private String containerName;

    private BlobContainerClient containerClient() {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectStr)
                .buildClient();
        BlobContainerClient blobContainer = blobServiceClient.getBlobContainerClient(containerName); //asegurarse que este creado a no ser que deseemos crear un container
        if (blobContainer.getAccessPolicy().getBlobAccessType() != PublicAccessType.BLOB) {
            BlobSignedIdentifier identifier = new BlobSignedIdentifier()
                    .setId("imagenes")
                    .setAccessPolicy(new BlobAccessPolicy().setPermissions("r"));
            try {
                blobContainer.setAccessPolicy(PublicAccessType.BLOB, Collections.singletonList(identifier));
                System.out.printf("Set Access Policy completed %n");
                if (!blobContainer.exists()) {
                    blobContainer.create();
                }
            } catch (UnsupportedOperationException error) {
                System.out.printf("Set Access Policy completed %s%n", error);
                return null;
            }
        }
        return blobContainer;
    }

    public ArrayList<BlobItem> listaBlobs() {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            try {
                ArrayList<BlobItem> listaBlobs = new ArrayList<>();
                blobContainer.listBlobs().forEach(listaBlobs::add);
                return listaBlobs;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    public String subirArchivo(MultipartFile file, String name) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            /* Obtiene la extensión del archivo subido */
            String fileName = file.getOriginalFilename();
            assert fileName != null;
            String fileExtension = fileName.substring(fileName.lastIndexOf("."));

            try {
                BlobClient blobClient = blobContainer.getBlobClient(name + fileExtension);
                Map<String, String> blobMetadata = Collections.singletonMap("myblobmetadata", "sample");
                BlobHttpHeaders httpHeaders = new BlobHttpHeaders()
                        .setContentDisposition("attachment")
                        .setContentType("image/jpeg");
                byte[] md5 = MessageDigest.getInstance("MD5").digest(file.getBytes());
                BlobParallelUploadOptions options = new BlobParallelUploadOptions(file.getInputStream());
                options.setComputeMd5(true);
                options.setHeaders(httpHeaders);
                options.setMetadata(blobMetadata);
                blobClient.uploadWithResponse(options, null, Context.NONE);
                return blobClient.getBlobUrl();

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean borrarArchivoPorNombre(String name) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            blobContainer.getBlobClient(name).delete();
            return true;
        } else {
            return false;
        }
    }

}
