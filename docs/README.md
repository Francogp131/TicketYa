# Documentación de TicketYa

Índice único de la documentación del TP. Si un documento no está acá, no existe para el equipo. El estado se actualiza al cerrar cada día.

## 1. Documentos

| Documento | Qué contiene | Dueño | Cuándo | Estado |
|---|---|---|---|---|
| [`../README.md`](../README.md) | Presentación, requisitos, cómo ejecutar y datos de prueba | P4 | Día 0 a 5 | En progreso |
| [`equipo/README.md`](equipo/README.md) | Briefs por integrante, setup común, flujo de ramas y definición de terminado | P5 | Día 0 | Listo |
| [`equipo/primer-pr.md`](equipo/primer-pr.md) | Guía paso a paso para el primer PR (GitHub Desktop, terminal y web) | P5 | Día 1 | Listo |
| [`equipo/compa-1.md`](equipo/compa-1.md) | Brief de catalog (P1) | P5 | Día 0 | Listo |
| [`equipo/compa-2.md`](equipo/compa-2.md) | Brief de inventory (P2) | P5 | Día 0 | Listo |
| [`equipo/compa-3.md`](equipo/compa-3.md) | Brief de orders (P3) | P5 | Día 0 | Listo |
| [`equipo/compa-4.md`](equipo/compa-4.md) | Brief de gateway e infraestructura (P4) | P5 | Día 0 | Listo |
| [`equipo/franco-p5.md`](equipo/franco-p5.md) | Brief de calidad, integración y documentación (P5) | P5 | Día 0 | Listo |
| [`plan-entrega-1.md`](plan-entrega-1.md) | Cronograma día por día, responsables y riesgos | P4 | Día 0 | Listo |
| [`checklist-entrega-1.md`](checklist-entrega-1.md) | Verificación de todo lo pedido para la Entrega 1 | P4 | Día 0 | Listo |
| [`features-y-user-stories.md`](features-y-user-stories.md) | Features y US-01 a US-38 con criterios de aceptación | P1/P2/P3 | Día 0 | Listo |
| [`api-contract.md`](api-contract.md) | Contrato REST: método, ruta, parámetros, códigos y ejemplos JSON | P1/P2/P3 | Día 0 | Listo (1.3) |
| [`arquitectura.md`](arquitectura.md) | Diagrama de módulos y secuencia de compra (aprobado/rechazado/vencido) | P4 | Día 3 a 5 | Listo (5.3, 5.4) |
| `modelo-datos.md` | ER por microservicio, claves, relaciones y entidades solapadas | P1/P2/P3 | Día 5 | Pendiente (5.5) |
| `decisiones.md` | ADR cortas: decisión, alternativas y consecuencias | P4 | Día 5 | Pendiente (5.9) |
| [`pruebas.md`](pruebas.md) | Estrategia, comandos y evidencia de concurrencia y E2E | P5 | Día 5 y 6 | En progreso (5.11) |
| `../openspec/changes/entrega-1-core/` | Propuesta, specs, diseño y tareas del cambio | Todos | Día 0 | Listo |
| `../catalog-service/README.md` | Responsabilidad, endpoints, modelo y comandos del servicio | P1 | Día 1 a 4 | Día 1 |
| `../inventory-service/README.md` | Responsabilidad, endpoints, modelo y comandos del servicio | P2 | Día 1 a 4 | Día 1 |
| `../orders-service/README.md` | Responsabilidad, endpoints, modelo y comandos del servicio | P3 | Día 1 a 4 | Día 1 |
| `../api-gateway/README.md` | Rutas, headers, variables y ejecución | P4 | Día 1 a 4 | Día 1 |
| `../postman/TicketYa.postman_collection.json` | Colección de requests con assertions | P5 | Día 5 | Flujos de compra, rechazo, vencimiento y cancelación |
| `../openapi/*.json` | OpenAPI exportado de cada servicio | Cada dueño | Día 4 | Pendiente (2.6, 3.8, 4.11) |

## 2. Estándares

- **Idioma:** español; nombres de endpoints, códigos y estados en inglés cuando ya están definidos en los specs.
- **Formato:** Markdown con tablas y bloques de código; diagramas en Mermaid, no imágenes sueltas.
- **Contrato:** `docs/api-contract.md` es la vista humana y el OpenAPI es la fuente machine-readable. Ambos se actualizan en el mismo PR.
- **Errores:** RFC 7807 (`application/problem+json`) con `status`, `title`, `detail`, `instance`, `code` y `errors[]`.
- **Fechas:** ISO-8601 con offset `America/Argentina/Buenos_Aires`; montos en ARS con `BigDecimal`.
- **Ejemplos:** cada endpoint documentado debe incluir request, response 2xx, al menos un error y un `curl` reproducible.
- **Identidad:** en Entrega 1 se documentan `X-User-Id` y `X-User-Role`; en Entrega 2 se reemplazan por JWT sin cambiar los docs de negocio.
- **Evidencia:** los resultados de tests, concurrencia y E2E se pegan como texto o salida de consola, no como capturas sin contexto.

## 3. Reglas de actualización

1. La documentación se escribe en el mismo PR que el código; el PR no se aprueba si falta.
2. Cada PR actualiza el índice si agrega o mueve un documento.
3. Los enlaces deben ser relativos y funcionar desde GitHub; no se usan rutas absolutas de la máquina.
4. Los diagramas se actualizan cuando cambia el contrato, el modelo o el flujo de compra.
5. Al cerrar el día, P4 revisa que el índice refleje el estado real y que no haya enlaces rotos.
6. Los cambios de documentación usan commits `docs:` y se pueden agrupar con el cambio de código si son del mismo alcance.

## 4. Verificación de la documentación

- [ ] Todos los enlaces de este índice resuelven.
- [ ] Cada endpoint del contrato aparece en el OpenAPI y en el README del servicio.
- [ ] Los diagramas Mermaid renderizan en GitHub.
- [ ] El README raíz se probó en un clone limpio.
- [ ] `docs/pruebas.md` tiene comandos que se ejecutan tal cual.
- [ ] `docs/checklist-entrega-1.md` no tiene ítems sin evidencia.
