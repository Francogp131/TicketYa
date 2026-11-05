package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class LimitePorClienteTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private LimitePorCliente limitePorCliente;
    private final UUID eventoId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        limitePorCliente = new LimitePorCliente(jdbcTemplate);
    }

    @Test
    void tresEntradasYDosMasConLimiteCuatroResponde422() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class), any(), any(), any(), any())).thenReturn(3);

        assertThatThrownBy(() -> limitePorCliente.verificar(eventoId, clienteId, 4, 2))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getCode()).isEqualTo("CLIENT_LIMIT_EXCEEDED");
                });
        assertLockAntesDelConteo();
    }

    @Test
    void unaEntradaYDosMasConLimiteCuatroPasa() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class), any(), any(), any(), any())).thenReturn(1);

        limitePorCliente.verificar(eventoId, clienteId, 4, 2);

        assertLockAntesDelConteo();
    }

    private void assertLockAntesDelConteo() {
        ArgumentCaptor<String> lock = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(lock.capture(), any(ResultSetExtractor.class),
                eq(eventoId.toString()), eq(clienteId.toString()));
        assertThat(lock.getValue()).isEqualTo(LimitePorCliente.LOCK);

        InOrder orden = inOrder(jdbcTemplate);
        orden.verify(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), any(), any());
        orden.verify(jdbcTemplate).queryForObject(eq(LimitePorCliente.CONTAR), eq(Number.class),
                eq(eventoId), eq(clienteId), eq(eventoId), eq(clienteId));
    }
}
