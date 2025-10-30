package com.example.atm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KeyChangeRequest {

    @NotNull(message = "Key type is required")
    private KeyType keyType;

    public enum KeyType {
        TMK,
        TPK,
        TSK
    }
}
