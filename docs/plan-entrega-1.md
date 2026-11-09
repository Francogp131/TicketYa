# Plan de trabajo — Entrega 1 (2 al 10 de noviembre de 2026)

Plan día por día para llegar a la Entrega 1 del TP TicketYa. El alcance funcional está en `openspec/changes/entrega-1-core/` y la trazabilidad completa en `docs/checklist-entrega-1.md`.

## 1. Resumen

- **Entrega 1:** core del sistema (publicación, búsqueda, compra con reserva temporal, no doble venta, devoluciones y cancelación).
- **Fecha límite:** martes 10 de noviembre de 2026.
- **Equipo:** 5 personas (una por servicio + calidad/integración), ver `CONTRIBUTING.md` y `docs/equipo/`.
- **Estrategia:** congelar contratos el Día 0, implementar en paralelo por servicio, integrar todos los días y congelar funcionalidad el domingo 8 para dedicar los últimos dos días a verificación y entrega.
- **Regla de oro:** ninguna tarea se marca como terminada sin la verificación que figura en `tasks.md`; ninguna funcionalidad entra después del domingo 8 a las 18:00.

## 2. Supuestos

- Dedicación intensiva durante los 9 días (el cronograma asume jornadas completas; si el equipo tiene menos horas, aplicar el plan de contingencia de la sección 8).
- El repositorio es público para lectura: `https://github.com/Francogp131/TicketYa` (rama `main`; escritura por colaboradores).
- Se trabaja en local; las bases se levantan con Docker y los servicios se ejecutan con Maven/IDE.
- La interfaz gráfica y todo lo marcado como Entrega 2 queda fuera de alcance.
- El equipo usa OpenSpec como fuente de verdad: primero se ajusta el spec, después el código.

## 3. Equipo y responsabilidades

| Persona | Nombre | Rol | Servicio / foco | Tareas OpenSpec |
|---|---|---|---|---|
| P1 | Iván | Catalog | Lugares, eventos, publicación, estados y búsqueda | 2.1 a 2.6 |
| P2 | Pablo | Inventory | Reservas, vencimiento, límite y no doble venta | 3.1 a 3.8 |
| P3 | Caro | Orders | Órdenes, pagos, QR, idempotencia, devoluciones y cancelación | 4.1 a 4.11 |
| P4 | Tiago | Gateway + infraestructura | Gateway, Docker, CI, diagramas de módulos/secuencia, README raíz | 1.1, 1.4 a 1.7, 5.3, 5.4, 5.6, 5.8, 5.9 |
| P5 | Franco | Calidad e integración | Datos de prueba, Postman, E2E, evidencia, índice y checklist | 5.1, 5.2, 5.11, 6.2, 6.3 |

P4 no es “el que documenta al final”: arranca el Día 0 con el gateway y el CI, y desde el Día 2 prepara el entorno para que los demás prueben. P5 es el dueño de la verificación: no implementa features, pero corre los flujos, mantiene Postman y exige evidencia antes de cerrar cada tarea. Todos revisan los PR de todos.

## 4. Cómo se usa OpenSpec

- Un solo change: `entrega-1-core` (propuesta, specs, diseño y tareas).
- Cada persona implementa su grupo de tareas y marca los checkboxes en `tasks.md` al cerrar el PR.
- Antes de codificar un cambio de comportamiento: se actualiza el spec y se valida con `openspec validate entrega-1-core`.
- Al final de cada día: `openspec status --change entrega-1-core` para ver el avance real.
- Al terminar la entrega: `openspec archive entrega-1-core` y sincronizar los specs principales.
- El diseño técnico está en `openspec/changes/entrega-1-core/design.md`; si el código se desvía, primero se actualiza el diseño y se avisa al grupo.

## 4.1 Estrategia de documentación

La documentación es un entregable, no un extra de último momento. Reglas del equipo:

- **Se escribe en el mismo PR que el código.** Un PR no se aprueba si el endpoint no está en el OpenAPI y en `docs/api-contract.md`.
- **`docs/README.md` es el índice y el tablero de estado.** P4 lo mantiene y revisa que no haya enlaces rotos.
- **Contrato en dos capas:** `docs/api-contract.md` es la vista humana (método, ruta, parámetros, códigos y ejemplos JSON); el OpenAPI es la fuente machine-readable. Se actualizan juntos.
- **Diagramas versionados en Mermaid** dentro de `docs/arquitectura.md` (módulos y secuencia) y `docs/modelo-datos.md` (ER por servicio). GitHub los renderiza sin exportar imágenes.
- **Cada servicio tiene su README** con responsabilidad, endpoints, modelo, variables y comandos de test; el dueño del servicio lo actualiza en el mismo PR.
- **Evidencia de pruebas en `docs/pruebas.md`:** comandos, resultados de la prueba de concurrencia y salida del E2E.
- **Decisiones en `docs/decisiones.md`:** ADR cortas con decisión, alternativas y consecuencias; `design.md` queda como documento técnico de referencia.
- **Cierre diario:** 10 minutos para actualizar docs, `tasks.md` y el índice; la documentación atrasada se detecta el mismo día, no el 9 de noviembre.

