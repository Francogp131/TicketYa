# P5 — Calidad, integración y documentación (Franco)

**Foco:** que lo que hacen los otros cuatro funcione junto y esté demostrado.
**Tareas OpenSpec:** 5.1, 5.2, 5.11, 6.1, 6.2, 6.3, 6.4 y apoyo en 5.5, 5.7, 5.9.

## 1. Rol

No sos dueño de un microservicio. Sos el integrador:

- Mantenés el contrato y el índice de documentación.
- Preparás los datos de prueba y la colección Postman.
- Corrés los flujos E2E por el gateway.
- Exigís evidencia antes de marcar una tarea como terminada.
- Revisás los PR de los cuatro.

Regla: **no editás código de un servicio sin acordarlo con su dueño**. Si encontrás un bug, abrís un issue o un PR pequeño y lo coordinás.

## 2. Tareas

### 5.1 Datos de prueba (seed)

- Migraciones Flyway `V2__seed.sql` (o `R__seed.sql` si es repetible) en catalog, inventory y orders.
- Datos mínimos: 2 lugares, sectores numerados y no numerados, 3 eventos publicados con precios, inventario consistente.
- Verificar que se puedan consultar por API después de `docker compose up -d` + levantar servicios.
- No inventar datos que contradigan los snapshots entre servicios.

### 5.2 Colección Postman

- `postman/TicketYa.postman_collection.json` con los flujos: publicar, buscar, reservar, comprar aprobada, compra rechazada, reserva vencida, devolución y cancelación.
- Variables de colección: `gateway`, `organizadorId`, `clienteId`, `adminId`, IDs que devuelven los requests.
- Assertions en cada request: status, `code` de error, campos clave.
- Ejecutable con Newman contra el entorno local.

### 5.11 Evidencia de pruebas

- `docs/pruebas.md` con estrategia, comandos y resultados.
- Pegar la salida de la prueba de concurrencia (3.7) y del E2E.
- Los comandos deben ejecutarse tal cual, sin edición manual.

### 6.1 Suite completa

- `mvn test` en los cuatro servicios.
- Verificar que el CI esté verde y que no haya tests omitidos.

### 6.2 E2E por el gateway

- Seis flujos: publicar, buscar, reservar, comprar, devolver y cancelar.
- Ejecutar con Postman/Newman y guardar la salida como evidencia.
- Todo pasa por `http://localhost:8080`; nunca por los puertos internos.

### 6.3 Checklist

- Completar `docs/checklist-entrega-1.md` al 100 %.
- Cada ítem con evidencia enlazada en el PR o en `docs/`.

### 6.4 Cierre

- Probar un clone limpio.
- Crear el tag `entrega-1`.
- Verificar accesos y entregar el enlace del repo.

### Apoyo

- **5.5 ER por servicio:** revisar que coincida con las migraciones Flyway.
- **5.7 `docs/arquitectura.md`:** revisar coherencia con `design.md`.
- **5.9 índice y ADRs:** mantener `docs/README.md` y `docs/decisiones.md`.

## 3. Rutina diaria

1. Revisar `tasks.md` y el tablero de PRs.
2. Correr el smoke test del entorno (bases + servicios + gateway).
3. Revisar que cada PR tenga tests, contrato y documentación.
4. Actualizar `docs/README.md` con el estado real.
5. Al cerrar el día, dejar registrado qué falta y quién lo hace.

## 4. Criterio para aprobar un PR

- [ ] La tarea está marcada en `tasks.md`.
- [ ] Tests pasan y cubren el caso de error.
- [ ] Endpoint en `docs/api-contract.md` y OpenAPI.
- [ ] README del servicio actualizado.
- [ ] Sin secretos ni archivos generados.
- [ ] CI verde.
- [ ] Evidencia en el cuerpo del PR.

## 5. Riesgos que vigilás

- Que inventory no sea realmente atómico (probar concurrencia, no confiar en el código).
- Que la idempotencia se pruebe solo secuencial y no concurrente.
- Que los servicios se llamen directo en lugar de por el gateway en las pruebas.
- Que la documentación se escriba al final y no acompañe el código.
- Que los seeds y los snapshots entre servicios queden inconsistentes.
