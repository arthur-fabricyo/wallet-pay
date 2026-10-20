package ElFabrica.Wallet_pay.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

class ApiAccessDeniedHandlerTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldWriteForbiddenAsApiError() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "req-403");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(JsonMapper.builder().build())
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("negado"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"ACCESS_DENIED\"")
                .contains("\"message\":\"Acesso negado\"")
                .contains("\"requestId\":\"req-403\"");
    }
}