## 5. Ruta crítica

```
Contrato REST (1.3)
   → Bases + Flyway (1.5)
   → Inventario y reserva numerada (3.1, 3.2)
   → Orden desde reserva (4.2)
   → Pago aprobado + QR (4.4)
   → Prueba de concurrencia (3.7)
   → Devoluciones/cancelación (4.8, 4.9)
   → E2E y checklist (6.2, 6.3)
```

Si se atrasa un eslabón, se prioriza terminar la ruta crítica antes que cualquier extra (relevancia, documentación ampliada, Swagger detallado).

## 6. Cronograma día por día

### Día 0 — Lunes 2 de noviembre: fundaciones y contrato

**Objetivo:** que los cinco puedan trabajar en paralelo el martes sin bloquearse.

| Persona | Tareas | Entregable |
|---|---|---|
| Todos | Leer `design.md`, specs y `docs/features-y-user-stories.md`; resolver dudas en el sync | Alcance y contrato entendidos |
| P5 | 1.8 revisión de features/US, estrategia de pruebas y esqueleto de Postman | Cobertura verificada y plan de pruebas |
| P4 | 1.1 repo, 1.4 errores, 1.5 Docker/Postgres, 1.6 gateway, 1.7 CI (inicio) | Repo con PR de prueba y gateway ruteando |
| P1/P2/P3 | 1.2 esqueletos de sus servicios, 1.3 contrato REST, 1.8 revisión de features/US | Cuatro servicios con `/actuator/health` y contrato v1 |

**Verificación del día:** `mvn -q -DskipTests package` en los cuatro proyectos; `docker compose up -d` con las tres bases; `curl` al gateway contra los tres servicios; PR de prueba con el workflow de CI creado (verde a más tardar el Día 1).

**Riesgo:** que el contrato quede incompleto y se descubra el viernes. Mitigación: revisar la matriz US-01 a US-24 contra `docs/api-contract.md` antes de cerrar el día.

---

### Día 1 — Martes 3 de noviembre: CRUD de catálogo, inventario base y órdenes base

| Persona | Tareas | Entregable |
|---|---|---|
| P1 | 2.1, 2.2 | Lugares, sectores, asientos, eventos y precios funcionando con tests |
| P2 | 3.1, 3.2 | Modelo de inventario y reserva numerada atómica |
| P3 | 4.1, 4.2 | Modelo de órdenes y creación de orden desde reserva |
| P4 | 1.7 (cierre), 5.6 (inicio) y revisión de PRs | CI estable y README inicial |
| P5 | 5.1 (inicio), 5.2 (inicio) | Datos de prueba y colección Postman base |

**Verificación del día:** tests de 2.1/2.2 en verde; reserva de un asiento y de varios con rollback; orden `PENDIENTE_PAGO` con total correcto; PRs mergeados.

**Riesgo:** el alta de inventario desde catalog se define tarde. Mitigación: 2.4 se implementa el Día 2 y P4 ayuda con el cliente REST.

---

### Día 2 — Miércoles 4 de noviembre: estados, búsqueda y disponibilidad

| Persona | Tareas | Entregable |
|---|---|---|
| P1 | 2.3, 2.5 | Estados del evento y búsqueda multi-criterio con orden/paginación |
| P2 | 3.3, 3.4, 3.5 | Zonas, límite por cliente y vencimiento de reservas |
| P3 | 4.3, 4.4 | Pasarela mock y pago aprobado con QR |
| P4 | 2.4 (soporte), 5.3 (inicio) | Publicación→inventario integrada y diagrama de módulos |
| P5 | 5.1 (cierre), 5.2 | Seed completo y Postman de endpoints de catálogo |

**Verificación del día:** búsqueda combinada con orden y total; zona que descuenta N y rechaza sin disponibilidad; límite 422; reserva vencida libera; pago aprobado emite entradas; PRs mergeados.

**Riesgo:** la búsqueda con `relevancia` es ambigua. Mitigación: definir una heurística simple (fecha más próxima + disponibilidad) y documentarla en el contrato; no bloquear el resto.

---

