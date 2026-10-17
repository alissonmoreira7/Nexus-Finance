package com.dev.nexusfinance.dto;

import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserProfileImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de {@link UserView}.
 *
 * <p>A montagem do data URI tem tres condicoes encadeadas, e so o caminho feliz
 * estava coberto. As demais sao justamente os estados degradados que aparecem
 * quando a linha da foto existe mas o blob nao veio.
 */
class UserViewTest {
    private User ana;

    @BeforeEach
    void setUp() {
        ana = UserFixture.ana();
    }

    @Test
    @DisplayName("monta a view sem foto quando nao ha imagem")
    void semImagem() {
        UserView view = UserView.from(ana, null);

        assertThat(view.id()).isEqualTo(ana.getIdUser());
        assertThat(view.name()).isEqualTo("Ana Silva");
        assertThat(view.email()).isEqualTo("ana@email.com");
        assertThat(view.cpf()).isEqualTo("12345678901");
        assertThat(view.profileImage()).isNull();
    }

    @Test
    @DisplayName("monta o data URI com o content type e o base64 dos bytes")
    void comImagemValida() {
        byte[] bytes = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        UserProfileImage imagem = imagem("image/jpeg", bytes);

        assertThat(UserView.from(ana, imagem).profileImage())
            .isEqualTo("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    @DisplayName("ignora imagem cujo blob e nulo")
    void imagemComDadosNulos() {
        assertThat(UserView.from(ana, imagem("image/png", null)).profileImage()).isNull();
    }

    @Test
    @DisplayName("ignora imagem cujo blob esta vazio")
    void imagemComDadosVazios() {
        assertThat(UserView.from(ana, imagem("image/png", new byte[0])).profileImage()).isNull();
    }

    @Test
    @DisplayName("a view nao expoe senha, papel nem plano")
    void naoExpoeCamposSensiveis() {
        assertThat(UserView.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactly("id", "name", "email", "cpf", "profileImage");
    }

    private UserProfileImage imagem(String contentType, byte[] dados) {
        UserProfileImage imagem = new UserProfileImage();
        imagem.setUserId(ana.getIdUser());
        imagem.setContentType(contentType);
        imagem.setData(dados);
        return imagem;
    }
}
