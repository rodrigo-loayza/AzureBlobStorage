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
//import org.imgscalr.Scalr;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AzureBlobStorageService {

    /*
     * -- Teatro aspect ratio validation: [1.5, 2.17]; thumb = 495 x 225 px; min_size = 530 x 350 px
     * -- Obra aspect ratio validation: [0.6, 0.77]; thumb = 308 x 410 px; min_size = 335 x 475 px
    */

    private static final int[] teatroThumbSize = {495, 225};
    private static final int[] obraThumbSize = {308, 410};

    private static final int[] teatroMinSize = {530, 350};
    private static final int[] obraMinSize = {308, 410};

    private static final double[] teatroAspectRatio = {1.5, 2.17};
    private static final double[] obraAspectRatio = {0.6, 0.77};

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

    /* Delete block blob by name */
    public boolean borrarArchivoPorNombre(String name) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            /* Delete block blob by name */
            BlobClient blobClient = blobContainer.getBlobClient(name);
            if (blobClient.exists()) blobClient.delete();
            else return false;

            return true;
        } else {
            return false;
        }
    }

    /* Delete block blob by url */
    public boolean borrarArchivoPorUrl(String url) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null) {
            String[] urlList = url.split("/");
            String filename = urlList[urlList.length - 1];
            BlobClient blobClient = blobContainer.getBlobClient(filename);
            if (blobClient.exists()) blobClient.delete();
            else return false;

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
     * Generates and uploads thumbnail, then sets thumbnail url to blobData object
     */
    public boolean genThumbnail(BlobContainerClient blobContainer, MultipartFile tmpMultipart, BlobData blobData, int[] size) {
        try {
            BufferedImage img = ImageIO.read(tmpMultipart.getInputStream());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.of(img)
                    .size(size[0], size[1])
                    .keepAspectRatio(false)
                    .outputFormat("JPEG")
                    .outputQuality(1)
                    .toOutputStream(baos);
            byte[] data = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(data);

            String originFileName = blobData.getFileName();
            String blobThumbnail = originFileName.substring(0, originFileName.lastIndexOf(".")) + "_thumbnail.jpg";
            BlobClient blobClient = blobContainer.getBlobClient(blobThumbnail);

            /* Blob uploading */
            Map<String, String> blobMetadata = Collections.singletonMap("myblobmetadata", "sample");
            BlobHttpHeaders httpHeaders = new BlobHttpHeaders()
                    .setContentDisposition("attachment")
                    .setContentType("image/jpeg")
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
            img.flush();

            return true;

        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    /*
     * Sube la imagen después de validar que sea una y que tenga extensión correcta, además genera un thumbnail
     * si se desea de acuerdo al tipo
     */
    public boolean uploadImage(MultipartFile file, BlobData blobData, String name, Boolean genThumb, String sizeOption) {
        BlobContainerClient blobContainer = containerClient();
        if (blobContainer != null && file != null) {
            if (!isImage(file)) return false;
            if (!isValidSize(file, sizeOption)) return false;

            String fileExtension = getFileExtension(file).orElse(null);
            if (fileExtension == null) return false;
            blobData.setFileName(name + fileExtension);

            try {
                if (genThumb) {
                    switch (sizeOption) {
                        case "teatro":
                            if (!genThumbnail(blobContainer, file, blobData, teatroThumbSize)) return false;
                            break;
                        case "obra":
                            if (!genThumbnail(blobContainer, file, blobData, obraThumbSize)) return false;
                            break;
                        default:
                            return false;
                    }
                }

                String imgUrl = upload(blobContainer, file, blobData.getFileName());
                if (imgUrl == null) return false;
                blobData.setFileUrl(imgUrl);

                return true;

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

        } else {
            return false;
        }
    }

    /*
     * Valida que el archivo sea una imagen
     */
    public boolean isImage(MultipartFile file) {
        Tika tika = new Tika();
        try {
            return Arrays.asList("image/jpg", "image/jpeg", "image/png").contains(tika.detect(file.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /*
     * Valida el aspect ratio y tamaño minimo de la imagen
     */
    public boolean isValidSize(MultipartFile file, String option) {
        try {
            InputStream fileIS = file.getInputStream();
            BufferedImage img = ImageIO.read(fileIS);

//            if (img == null) {
//                //Find a suitable ImageReader
//                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
//                ImageReader reader = null;
//                while(readers.hasNext()) {
//                    reader = readers.next();
//                    if(reader.canReadRaster()) {
//                        break;
//                    }
//                }
//
//                //Stream the image file (the original CMYK image)
////                ImageInputStream input = ImageIO.createImageInputStream(file.getBytes());
//                reader.setInput(fileIS);
//
//                //Read the image raster
//                Raster raster = reader.readRaster(0, null);
//
//                //Create a new RGB image
//                img = new BufferedImage(raster.getWidth(), raster.getHeight(),
//                        BufferedImage.TYPE_INT_BGR);
//
//                //Fill the new image with the old raster
//                img.getRaster().setRect(raster);
//            }

            int width = img.getWidth();
            int height = img.getHeight();
            double ratio = (double) width / height;

            int[] minSize;
            boolean validRatio = false;
            switch (option) {
                case "teatro":
                    minSize = teatroMinSize;
                    validRatio = teatroAspectRatio[0] <= ratio && ratio <= teatroAspectRatio[1];
                    break;
                case "obra":
                    minSize = obraMinSize;
                    validRatio = obraAspectRatio[0] <= ratio && ratio <= obraAspectRatio[1];
                    break;
                default:
                    return false;
            }

            return width >= minSize[0] && height >= minSize[1] && validRatio;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /*
     * Obtiene la extensión del archivo a subir
     */
    public Optional<String> getFileExtension(MultipartFile file) {
        return file.getOriginalFilename() != null
                ? Optional.of(file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")))
                : Optional.empty();
    }

    /*
     * Sube el archivo al blob container y devuelve el url
     */
    public String upload(BlobContainerClient blobContainer, MultipartFile file, String name) {
        try {
            /*
             * Create a client that references a to-be-created blob in your Azure Storage account's container.
             * This returns a BlockBlobClient object that wraps the blob's endpoint, credential and a request pipeline
             * (inherited from containerClient). Note that blob names can be mixed case.
             */
            BlobClient blobClient = blobContainer.getBlobClient(name);
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
            blobClient.uploadWithResponse(options, null, Context.NONE);

            return blobClient.getBlobUrl();

        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
