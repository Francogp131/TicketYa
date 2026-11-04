package ar.edu.unicen.ticketya.orders.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "entrada")
public class Entrada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "orden_id", nullable = false)
    private UUID ordenId;

    @Column(name = "orden_item_id", nullable = false)
    private UUID ordenItemId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "evento_nombre", nullable = false, length = 200)
    private String eventoNombre;

    @Column(name = "evento_fecha", nullable = false)
    private OffsetDateTime eventoFecha;

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(name = "asiento_id")
    private UUID asientoId;

    @Column(name = "qr_code", nullable = false, unique = true)
    private UUID qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEntrada estado = EstadoEntrada.VALIDA;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(UUID ordenId) {
        this.ordenId = ordenId;
    }

    public UUID getOrdenItemId() {
        return ordenItemId;
    }

    public void setOrdenItemId(UUID ordenItemId) {
        this.ordenItemId = ordenItemId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public void setClienteId(UUID clienteId) {
        this.clienteId = clienteId;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public String getEventoNombre() {
        return eventoNombre;
    }

    public void setEventoNombre(String eventoNombre) {
        this.eventoNombre = eventoNombre;
    }

    public OffsetDateTime getEventoFecha() {
        return eventoFecha;
    }

    public void setEventoFecha(OffsetDateTime eventoFecha) {
        this.eventoFecha = eventoFecha;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public void setSectorId(UUID sectorId) {
        this.sectorId = sectorId;
    }

    public UUID getAsientoId() {
        return asientoId;
    }

    public void setAsientoId(UUID asientoId) {
        this.asientoId = asientoId;
    }

    public UUID getQrCode() {
        return qrCode;
    }

    public void setQrCode(UUID qrCode) {
        this.qrCode = qrCode;
    }

    public EstadoEntrada getEstado() {
        return estado;
    }

    public void setEstado(EstadoEntrada estado) {
        this.estado = estado;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
