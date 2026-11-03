# Cómo trabajamos

## Reparto inicial (a ajustar entre el grupo)
| Persona | Servicio | Ítems del enunciado |
|---|---|---|
| 1 | `catalog-service` | 1, 2, 3, 9 |
| 2 | `inventory-service` | 4, 5 |
| 3 | `orders-service` | 4, 7 |
| 4 | `api-gateway` + infraestructura (README, Postman, Docker) | transversal |

Un solo dueño por carpeta de microservicio.

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
