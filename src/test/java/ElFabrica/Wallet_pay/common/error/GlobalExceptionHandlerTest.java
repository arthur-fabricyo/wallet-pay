package ElFabrica.Wallet_pay.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.config.SecurityConfig;
import ElFabrica.Wallet_pay.support.TestJwt;
import ElFabrica.Wallet_pay.transaction.service.InsufficientBalanceException;
import ElFabrica.Wallet_pay.user.service.InvalidDocumentException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.ProbeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.ProbeController.class})
@TestPropertySource(properties = "wallet-pay.auth.jwt-secret=" + TestJwt.SECRET)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    private String auth() {
        return TestJwt.bearer(UUID.randomUUID());
    }

    @Test
    void shouldMapBusinessExceptionUsingItsErrorCode() throws Exception {
        mockMvc.perform(get("/probe/business").header("Authorization", auth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").value("Saldo insuficiente"))
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @Test
    void shouldKeepBusinessExceptionFields() throws Exception {
        mockMvc.perform(get("/probe/document").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"))
                .andExpect(jsonPath("$.fields.document").value("CPF invalido"));
    }

    @Test
    void shouldHideUnexpectedErrorDetails() throws Exception {
        mockMvc.perform(get("/probe/boom").header("Authorization", auth()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Erro interno, informe o requestId ao suporte"))
                .andExpect(content().string(not(containsString("detalhe interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void shouldMapDataIntegrityViolationWithoutConstraintName() throws Exception {
        mockMvc.perform(get("/probe/integrity").header("Authorization", auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Conflito de dados"))
                .andExpect(content().string(not(containsString("uk_users_email"))));
    }

    @Test
    void shouldMapMalformedJson() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Corpo da requisicao invalido"));
    }

    @Test
    void shouldMapUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .header("Authorization", auth())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void shouldMapParameterTypeMismatch() throws Exception {
        mockMvc.perform(get("/probe/param").param("count", "abc").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fields.count").value("Valor invalido"));
    }

    @Test
    void shouldMapMissingParameter() throws Exception {
        mockMvc.perform(get("/probe/param").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fields.count").value("Parametro obrigatorio"));
    }

    @Test
    void shouldMapMissingIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(get("/probe/idempotent").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key e obrigatorio"));
    }

    @Test
    void shouldMapOtherMissingHeader() throws Exception {
        mockMvc.perform(get("/probe/header").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fields.X-Probe").value("Header obrigatorio"));
    }

    @Test
    void shouldMapMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/probe/business").header("Authorization", auth()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldMapUnknownRoute() throws Exception {
        mockMvc.perform(get("/probe/nao-existe").header("Authorization", auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Rota nao encontrada"));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/business")
        void business() {
            throw new InsufficientBalanceException("Saldo insuficiente");
        }

        @GetMapping("/document")
        void document() {
            throw new InvalidDocumentException("CPF invalido");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("select * from users -- detalhe interno");
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("violates unique constraint uk_users_email");
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        void body(@RequestBody ProbeBody body) {
        }

        @GetMapping("/param")
        void param(@RequestParam int count) {
        }

        @GetMapping("/header")
        void header(@RequestHeader("X-Probe") String value) {
        }

        @GetMapping("/idempotent")
        void idempotent(@RequestHeader("Idempotency-Key") String value) {
        }

        record ProbeBody(String value) {
        }
    }
}
