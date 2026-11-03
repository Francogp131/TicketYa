## ADDED Requirements

### Requirement: Solicitud de devolución
El sistema SHALL permitir al cliente solicitar la devolución de una entrada válida y propia hasta 72 horas antes del evento.

#### Scenario: Dentro del plazo
- **WHEN** el cliente solicita la devolución de una entrada válida de un evento a más de 72 horas
- **THEN** se crea una solicitud en estado `PENDIENTE`

#### Scenario: Fuera de plazo
- **WHEN** el cliente solicita la devolución de un evento a 72 horas o menos
- **THEN** se responde 422

#### Scenario: Entrada ya usada o ya devuelta
- **WHEN** el cliente solicita la devolución de una entrada utilizada o ya devuelta
- **THEN** se responde 409

#### Scenario: Entrada de otro cliente
- **WHEN** un cliente solicita la devolución de una entrada que no le pertenece
- **THEN** se responde 403

### Requirement: Resolución por administrador
El sistema SHALL permitir a un administrador aprobar o rechazar solicitudes pendientes. Al aprobar, SHALL liberar el lugar, invalidar la entrada y solicitar el reintegro a la pasarela.

#### Scenario: Aprobar devolución
- **WHEN** el administrador aprueba una solicitud pendiente
- **THEN** el lugar se libera, la entrada se invalida y se solicita el reintegro

#### Scenario: Rechazar devolución
- **WHEN** el administrador rechaza una solicitud pendiente
- **THEN** la entrada sigue siendo válida

#### Scenario: Solicitud ya resuelta
- **WHEN** se intenta resolver una solicitud ya resuelta
- **THEN** se responde 409

#### Scenario: Usuario sin rol administrador
- **WHEN** un usuario que no es administrador intenta resolver una solicitud
- **THEN** se responde 403

### Requirement: Reintegro por cancelación de evento
El sistema SHALL pasar a reintegro todas las entradas vendidas de un evento cancelado.

#### Scenario: Evento cancelado
- **WHEN** un evento pasa a `CANCELADO`
- **THEN** todas sus entradas vendidas pasan a `EN_REINTEGRO` y se solicita el reintegro de cada una

#### Scenario: Evento sin entradas vendidas
- **WHEN** se cancela un evento sin entradas vendidas
- **THEN** el evento queda `CANCELADO` sin solicitudes de reintegro
