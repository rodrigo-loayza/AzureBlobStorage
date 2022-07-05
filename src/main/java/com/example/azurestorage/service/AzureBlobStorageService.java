package com.example.azurestorage.service;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.example.azurestorage.dto.BlobData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AzureBlobStorageService {

    private static final int[] teatroSize = {300, 300};
    private static final int[] obraSize = {180, 600};

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
    public boolean subirArchivo(MultipartFile file, BlobData blobData, String name) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null && file != null) {
            /*
             * Valida que el archivo sea una imagen
             */
            ArrayList<String> typeList = new ArrayList<>(Arrays.asList("image/jpg", "image/jpeg", "image/png"));
            if (!typeList.contains(file.getContentType())) {
                System.out.println("El archivo subido no es una imagen.");
                return false;
            }
            /*
             * Obtiene la extensión del archivo subido y genera el nuevo nombre
             */
            String fileName = file.getOriginalFilename();
            assert fileName != null;
            String fileExtension = fileName.substring(fileName.lastIndexOf("."));
            System.out.println("Extension del archivo a subir: " + fileExtension);
            blobData.setFileName(name + fileExtension);

            try {
                /*
                 * Create a client that references a to-be-created blob in your Azure Storage account's container.
                 * This returns a BlockBlobClient object that wraps the blob's endpoint, credential and a request pipeline
                 * (inherited from containerClient). Note that blob names can be mixed case.
                 */
                BlobClient blobClient = blobContainer.getBlobClient(blobData.getFileName());
                /*
                 * Create a blob with blob's blobMetadata, BlobHttpHeaders and BlobRequestConditions
                 */
                Map<String, String> blobMetadata = Collections.singletonMap("myblobmetadata", "sample");
                BlobHttpHeaders httpHeaders = new BlobHttpHeaders()
                        .setContentDisposition("attachment")
                        .setContentType(file.getContentType())
                        /*
                         * Send an MD5 hash of the content to be validated by the service.
                         */
                        .setContentMd5(MessageDigest.getInstance("MD5").digest(file.getBytes()));
                BlobRequestConditions requestConditions = new BlobRequestConditions().setIfNoneMatch("*");

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
                Response<BlockBlobItem> blobItem = blobClient.uploadWithResponse(options, null, Context.NONE);
                blobData.setFileUrl(blobClient.getBlobUrl());

                /*
                 * Generate thumbnail
                 */
                genThumbnail(blobData, file, blobContainer);

                return true;

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
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

    /*
     * Generates and uploads thumbnail, then sets its url
     */
    public void genThumbnail(BlobData blobData, MultipartFile tmpMultipart, BlobContainerClient blobContainer) {
        try {
            BufferedImage img = new BufferedImage(teatroSize[0], teatroSize[1], BufferedImage.TYPE_INT_RGB);
            BufferedImage read = ImageIO.read(tmpMultipart.getInputStream());
            img.createGraphics().drawImage(
                    read.getScaledInstance(teatroSize[0], teatroSize[1], Image.SCALE_SMOOTH), 0, 0, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            InputStream bais = new ByteArrayInputStream(baos.toByteArray());

            String originFileName = blobData.getFileName();
            System.out.println(originFileName);
            String blobThumbnail = originFileName.substring(0, originFileName.lastIndexOf(".")) + "_thumbnail.jpg";
            BlobClient blobClient = blobContainer.getBlobClient(blobThumbnail);

            /* Blob uploading */
            Map<String, String> blobMetadata = Collections.singletonMap("myblobmetadata", "sample");
            BlobHttpHeaders httpHeaders = new BlobHttpHeaders()
                    .setContentDisposition("attachment")
                    .setContentType("image/jpeg")
//                    .setContentMd5(MessageDigest.getInstance("MD5").digest(bais.readAllBytes()));
                    .setContentMd5(MessageDigest.getInstance("MD5").digest(baos.toByteArray()));

            BlobRequestConditions requestConditions = new BlobRequestConditions().setIfNoneMatch("*");

            BlobParallelUploadOptions options = new BlobParallelUploadOptions(bais);
            options.setComputeMd5(true);
            options.setHeaders(httpHeaders);
            options.setMetadata(blobMetadata);
            options.setRequestConditions(requestConditions);

            Response<BlockBlobItem> blobItem = blobClient.uploadWithResponse(options, null, Context.NONE);

            blobData.setThumbnailUrl(blobClient.getBlobUrl());

            // Closes stream
            baos.close();
            bais.close();

        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            //TODO: Gatitos
        }
//        switch (option) {
//            case "teatro":
//
//
//                break;
//            case "obra":
//                break;
//            default:
//                break;
//        }
    }

}
