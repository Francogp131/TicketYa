# Documentación de TicketYa

Índice único de la documentación del TP. Si un documento no está acá, no existe para el equipo. El estado se actualiza al cerrar cada día.

## 1. Documentos

| Documento | Qué contiene | Dueño | Cuándo | Estado |
|---|---|---|---|---|
| [`../README.md`](../README.md) | Presentación, requisitos, cómo ejecutar y datos de prueba | P4 | Día 0 a 8 | Listo (5.6; verificado en el RC y en el tag final) |
| [`equipo/README.md`](equipo/README.md) | Briefs por integrante, setup común, flujo de ramas y definición de terminado | P5 | Día 0 | Listo |
| [`equipo/primer-pr.md`](equipo/primer-pr.md) | Guía paso a paso para el primer PR (GitHub Desktop, terminal y web) | P5 | Día 1 | Listo |
| [`equipo/compa-1.md`](equipo/compa-1.md) | Brief de catalog (P1) | P5 | Día 0 | Listo |
| [`equipo/compa-2.md`](equipo/compa-2.md) | Brief de inventory (P2) | P5 | Día 0 | Listo |
| [`equipo/compa-3.md`](equipo/compa-3.md) | Brief de orders (P3) | P5 | Día 0 | Listo |
| [`equipo/compa-4.md`](equipo/compa-4.md) | Brief de gateway e infraestructura (P4) | P5 | Día 0 | Listo |
| [`equipo/franco-p5.md`](equipo/franco-p5.md) | Brief de calidad, integración y documentación (P5) | P5 | Día 0 | Listo |
| [`plan-entrega-1.md`](plan-entrega-1.md) | Cronograma día por día, responsables y riesgos | P4 | Día 0 | Listo |
| [`checklist-entrega-1.md`](checklist-entrega-1.md) | Verificación de todo lo pedido para la Entrega 1 | P4 | Día 0 a 9 | En revisión (observaciones 9/11) |
| [`plan-entrega-2.md`](plan-entrega-2.md) | Cronograma 13–22/11, carga por integrante, ruta crítica y riesgos | P5 | Día 0 | Listo (planificación) |
| [`checklist-entrega-2.md`](checklist-entrega-2.md) | Verificación de todo lo pedido para la Entrega 2 | P5 | Día 0 a 10 | En curso (planificación) |
| [`equipo/entrega-2/`](equipo/entrega-2/README.md) | Briefs por integrante para la Entrega 2 | P5 | Día 0 | Listo |
| [`features-y-user-stories.md`](features-y-user-stories.md) | Features y US-01 a US-38 con criterios de aceptación | P1/P2/P3 | Día 0 | Listo |
| [`api-contract.md`](api-contract.md) | Contrato REST: método, ruta, parámetros, códigos y ejemplos JSON | P1/P2/P3 | Día 0 | Listo (1.3) |
| [`eventos.md`](eventos.md) | Sobre de eventos, routing keys, colas/DLQ y payloads | P4/P5 | Día 1 y 3 | Borrador para 1.5; consumidor de 7.3 alineado |
| [`arquitectura.md`](arquitectura.md) | Diagrama de módulos, secuencia de compra (aprobado/rechazado/vencido), límites de servicio y consistencia | P4/P5 | Día 3 a 9 | Listo; deuda conocida documentada |
| [`modelo-datos.md`](modelo-datos.md) | ER por microservicio, claves, relaciones y entidades solapadas | P1/P2/P3/P5 | Día 5 | Listo (5.5) |
| [`decisiones.md`](decisiones.md) | ADR cortas: decisión, alternativas y consecuencias | P4/P5 | Día 5 a 9 | Listo (ADR-001 a ADR-018) |
| [`pruebas.md`](pruebas.md) | Estrategia, comandos y evidencia de concurrencia, suite, E2E, multi-instancia y carga | P5 | Día 5 a 9 | En revisión; incluye plan multi-instancia y estrategia de carga temprana |
| [`evidencia/`](evidencia/) | Salidas de suite, E2E y clone limpio de los Días 4 a 9 | P5 | Día 4 a 9 | Listo hasta el Día 9; se agregan días 2 y 3 de la Entrega 2 |
| `../openspec/changes/entrega-1-core/` | Propuesta, specs, diseño y tareas del cambio | Todos | Día 0 | Listo |
| `../openspec/changes/entrega-2-completo/` | Propuesta, 15 specs, diseño y 64 tareas de la Entrega 2 | Todos | Día 0 | Listo (validado) |
| `../catalog-service/README.md` | Responsabilidad, endpoints, modelo y comandos del servicio | P1 | Día 1 a 4 | Día 4 |
| `../inventory-service/README.md` | Responsabilidad, endpoints, modelo y comandos del servicio | P2 | Día 1 a 4 | Día 4 |
| `../orders-service/README.md` | Responsabilidad, endpoints, modelo y comandos del servicio | P3 | Día 1 a 4 | Día 4 |
| `../recommendation-service/README.md` | Responsabilidad, endpoints, modelo MongoDB y comandos del servicio | P5 | Día 2 a 5 | Día 3 (1.7, 7.1–7.4) |
| `../api-gateway/README.md` | Rutas, headers, variables y ejecución | P4 | Día 1 a 5 | Día 5 |
| `../postman/TicketYa.postman_collection.json` | Colección de requests con assertions | P5 | Día 5 y 6 | 45 requests, Newman 45/45 (Día 6) |
| `../openapi/*.json` | OpenAPI exportado de cada servicio | Cada dueño | Día 4 | Listos: catalog (2.6), inventory (3.8) y orders (4.11); revisión agregada 5.8; recommendation agregado el Día 2 |

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

- [x] Todos los enlaces de este índice resuelven (100 enlaces relativos verificados el Día 7).
- [x] Cada endpoint del contrato aparece en el OpenAPI y en el README del servicio (26 paths y 30 operaciones en `openapi/*.json`).
- [x] Los diagramas Mermaid renderizan en GitHub.
- [x] El README raíz se probó en un clone limpio (Días 5 y 7).
- [x] `docs/pruebas.md` tiene comandos que se ejecutan tal cual (suite y Newman corridos el Día 6).
- [ ] `docs/checklist-entrega-1.md` no tiene ítems sin evidencia. _(2.4 en corrección)_
