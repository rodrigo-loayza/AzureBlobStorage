package com.example.azurestorage.controller;

import com.example.azurestorage.entity.Image;
import com.example.azurestorage.repository.ImageRepository;
import com.example.azurestorage.service.AzureBlobStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/blob")
public class BlobController {

    @Autowired
    AzureBlobStorageService azureBlobStorageService;

    @Autowired
    ImageRepository imageRepository;

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
    public String guardar(@RequestParam("file") MultipartFile file, Image image,
                          @RequestParam("my_file[]") MultipartFile[] multipartFiles, @RequestParam("jajaja") List<String> listatest) throws IOException {
//        String imgUrl = azureBlobStorageService.subirArchivo(file, "img_" + UUID.randomUUID());
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
        for (String a : listatest) System.out.println(a);

        return "redirect:/blob/lista";
    }

    @GetMapping("/borrar")
    public String borrar(@RequestParam("name") String name) {
        if (azureBlobStorageService.borrarArchivoPorNombre(name)) {

            return "redirect:/blob/lista";
        } else {
            return "redirect:/error";
        }
    }
}
