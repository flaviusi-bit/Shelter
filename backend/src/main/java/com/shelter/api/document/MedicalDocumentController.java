package com.shelter.api.document;

import com.shelter.api.animal.AnimalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/documents")
public class MedicalDocumentController {
    private final MedicalDocumentRepository documents;
    private final AnimalRepository animals;

    public MedicalDocumentController(MedicalDocumentRepository documents, AnimalRepository animals){
        this.documents=documents; this.animals=animals;
    }

    @GetMapping
    public List<MedicalDocument> list(@PathVariable UUID animalId){
        return documents.findByAnimalIdOrderByDocumentDateDescCreatedAtDesc(animalId);
    }

    @PostMapping
    public MedicalDocument create(@PathVariable UUID animalId, @RequestBody MedicalDocument input, Authentication auth){
        var animal=animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        input.setAnimal(animal);
        input.setUploadedBy(auth.getName());
        input.setId(null);
        return documents.save(input);
    }
}
