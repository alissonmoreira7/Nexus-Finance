package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.dto.UserView;
import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.AuthService;
import com.dev.nexusfinance.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UserControllerTest {
    private static final String ATRIBUTO_USUARIO = "authenticatedUserId";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    @MockitoBean
    private AuthService authService;

    private User ana;

    @BeforeEach
    void setUp() {
        ana = UserFixture.ana();
    }

    @Test
    @DisplayName("POST /users devolve 201 com a view do usuario")
    void cadastroDevolve201() throws Exception {
        when(service.create(any(User.class))).thenReturn(ana);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana Silva","cpf":"12345678901",
                     "email":"ana@email.com","password":"senha-segura"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(ana.getIdUser().toString()))
            .andExpect(jsonPath("$.name").value("Ana Silva"))
            .andExpect(jsonPath("$.email").value("ana@email.com"));
    }

    /**
     * S-02: a resposta do cadastro nao pode carregar o hash da senha. O campo tem
     * {@code @JsonProperty(WRITE_ONLY)} na entidade, mas devolver {@link UserView}
     * em vez da entidade torna o vazamento impossivel por construcao, e nao
     * dependente de uma anotacao que alguem pode remover.
     */
    @Test
    @DisplayName("S-02: resposta do cadastro nao expoe senha, papel nem plano")
    void cadastroNaoExpoeCamposSensiveis() throws Exception {
        when(service.create(any(User.class))).thenReturn(ana);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana Silva","cpf":"12345678901",
                     "email":"ana@email.com","password":"senha-segura"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.plan").doesNotExist())
            .andExpect(jsonPath("$.authorities").doesNotExist());
    }

    @Test
    @DisplayName("POST /users propaga 400 de validacao do service")
    void cadastroInvalidoDevolve400() throws Exception {
        when(service.create(any(User.class)))
            .thenThrow(new IllegalArgumentException("CPF deve conter 11 dígitos"));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana","cpf":"123","email":"ana@email.com","password":"senha-segura"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("CPF deve conter 11 dígitos"));
    }

    @Test
    @DisplayName("GET /users/me devolve o perfil do usuario autenticado")
    void perfilDoUsuarioAutenticado() throws Exception {
        when(service.getProfile(ana.getIdUser()))
            .thenReturn(new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null));

        mockMvc.perform(get("/api/v1/users/me").requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ana Silva"))
            .andExpect(jsonPath("$.profileImage").doesNotExist());

        verify(service).getProfile(ana.getIdUser());
    }

    @Test
    @DisplayName("GET /users/me usa o id do token, nunca um id vindo do cliente")
    void perfilIgnoraIdDoCliente() throws Exception {
        UUID idDeOutro = UUID.randomUUID();
        when(service.getProfile(any())).thenReturn(
            new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null));

        mockMvc.perform(get("/api/v1/users/me")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("userId", idDeOutro.toString()))
            .andExpect(status().isOk());

        verify(service).getProfile(ana.getIdUser());
    }

    @Test
    @DisplayName("PUT /users/me atualiza perfil com foto multipart")
    void atualizaPerfilComFoto() throws Exception {
        MockMultipartFile foto = new MockMultipartFile(
            "photo", "ana.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(service.updateProfile(eq(ana.getIdUser()), any(), any(), any(), any(), any(), eq(false)))
            .thenReturn(new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                "data:image/jpeg;base64,abc"));

        mockMvc.perform(multipart("/api/v1/users/me")
                .file(foto)
                .param("name", "Ana Silva")
                .param("email", "ana@email.com")
                .param("cpf", "12345678901")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileImage").value("data:image/jpeg;base64,abc"));
    }

    @Test
    @DisplayName("PUT /users/me funciona sem foto, passando dados nulos de imagem")
    void atualizaPerfilSemFoto() throws Exception {
        when(service.updateProfile(eq(ana.getIdUser()), any(), any(), any(), isNull(), isNull(), eq(false)))
            .thenReturn(new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null));

        mockMvc.perform(multipart("/api/v1/users/me")
                .param("name", "Ana Silva")
                .param("email", "ana@email.com")
                .param("cpf", "12345678901")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk());

        verify(service).updateProfile(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
            null, null, false);
    }

    @Test
    @DisplayName("PUT /users/me trata arquivo vazio como ausencia de foto")
    void arquivoVazioEhTratadoComoAusencia() throws Exception {
        MockMultipartFile vazio = new MockMultipartFile("photo", "vazio.jpg", "image/jpeg", new byte[0]);
        when(service.updateProfile(any(), any(), any(), any(), isNull(), any(), eq(false)))
            .thenReturn(new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null));

        mockMvc.perform(multipart("/api/v1/users/me")
                .file(vazio)
                .param("name", "Ana Silva")
                .param("email", "ana@email.com")
                .param("cpf", "12345678901")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk());

        verify(service).updateProfile(eq(ana.getIdUser()), any(), any(), any(),
            isNull(), eq("image/jpeg"), eq(false));
    }

    @Test
    @DisplayName("PUT /users/me repassa removeImage")
    void repassaRemoveImage() throws Exception {
        when(service.updateProfile(any(), any(), any(), any(), any(), any(), eq(true)))
            .thenReturn(new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null));

        mockMvc.perform(multipart("/api/v1/users/me")
                .param("name", "Ana Silva")
                .param("email", "ana@email.com")
                .param("cpf", "12345678901")
                .param("removeImage", "true")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk());

        verify(service).updateProfile(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
            null, null, true);
    }

    @Test
    @DisplayName("PUT /users/me devolve 400 quando falta um campo obrigatorio")
    void faltaCampoObrigatorioDevolve400() throws Exception {
        mockMvc.perform(multipart("/api/v1/users/me")
                .param("name", "Ana Silva")
                .param("email", "ana@email.com")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /users/me propaga 400 de foto invalida")
    void fotoInvalidaDevolve400() throws Exception {
        MockMultipartFile falsa = new MockMultipartFile(
            "photo", "virus.jpg", "image/jpeg", "MZ executavel".getBytes());
        when(service.updateProfile(any(), any(), any(), any(), any(), any(), eq(false)))
            .thenThrow(new IllegalArgumentException("Envie uma foto JPEG, PNG ou WebP válida"));

        mockMvc.perform(multipart("/api/v1/users/me")
                .file(falsa)
                .param("name", "Ana Silva")
                .param("email", "ana@email.com")
                .param("cpf", "12345678901")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Envie uma foto JPEG, PNG ou WebP válida"));
    }

    @Test
    @DisplayName("DELETE /users/me devolve 204")
    void exclusaoDevolve204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me").requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isNoContent());

        verify(service).delete(ana.getIdUser());
    }

    @Test
    @DisplayName("DELETE /users/me propaga 404 de usuario inexistente")
    void exclusaoDeInexistenteDevolve404() throws Exception {
        doThrow(new ResourceNotFoundException("Usuário não encontrado"))
            .when(service).delete(ana.getIdUser());

        mockMvc.perform(delete("/api/v1/users/me").requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isNotFound());
    }

    /**
     * BUG-03: o usuario tem conta ou planejamento, a FK e {@code ON DELETE
     * RESTRICT}, e o banco recusa a exclusao. Antes do handler de
     * {@code DataIntegrityViolationException} isso era 500 com stack trace.
     */
    @Test
    @DisplayName("BUG-03: DELETE /users/me devolve 409 quando o usuario tem dados vinculados")
    void exclusaoComDadosVinculadosDevolve409() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("FK constraint fails"))
            .when(service).delete(ana.getIdUser());

        mockMvc.perform(delete("/api/v1/users/me").requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("A operação conflita com dados já existentes"));
    }
}
