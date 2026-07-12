package com.dev.nexusfinance.controller;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.UserService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }
    @PostMapping public ResponseEntity<User> create(@RequestBody User user) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user)); }
    @GetMapping("/me") public User me(@RequestAttribute UUID authenticatedUserId) { return service.findById(authenticatedUserId); }
    @DeleteMapping("/me") public ResponseEntity<Void> delete(@RequestAttribute UUID authenticatedUserId) { service.delete(authenticatedUserId); return ResponseEntity.noContent().build(); }
}
