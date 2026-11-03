## ADDED Requirements

### Requirement: Registro de lugares y sectores
El sistema SHALL permitir a un administrador crear lugares con nombre, dirección y coordenadas, y definir sectores numerados (filas y asientos) o no numerados (capacidad máxima).

#### Scenario: Crear un lugar válido
- **WHEN** un administrador crea un lugar con nombre, dirección, latitud y longitud válidos
- **THEN** el lugar se guarda con un ID y se responde 201

#### Scenario: Usuario sin permiso
- **WHEN** un usuario que no es administrador intenta crear un lugar
- **THEN** se responde 403

#### Scenario: Coordenadas fuera de rango
- **WHEN** se crea un lugar con latitud fuera de [-90, 90] o longitud fuera de [-180, 180]
- **THEN** se responde 400

#### Scenario: Sector no numerado con capacidad inválida
- **WHEN** se crea un sector no numerado con capacidad menor o igual a 0
- **THEN** se responde 400

#### Scenario: Sector numerado genera asientos
- **WHEN** se crea un sector numerado con filas y asientos por fila
- **THEN** se generan los asientos identificados por fila y número

#### Scenario: Sector numerado con dimensiones inválidas
- **WHEN** se crea un sector numerado con filas o asientos por fila menores o iguales a 0
- **THEN** se responde 400

### Requirement: Consulta de lugares
El sistema SHALL permitir listar los lugares y consultar su detalle con nombre, dirección, ubicación y sectores.

#### Scenario: Listado de lugares
- **WHEN** existen lugares y se consulta el listado
- **THEN** se responde 200 con nombre, dirección y ubicación de cada lugar

#### Scenario: Detalle de lugar inexistente
- **WHEN** se consulta el detalle de un lugar con un ID inexistente
- **THEN** se responde 404

### Requirement: Eventos en borrador
El sistema SHALL permitir a un organizador crear eventos en estado `BORRADOR` con nombre, descripción, tipo, participantes, fecha, hora y lugar.

#### Scenario: Crear evento válido
- **WHEN** un organizador crea un evento con datos válidos
- **THEN** el evento queda en estado `BORRADOR` y asociado a ese organizador

#### Scenario: Fecha en el pasado
- **WHEN** se crea un evento con una fecha pasada
- **THEN** se responde 400

#### Scenario: Tipo de evento inválido
- **WHEN** se crea un evento con un tipo que no pertenece al catálogo permitido
- **THEN** se responde 400

### Requirement: Precios por sector
El sistema SHALL permitir al organizador definir el precio de cada sector del lugar para su evento, solo mientras el evento esté en borrador.

#### Scenario: Asignar precio válido
- **WHEN** el organizador asigna un precio mayor o igual a 0 a un sector del lugar del evento
- **THEN** el precio queda guardado para ese evento

#### Scenario: Sector ajeno al lugar del evento
- **WHEN** el organizador intenta asignar precio a un sector que no pertenece al lugar del evento
- **THEN** se responde 400

#### Scenario: Cambiar precio de un evento publicado
- **WHEN** se intenta cambiar el precio de un evento ya publicado
- **THEN** se responde 409

### Requirement: Publicación de eventos
El sistema SHALL permitir publicar un evento solo si todos sus sectores tienen precio, y SHALL habilitar su inventario en inventory-service.

#### Scenario: Publicar evento completo
- **WHEN** el organizador publica un evento en borrador con todos los sectores con precio
- **THEN** el evento pasa a `PUBLICADO` y se habilita su inventario

#### Scenario: Publicar evento incompleto
- **WHEN** el organizador intenta publicar un evento con algún sector sin precio
- **THEN** se responde 409 indicando los sectores faltantes

#### Scenario: Publicar evento de otro organizador
- **WHEN** un organizador intenta publicar un evento que no le pertenece
- **THEN** se responde 403

### Requirement: Suspensión y cancelación por administrador
El sistema SHALL permitir a un administrador suspender o cancelar cualquier evento no finalizado.

#### Scenario: Cancelar evento publicado
- **WHEN** un administrador cancela un evento publicado
- **THEN** el evento pasa a `CANCELADO` y se inicia el reintegro de sus entradas vendidas

#### Scenario: Cancelar evento finalizado
- **WHEN** se intenta cancelar un evento `FINALIZADO`
- **THEN** se responde 409

#### Scenario: Organizador intenta cancelar un evento ajeno
- **WHEN** un organizador intenta cancelar un evento que no le pertenece
- **THEN** se responde 403

### Requirement: Estados coherentes del evento
El sistema SHALL mantener los estados borrador, publicado, agotado, cancelado y finalizado, rechazando transiciones no válidas.

#### Scenario: Evento agotado
- **WHEN** el inventario de un evento publicado se agota
- **THEN** el evento pasa a `AGOTADO`

#### Scenario: Se libera un lugar de un evento agotado
- **WHEN** se libera un lugar de un evento `AGOTADO`
- **THEN** el evento vuelve a `PUBLICADO`

#### Scenario: Evento cuya fecha ya pasó
- **WHEN** un evento publicado alcanza su fecha y hora de finalización
- **THEN** el evento pasa a `FINALIZADO`

#### Scenario: Transición de estado inválida
- **WHEN** se intenta una transición que no está permitida por la máquina de estados
- **THEN** se responde 409
