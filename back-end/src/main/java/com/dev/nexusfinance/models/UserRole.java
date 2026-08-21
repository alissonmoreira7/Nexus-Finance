package com.dev.nexusfinance.models;

public enum UserRole {

    USER("user");

    private String role;

    UserRole(String role){
        this.role = role;
    }

    public String getRole(){
        return this.role;
    }
}
