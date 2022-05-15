package com.example.azurestorage.service;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class AzureBlobStorageService {

    // Parametros de conexion, los obtiene del application.properties
    @Value("${spring.cloud.azure.storage.blob.connection-string}")
    private String connectStr;

    @Value("${spring.cloud.azure.storage.blob.container-name}")
    private String containerName;

    private CloudBlobContainer containerClient() {
        try {
            // Retrieve storage account from connection-string.
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(connectStr);

            // Create the blob client.
            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();

            // Get a reference to a container, must be lower case
            CloudBlobContainer container = blobClient.getContainerReference(containerName);

            // Create the container if it does not exist.
            container.createIfNotExists();

            // Create a permissions object
            BlobContainerPermissions containerPermissions = new BlobContainerPermissions();
            // Include public access in the permissions object
            containerPermissions.setPublicAccess(BlobContainerPublicAccessType.BLOB);
            // Set the permissions on the container
            container.uploadPermissions(containerPermissions);

            return container;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public ArrayList<ListBlobItem> listaBlobs() {
        try {
            ArrayList<ListBlobItem> listaBlobs = new ArrayList<>();
            for (ListBlobItem blobItem : containerClient().listBlobs()) {
                listaBlobs.add(blobItem);
            }
            return listaBlobs;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public String subirArchivo(MultipartFile file, String name) {

        String fileName = file.getOriginalFilename();
        assert fileName != null;
        String fileExtension = fileName.substring(fileName.lastIndexOf("."));

        try {
            CloudBlockBlob blob = containerClient().getBlockBlobReference(name + fileExtension);
            // Create or overwrite the blob with contents from an uploaded file from frontend.
            blob.upload(file.getInputStream(), file.getSize());
            return blob.getUri().toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean borrarArchivoPorNombre(String name) {
        try {
            containerClient().getBlockBlobReference(name)
                    .deleteIfExists();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
