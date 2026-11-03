## ADDED Requirements

### Requirement: Búsqueda por múltiples criterios
El sistema SHALL permitir buscar eventos por tipo, organizador, participante, ciudad y rango de fechas, combinando los criterios con AND y devolviendo solo eventos publicados o agotados.

#### Scenario: Criterios combinados
- **WHEN** el cliente busca por tipo "recital" y ciudad "Tandil"
- **THEN** recibe solo eventos que cumplen ambos criterios

#### Scenario: Sin resultados
- **WHEN** ningún evento coincide con los criterios
- **THEN** se responde 200 con una lista vacía

#### Scenario: Rango de fechas inválido
- **WHEN** la fecha de inicio es posterior a la fecha de fin
- **THEN** se responde 400

### Requirement: Orden y paginación
El sistema SHALL permitir ordenar por fecha, precio o relevancia y paginar los resultados, informando el total.

#### Scenario: Orden por precio
- **WHEN** el cliente busca ordenando por precio con una página de tamaño 10
- **THEN** recibe hasta 10 eventos ordenados por precio mínimo, junto con el total

#### Scenario: Orden por fecha
- **WHEN** el cliente busca ordenando por fecha
- **THEN** recibe los eventos ordenados cronológicamente y paginados, junto con el total

#### Scenario: Orden no soportado
- **WHEN** se solicita un criterio de orden desconocido
- **THEN** se responde 400

### Requirement: Detalle de evento
El sistema SHALL mostrar al cliente el detalle de eventos publicados o agotados, con sectores, precios y disponibilidad, y SHALL ocultar los eventos en borrador.

#### Scenario: Detalle de evento publicado
- **WHEN** un cliente consulta un evento publicado o agotado
- **THEN** se responde 200 con sus datos, sectores, precios y disponibilidad

#### Scenario: Evento en borrador
- **WHEN** un cliente consulta un evento en borrador
- **THEN** se responde 404
