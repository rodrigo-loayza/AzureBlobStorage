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
    public String guardar(@RequestParam("file") MultipartFile file, Image image) throws IOException {
        if (file.isEmpty()) {
            System.out.println("Error al cargar imagen, seleccione el archivo");
            return "redirect:/error";
        }
        //TODO: Validar que se suba un contentype de imagen para que el link display y no descargue.
        image.setImages(azureBlobStorageService.subirArchivo(file, "imagen_" + System.currentTimeMillis()));
        imageRepository.save(image);
        return "redirect:/blob/lista";
    }

    @GetMapping("/borrar")
    public String borrar(@RequestParam("name") String name) {
        if (azureBlobStorageService.borrarArchivoPorNombre(name)) {
            //TODO: Aplicar también el borrado en DB.
            return "redirect:/blob/lista";
        } else {
            return "redirect:/error";
        }
    }
}
