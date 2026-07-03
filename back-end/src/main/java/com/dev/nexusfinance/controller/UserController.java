package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/")
    public User create(@RequestBody User user) {
       return this.userRepository.save(user);
    }
}
