package com.dev.nexusfinance;

import com.dev.nexusfinance.services.PasswordService;
import com.dev.nexusfinance.services.TransactionEngineService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NexusFinanceApplicationTests {
    @Test
    void protegeEValidaSenha() {
        PasswordService service = new PasswordService();
        String hash = service.hash("senha-segura");
        assertNotEquals("senha-segura", hash);
        assertTrue(service.matches("senha-segura", hash));
        assertFalse(service.matches("senha-errada", hash));
    }

    @Test
    void sanitizaDescricaoBancaria() {
        TransactionEngineService service = new TransactionEngineService(null, null, null);
        assertEquals("UBER EATS", service.cleanDescription("COMPRA VISA*1234 UBER EATS SAO PAULO"));
    }
}