### Día 3 — Jueves 5 de noviembre: compra completa y no doble venta

| Persona | Tareas | Entregable |
|---|---|---|
| P1 | 2.4 y avance de 2.6 | Publicación idempotente, callback de disponibilidad y OpenAPI de catalog |
| P2 | 3.6, 3.7 | Confirmación/liberación y prueba de concurrencia de 100 hilos |
| P3 | 4.5, 4.6, 4.7 | Pago rechazado/vencido, idempotencia y compensaciones |
| P4 | 5.3, 5.4 (borradores) y soporte de integración | Diagramas de módulos y secuencia |
| P5 | 5.2 (compra), 5.11 (inicio) | Postman del flujo de compra y primeras pruebas E2E |

**Verificación del día:** flujo reservar→orden→pagar aprobado→QR y reservar→pagar rechazado→liberar; `Idempotency-Key` repetida devuelve la misma orden; la prueba de concurrencia da exactamente 1 éxito; PRs mergeados.

**Riesgo:** deadlocks o tests de concurrencia inestables. Mitigación: locks ordenados por ID, reintentos acotados y Testcontainers en CI; P4 corre la prueba en una máquina limpia.

---

### Día 4 — Viernes 6 de noviembre: devoluciones, cancelación y documentación

| Persona | Tareas | Entregable |
|---|---|---|
| P1 | 2.6 (cierre) y soporte de devoluciones | OpenAPI de catalog revisado |
| P2 | 3.8 y soporte de 4.8 | OpenAPI de inventory y liberación por devolución probada |
| P3 | 4.8, 4.9, 4.10, 4.11 | Devoluciones, cancelación, consulta propia y OpenAPI de orders |
| P4 | 5.8, 5.9 (inicio) | Swagger revisado e índice de documentación iniciado |
| P5 | 5.2 (cierre), 5.11 | Postman completo y evidencia de pruebas |

**Verificación del día:** devolución dentro/fuera de plazo (422), aprobación que libera e invalida, rechazo que mantiene válida, cancelación con todas las entradas `EN_REINTEGRO`, consulta propia con 403 para ajenas; PRs mergeados.

**Riesgo:** la cancelación toca catalog, orders e inventory. Mitigación: contrato interno idempotente y prueba de integración con Testcontainers; si falta tiempo, se implementa primero la cancelación con refunds y luego los reintentos.

---

### Día 5 — Sábado 7 de noviembre: integración y documentación pesada

| Persona | Tareas | Entregable |
|---|---|---|
| Todos | 5.5, 5.6, 5.7, 5.9, 5.10, 5.11, 6.1 | ER por servicio, README raíz, arquitectura, índice/ADR, READMEs por servicio, evidencia y suite completa |
| P4 | Coordina 5.3/5.4/5.8/5.9 y revisa enlaces | Diagramas e índice definitivos |
| P5 | Lidera 5.11, 5.9 y 6.1; revisa 5.5 y corre la suite | `docs/pruebas.md` y checklist actualizado |
| P1/P2/P3 | Revisan el ER de su servicio y corrigen bugs de integración | ER validado contra Flyway |

**Verificación del día:** `mvn test` verde en los cuatro proyectos; README probado en un clone limpio; diagramas revisados por otra persona; ningún bug crítico abierto.

**Riesgo:** documentar el domingo es tarde. Mitigación: este día no se agrega funcionalidad; solo se documenta y se corrige.

---

### Día 6 — Domingo 8 de noviembre: E2E y congelamiento

| Persona | Tareas | Entregable |
|---|---|---|
| Todos | 6.2, 6.3 | E2E por gateway de los seis flujos y checklist al 100 % |
| P4 | Coordina la corrida y guarda evidencias | Salida de Postman/Newman y checklist completo |
| P5 | 6.2, 6.3: corre E2E y completa el checklist | Evidencia de los seis flujos |

**Verificación del día:** los seis flujos E2E pasan por el gateway; cada ítem de `docs/checklist-entrega-1.md` tiene evidencia; **congelamiento de funcionalidad a las 18:00**.

**Riesgo:** aparece un bug grande a último momento. Mitigación: desde este momento solo se aceptan fixes; cualquier feature nueva se rechaza.

---

### Día 7 — Lunes 9 de noviembre: release candidate y ensayo

| Persona | Tareas | Entregable |
|---|---|---|
| Todos | 6.4 (RC), revisión final de PRs y ensayo de demo | Tag `entrega-1-rc`, clone limpio verificado y demo ensayada |
| P4 | Verifica accesos, README y enlace del repo | Entrega lista para subir |
| P5 | Re-ejecuta E2E en clone limpio y valida documentación | Evidencia final adjunta |

