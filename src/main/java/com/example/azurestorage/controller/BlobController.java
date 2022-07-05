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

import java.io.IOException;
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
    public String guardar(Image image,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          @RequestParam(value = "my_files[]", required = false) MultipartFile[] multipartFiles) throws IOException {
        String fileName = "img_" + UUID.randomUUID();
        BlobData blobData = new BlobData();
        if (azureBlobStorageService.subirArchivo(file, blobData, fileName)) {
            image.setImages(blobData.getThumbnailUrl());
            imageRepository.save(image);
            return "redirect:/blob/lista";
        } else {
            return "redirect:/error";
        }

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
