package com.example.azurestorage.service;

import com.azure.core.http.rest.Response;
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

    /* Parametros de conexion, los obtiene del application.properties */
    @Value("${spring.cloud.azure.storage.blob.connection-string}")
    private String connectStr;

    @Value("${spring.cloud.azure.storage.blob.container-name}")
    private String containerName;

    /* Método para obtener el cliente del container */
    private BlobContainerClient containerClient() {
        /*
         * Create a BlobServiceClient (storage account client) object that wraps the service endpoint, credential and
         * a request pipeline, y todos esos parametros estan el connection string.
         */
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectStr)
                .buildClient();

        /*
         * Create a client that references a to-be-created container in your Azure Storage account. This returns a
         * ContainerClient object that wraps the container's endpoint, credential and a request pipeline (inherited
         * from blobServiceClient).
         * Container names require lowercase.
         */
        BlobContainerClient blobContainer = blobServiceClient.getBlobContainerClient(containerName);

        /*
         * Sets the container's permissions. The permissions indicate whether blobs in a container
         * may be accessed publicly.
         */
        if (blobContainer.getAccessPolicy().getBlobAccessType() != PublicAccessType.BLOB) {
            BlobSignedIdentifier identifier = new BlobSignedIdentifier()
                    .setId("imagenes")
                    .setAccessPolicy(new BlobAccessPolicy().setPermissions("r")); // r: read
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

    /* Método para listar los blobs del container */
    public ArrayList<BlobItem> listaBlobs() {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            try {
                /*
                 * List the blob(s) in our container.
                 */
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

    /* Método que recibe el archivo y retorna el link del blob en la nube */
    public String subirArchivo(MultipartFile file, String name) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null && file != null) {
            /*
             * Valida que el archivo sea una imagen
             */
            ArrayList<String> typeList = new ArrayList<>(Arrays.asList("image/jpg", "image/jpeg", "image/png"));
            if (!typeList.contains(file.getContentType())) {
                System.out.println("El archivo subido no es una imagen.");
                return null;
            }
            /*
             * Obtiene la extensión del archivo subido
             */
            String fileName = file.getOriginalFilename();
            assert fileName != null;
            String fileExtension = fileName.substring(fileName.lastIndexOf("."));

            try {
                /*
                 * Create a client that references a to-be-created blob in your Azure Storage account's container.
                 * This returns a BlockBlobClient object that wraps the blob's endpoint, credential and a request pipeline
                 * (inherited from containerClient). Note that blob names can be mixed case.
                 */
                BlobClient blobClient = blobContainer.getBlobClient(name + fileExtension);
                /*
                 * Create a blob with blob's blobMetadata, BlobHttpHeaders and BlobRequestConditions
                 */
                Map<String, String> blobMetadata = Collections.singletonMap("myblobmetadata", "sample");
                BlobHttpHeaders httpHeaders = new BlobHttpHeaders()
                        .setContentDisposition("attachment")
                        .setContentType(file.getContentType());
                BlobRequestConditions requestConditions = new BlobRequestConditions().setIfNoneMatch("*");

                /*
                 * Send an MD5 hash of the content to be validated by the service.
                 */
                byte[] md5 = MessageDigest.getInstance("MD5").digest(file.getBytes());

                /*
                 * Data which will upload to block blob and extra configs
                 */
                BlobParallelUploadOptions options = new BlobParallelUploadOptions(file.getInputStream());
                options.setComputeMd5(true);
                options.setHeaders(httpHeaders);
                options.setMetadata(blobMetadata);
                options.setRequestConditions(requestConditions);

                /*
                 * Creates a new blob, or updates the content of an existing blob. (En este caso lo creo porque se
                 * configuro un setIfNoneMatch en las requestConditions.
                 */
                Response<BlockBlobItem> blobItem =  blobClient.uploadWithResponse(options, null, Context.NONE);
                return blobClient.getBlobUrl(); // Retorna el link guardado en la nube

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
            /* Delete block blob by name */
            blobContainer.getBlobClient(name).delete();
            return true;
        } else {
            return false;
        }
    }

    public String obtenerUrl(String name) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            return blobContainer.getBlobClient(name).getBlobUrl();
        } else {
            return null;
        }
    }
}
