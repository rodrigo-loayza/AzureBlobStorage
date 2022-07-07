package com.example.azurestorage.controller;

import com.example.azurestorage.dto.BlobData;
import com.example.azurestorage.entity.Image;
import com.example.azurestorage.repository.ImageRepository;
import com.example.azurestorage.service.AzureBlobStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@Controller
@RequestMapping("/blob")
public class BlobController {

    @Autowired
    private AzureBlobStorageService azureBlobStorageService;

    @Autowired
    private ImageRepository imageRepository;

    @GetMapping("/lista")
    public String readBlobFile(Model model) {
        model.addAttribute("listaBlobs", azureBlobStorageService.listaBlobs());
        model.addAttribute("listaImages", imageRepository.findAll());
        return "prev";
    }

    @GetMapping("/frm")
    public String frm() {
        return "frm";
    }

    @PostMapping("/guardar")
    public String guardar(Image image, RedirectAttributes attr,
                          @RequestParam(value = "file_obra", required = false) MultipartFile fileObra,
                          @RequestParam(value = "file_teatro", required = false) MultipartFile fileTeatro,
                          @RequestParam(value = "my_files[]", required = false) MultipartFile[] multipartFiles) throws IOException {
        ArrayList<String> errors = new ArrayList<>();
        BlobData blobData = new BlobData();
        if (fileObra != null && azureBlobStorageService.uploadImage(fileObra, blobData, "img_" + UUID.randomUUID(), true, "obra")) {
            Image imgObra = new Image(image.getDescripcion(), blobData.getThumbnailUrl());
            imageRepository.save(imgObra);
        } else {
            errors.add("ERROR EN LA SUBIDA DE OBRA");
        }
        if (fileTeatro != null && azureBlobStorageService.uploadImage(fileTeatro, blobData, "img_" + UUID.randomUUID(), true, "teatro")) {
            Image imgTeatro = new Image(image.getDescripcion(), blobData.getThumbnailUrl());
            imageRepository.save(imgTeatro);
        } else {
            errors.add("ERROR EN LA SUBIDA DE TEATRO");
        }
        if (!errors.isEmpty()) attr.addFlashAttribute("errors", errors);
        return "redirect:/blob/frm";

//        if (imgUrl != null) {
//            image.setImages(imgUrl);
//            imageRepository.save(image);
//            return "redirect:/blob/lista";
//        } else {
//            System.out.println("Error al subir la imagen.");
//            return "redirect:/error";
//        }
//        for (MultipartFile f : multipartFiles) {
//            System.out.println("Subiendo " + f.getOriginalFilename() + "...");
//            azureBlobStorageService.subirArchivo(f, "img_" + UUID.randomUUID());
//        }

    }

    @GetMapping("/borrar")
    public String borrar(@RequestParam("name") String name) {
        if (azureBlobStorageService.borrarArchivoPorNombre(name)) {
            return "redirect:/blob/lista";
        } else {
            return "redirect:/error";
        }
    }

    @GetMapping("/deleteFromDB")
    public String borrar(@RequestParam("id") Integer id) {
        imageRepository.delete(imageRepository.getById(id));
        return "redirect:/blob/lista";
    }
}
