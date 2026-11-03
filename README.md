# TicketYa

TP grupal de Arquitectura Web (TUDAI 2026): plataforma de venta de entradas con microservicios Spring Boot.
Enunciado: [`docs/TPE-ArqWeb-TicketYa-2026.pdf`](docs/TPE-ArqWeb-TicketYa-2026.pdf).

## Estructura
| Carpeta | Contenido |
|---|---|
| `catalog-service/` | Eventos, lugares, sectores, búsqueda |
| `inventory-service/` | Reservas y anti doble venta |
| `orders-service/` | Compra, pago mock, QR, devoluciones |
| `api-gateway/` | Punto de entrada único |
| `openspec/` | Especificaciones (propuesta → specs → diseño → tareas) |
| `docs/` | Diseño, diagramas, enunciado |

## Requisitos
Java 21, Maven, Docker (para las bases de datos).

## Cómo ejecutar
_(se completa a medida que existan los servicios)_

## Metodología
Desarrollo guiado por especificaciones con [OpenSpec](https://github.com/Fission-AI/OpenSpec). Ver `CONTRIBUTING.md`.

## Plan de trabajo
- [Índice de documentación](docs/README.md)
- [Plan día por día — Entrega 1](docs/plan-entrega-1.md)
- [Checklist verificable de la Entrega 1](docs/checklist-entrega-1.md)
- [Cambio OpenSpec de la Entrega 1](openspec/changes/entrega-1-core/)
