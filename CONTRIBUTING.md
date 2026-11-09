# Cómo trabajamos

## Reparto
| Persona | Nombre | Rol | Servicio / foco | Ítems del enunciado |
|---|---|---|---|---|
| P1 | Iván | Catalog | `catalog-service` | 1, 2, 3, 9 |
| P2 | Pablo | Inventory | `inventory-service` | 4, 5 |
| P3 | Caro | Orders | `orders-service` | 4, 7 |
| P4 | Tiago | Gateway + infraestructura | `api-gateway`, Docker, CI, diagramas | transversal |
| P5 | Franco | Calidad e integración | Datos de prueba, Postman, E2E, evidencia, índice y checklist | transversal |

Un solo dueño por carpeta de microservicio. P5 no edita código de un servicio sin acordarlo con su dueño; su foco es la verificación y la documentación.

## Flujo de Git
1. `main` protegida: todo entra por Pull Request con al menos 1 aprobación.
2. Ramas por funcionalidad: `feat/<servicio>-<tema>` (ej. `feat/catalog-busqueda`).
3. Antes de empezar: `git pull origin main`. Commits chicos y descriptivos (`feat:`, `fix:`, `docs:`, `test:`).
4. No editar carpetas de otro servicio sin avisar; los cambios de contrato se discuten en el spec primero.

## Metodología (OpenSpec)
- Cada funcionalidad empieza con una propuesta en `openspec/changes/<nombre>/` (proposal → specs → design → tasks).
- Con la CLI: `openspec new change <nombre>` y `openspec validate <nombre>`.
- Al terminar de implementar: `openspec archive <nombre>`.
- Los specs son la base de los contratos OpenAPI y de los tests.

## Documentación
- Índice único en [`docs/README.md`](docs/README.md); si un documento no está ahí, no existe para el equipo.
- La documentación se escribe en el mismo PR que el código: contrato, OpenAPI y README del servicio.
- Diagramas en Mermaid dentro de `docs/arquitectura.md` y `docs/modelo-datos.md`.
- Evidencia de pruebas en `docs/pruebas.md`; decisiones técnicas en `docs/decisiones.md`.
- Un PR no se aprueba si deja enlaces rotos o endpoints sin documentar.
