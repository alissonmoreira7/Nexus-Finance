package com.dev.nexusfinance.controller;
import com.dev.nexusfinance.dto.UserView;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.UserService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }
    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user));
    }
    @GetMapping("/me")
    public UserView me(@RequestAttribute UUID authenticatedUserId) {
        return service.getProfile(authenticatedUserId);
    }
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserView update(@RequestAttribute UUID authenticatedUserId,
                           @RequestParam("name") String name,
                           @RequestParam("email") String email,
                           @RequestParam("cpf") String cpf,
                           @RequestParam(name = "removeImage", defaultValue = "false") boolean removeImage,
                           @RequestPart(name = "photo", required = false) MultipartFile photo) {
        try {
            byte[] data = photo == null || photo.isEmpty() ? null : photo.getBytes();
            String contentType = photo == null ? null : photo.getContentType();
            return service.updateProfile(authenticatedUserId, name, email, cpf, data, contentType, removeImage);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível processar a foto");
        }
    }
    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@RequestAttribute UUID authenticatedUserId) {
        service.delete(authenticatedUserId);
        return ResponseEntity.noContent().build();
    }
}
