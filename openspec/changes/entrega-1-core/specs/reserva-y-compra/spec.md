## ADDED Requirements

### Requirement: Reserva temporal de lugares
El sistema SHALL reservar temporalmente los lugares elegidos por un cliente durante 10 minutos, de forma atómica (todo o nada), tanto para asientos numerados como para zonas no numeradas.

#### Scenario: Reservar asiento libre
- **WHEN** un cliente reserva un asiento libre
- **THEN** el asiento queda `RESERVADO` para ese cliente con vencimiento a los 10 minutos

#### Scenario: Asiento ya reservado o vendido
- **WHEN** un cliente intenta reservar un asiento reservado o vendido
- **THEN** se responde 409

#### Scenario: Reserva múltiple parcialmente no disponible
- **WHEN** un cliente reserva varios asientos y alguno no está libre
- **THEN** no se reserva ninguno y se responde 409

#### Scenario: Zona con disponibilidad suficiente
- **WHEN** un cliente reserva N lugares de una zona con al menos N disponibles
- **THEN** la disponibilidad baja en N y los lugares quedan reservados

#### Scenario: Zona sin disponibilidad suficiente
- **WHEN** un cliente reserva N lugares de una zona con menos de N disponibles
- **THEN** se responde 409 y la disponibilidad no cambia

### Requirement: Límite de entradas por cliente
El sistema SHALL limitar la cantidad de entradas por cliente para un mismo evento, contando entradas vendidas y reservas vigentes.

#### Scenario: Superar el límite
- **WHEN** un cliente con 3 entradas de un evento con límite 4 intenta reservar 2 más
- **THEN** se responde 422

#### Scenario: Reservar dentro del límite
- **WHEN** un cliente con 1 entrada de un evento con límite 4 intenta reservar 2 más
- **THEN** la reserva se crea correctamente

### Requirement: Liberación de lugares
El sistema SHALL liberar los lugares cuando la reserva vence, el pago es rechazado o el cliente cancela la compra.

#### Scenario: Reserva vencida
- **WHEN** vence el tiempo de una reserva sin pago
- **THEN** los lugares vuelven a estar libres

#### Scenario: Pago sobre reserva vencida
- **WHEN** el cliente intenta pagar una reserva vencida
- **THEN** se responde 410 y no se emiten entradas

#### Scenario: Pago rechazado
- **WHEN** la pasarela rechaza el pago
- **THEN** la orden pasa a `RECHAZADA` y los lugares se liberan

### Requirement: Inicio de compra
El sistema SHALL crear una orden en estado `PENDIENTE_PAGO` a partir de una reserva vigente del cliente, con el detalle de ítems y el total calculado en el servidor.

#### Scenario: Crear orden desde una reserva vigente
- **WHEN** un cliente inicia la compra de una reserva vigente y propia
- **THEN** se crea una orden `PENDIENTE_PAGO` con los ítems y el total, y se responde 201

#### Scenario: Reserva inexistente
- **WHEN** un cliente inicia la compra con un ID de reserva inexistente
- **THEN** se responde 404

#### Scenario: Reserva de otro cliente
- **WHEN** un cliente inicia la compra con una reserva de otro cliente
- **THEN** se responde 403

### Requirement: Compra y emisión de entradas
El sistema SHALL, ante un pago aprobado por la pasarela, marcar la orden como `PAGADA`, marcar los lugares como vendidos y emitir una entrada con QR único por lugar, registrando el detalle de la compra y el medio de pago.

#### Scenario: Pago aprobado
- **WHEN** la pasarela mock aprueba el pago de una reserva vigente
- **THEN** la orden pasa a `PAGADA`, los lugares quedan `VENDIDOS` y se emite una entrada con QR único por lugar

#### Scenario: Registro del medio de pago
- **WHEN** una orden queda `PAGADA`
- **THEN** la orden registra el medio de pago utilizado y el detalle de los ítems comprados

### Requirement: Compensación ante fallos posteriores al pago
El sistema SHALL compensar los pasos ya realizados cuando falle la confirmación de la reserva o la emisión de entradas después de un pago aprobado.

#### Scenario: Falla al confirmar la reserva
- **WHEN** la pasarela aprueba el pago pero inventory-service no confirma la reserva
- **THEN** se solicita el reintegro, se liberan los lugares y la orden no queda `PAGADA`

#### Scenario: Falla al emitir entradas
- **WHEN** falla la emisión de entradas después de confirmar la reserva
- **THEN** se liberan los lugares, se solicita el reintegro y no queda ninguna entrada válida emitida

### Requirement: No doble venta
El sistema MUST garantizar que un mismo asiento o lugar nunca se venda dos veces, aun con solicitudes simultáneas y con varias instancias del servicio.

#### Scenario: Reserva concurrente del mismo asiento
- **WHEN** 100 clientes intentan reservar el mismo asiento al mismo tiempo
- **THEN** exactamente una reserva tiene éxito y las demás reciben 409

#### Scenario: Último lugar de una zona
- **WHEN** varios clientes intentan reservar el último lugar de una zona simultáneamente
- **THEN** solo uno lo obtiene

#### Scenario: Varias instancias del servicio
- **WHEN** dos instancias de inventory-service procesan la misma solicitud de reserva a la vez
- **THEN** solo una confirma la reserva y la otra recibe 409

### Requirement: Idempotencia de la compra
El sistema SHALL tratar de forma idempotente los reintentos de una compra identificados por el encabezado `Idempotency-Key`.

#### Scenario: Reintento con la misma clave
- **WHEN** se repite una solicitud de compra con la misma `Idempotency-Key` y el mismo cuerpo
- **THEN** se devuelve el resultado original sin crear otra orden ni otro cobro

#### Scenario: Misma clave con cuerpo distinto
- **WHEN** se envía la misma `Idempotency-Key` con un cuerpo distinto
- **THEN** se responde 422

#### Scenario: Reintentos concurrentes con la misma clave
- **WHEN** dos solicitudes concurrentes usan la misma `Idempotency-Key` y el mismo cuerpo
- **THEN** se crea una sola orden y ambas reciben el mismo resultado

### Requirement: Consulta de entradas y órdenes propias
El sistema SHALL mostrar al cliente solo sus propias entradas y órdenes.

#### Scenario: Listado de entradas propias
- **WHEN** un cliente consulta sus entradas
- **THEN** recibe solo las suyas con evento, sector, asiento y QR

#### Scenario: Entrada ajena
- **WHEN** un cliente consulta una entrada de otro cliente
- **THEN** se responde 403