**Verificación del día:** clone limpio + instrucciones del README funcionan; tag `entrega-1-rc` creado; demo de 5 minutos sin errores; checklist sin pendientes.

**Riesgo:** el entorno de la máquina de entrega difiere. Mitigación: probar en una máquina que no haya desarrollado el código.

---

### Día 8 — Martes 10 de noviembre: entrega

| Persona | Tareas | Entregable |
|---|---|---|
| Todos | 6.4 (cierre), tag `entrega-1` y submit | Repositorio entregado con el tag y el enlace |
| P4 | Crea el tag y verifica accesos | Enlace del repo listo |
| P5 | Cierra el checklist y adjunta evidencia | Checklist 100 % |

**Verificación del día:** el tag `entrega-1` apunta al commit entregado; el repo es accesible; el enlace se sube antes del cierre. **No se toca código este día salvo un blocker de último minuto.**

## 7. Rutina diaria y definición de terminado

**Cada día:**

1. Sync de 15 minutos: qué se hizo, qué sigue, qué bloquea.
2. Trabajo por tarea de OpenSpec, en la rama `feat/<servicio>-<tema>`.
3. PR a `main` con al menos una aprobación; se mergea el mismo día.
4. Al cerrar el PR: marcar el checkbox en `tasks.md` y dejar la evidencia de la verificación.
5. Actualizar `openspec status --change entrega-1-core`.

**Una tarea está terminada cuando:**

- El código compila y los tests de la tarea pasan.
- La verificación indicada en `tasks.md` se ejecutó y quedó registrada en el PR.
- El endpoint aparece en el OpenAPI (si aplica).
- Otro integrante revisó el PR.
- No quedan TODOs ni datos hardcodeados fuera del seed.

## 8. Prioridades y plan de contingencia

**P0 — no se negocia:**

- Publicación de eventos y búsqueda.
- Reserva temporal y compra con QR.
- No doble venta (3.7) e idempotencia (4.6).
- Devoluciones y cancelación.
- Entregables: README, OpenAPI, Postman, datos de prueba, diagramas y checklist.

**P1 — se recorta si hay atraso:**

- Orden por relevancia (se puede implementar como heurística simple).
- Swagger con ejemplos muy detallados (basta que esté completo y navegable).
- Documento de arquitectura extendido (basta `design.md` + README + diagramas).

**P2 — se descarta sin culpa:**

- Interfaz gráfica, cache, métricas y cualquier ítem de Entrega 2.

**Si un día termina atrasado:** al sync del día siguiente se reasigna a P4 o a la persona con menos carga; se recorta P1 antes que P0. Si el atraso es de más de un día, se elimina el buffer del sábado y se trabaja solo en la ruta crítica.

## 9. Riesgos principales

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Contrato REST incompleto | Retrabajo en tres servicios | Revisión US-01 a US-24 el Día 0; congelarlo y versionarlo |
| Doble venta por condición de carrera | Falla el requisito crítico | Updates condicionales/locks en PostgreSQL y prueba de 100 hilos en CI |
| Deadlocks en reservas múltiples | Errores intermitentes | Ordenar locks por ID y reintentar hasta 3 veces |
| Fallo parcial de la compra | Órdenes inconsistentes | Estados `ERROR_PAGO`/`REINTEGRO_PENDIENTE` y compensaciones idempotentes |
| Cancelación no llega a orders | Entradas sin reintegro | Endpoint idempotente + reintento desde catalog + prueba de integración |
| Tests de concurrencia solo en local | Se descubre tarde | Testcontainers en CI desde el Día 3 |
| Falta de tiempo para documentar | Entrega incompleta | Día 5 exclusivamente para documentación; P4 lidera desde el Día 0 |
| Miembros bloqueados entre sí | Paralización | Contrato congelado, mocks/WireMock y PRs pequeños |

## 10. Entregable final

El día 10 se entrega el repositorio con:

- Código fuente de los cuatro proyectos.
- `openspec/changes/entrega-1-core/` con propuesta, specs, diseño y tareas.
- `docs/features-y-user-stories.md` (todo el sistema).
- `docs/README.md` (índice), `docs/api-contract.md`, `docs/arquitectura.md`, `docs/modelo-datos.md`, `docs/decisiones.md` y `docs/pruebas.md`.
- README por servicio y diagramas (módulos, secuencia y ER).
- OpenAPI/Swagger por servicio.
- README con instrucciones de ejecución.
- Datos de prueba y colección Postman.
- Tests unitarios y prueba de no doble venta.
- Tag `entrega-1`.

La lista verificable está en `docs/checklist-entrega-1.md`.
