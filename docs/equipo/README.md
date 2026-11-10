# Briefs del equipo

Esta carpeta tiene un brief por integrante. La idea es que cada uno pueda clonar el repo, leer **su** archivo y arrancar sin depender de una explicación en el chat.

| Persona | Nombre | Rol | Brief |
|---|---|---|---|
| P1 | Iván | Catalog | [`compa-1.md`](compa-1.md) |
| P2 | Pablo | Inventory | [`compa-2.md`](compa-2.md) |
| P3 | Caro | Orders | [`compa-3.md`](compa-3.md) |
| P4 | Tiago | Gateway + infraestructura | [`compa-4.md`](compa-4.md) |
| P5 | Franco | Calidad, integración y documentación | [`franco-p5.md`](franco-p5.md) |

**Guía puntual Día 4:** [`caro-4.10.md`](caro-4.10.md) — la tarea 4.10 ya está implementada; pasos simples para verificarla y aprobar el PR.

## 1. Acceso al repositorio

**¿Es tu primer PR?** Empezá por [`primer-pr.md`](primer-pr.md): explica rama, commit, push y PR paso a paso, con GitHub Desktop y con terminal.

**De:** <https://github.com/Francogp131/TicketYa> (público para lectura).
**Para pushear:** el dueño (`Francogp131`) agrega a cada integrante como colaborador con permiso **Write**.
- Se trabaja con SSH o HTTPS; el dueño no necesita dar tokens a nadie.
- Si no llega la invitación, avisar en el grupo antes de crear un fork.

## 2. Setup común (una sola vez)

Requisitos: **Java 21**, Maven 3.9+, Docker Desktop y Git.

```bash
git clone git@github.com:Francogp131/TicketYa.git
cd TicketYa
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

Verificar Java 21 (si hay varias versiones instaladas):

```bash
java -version          # debe decir 21
mvn -version           # debe usar Java 21
```

Si Maven usa otra versión:

```bash
export JAVA_HOME=$JAVA_HOME
```

> Cada servicio es un proyecto Maven independiente. No hay `pom.xml` raíz: se ejecuta dentro de la carpeta del servicio.

## 3. Flujo de trabajo

1. `git switch main && git pull origin main`.
2. Crear rama: `git switch -c feat/<servicio>-<tema>` (ej. `feat/catalog-busqueda`).
3. Implementar **una tarea de `tasks.md` por PR** (o dos si son del mismo alcance).
4. Correr los tests de su servicio y actualizar documentación en el mismo PR.
5. Marcar la tarea en `openspec/changes/entrega-1-core/tasks.md`.
6. `git push -u origin <rama>` y abrir PR contra `main` con la plantilla.
7. Pedir revisión a otro integrante; nadie mergea su propio PR sin al menos una aprobación.
8. Después del merge: `git switch main && git pull` y borrar la rama local.

### Convención de commits

- `feat: ...` funcionalidad nueva
- `fix: ...` corrección
- `test: ...` tests
- `docs: ...` documentación
- `chore: ...` configuración

### Reglas que no se rompen

- **Un dueño por carpeta de servicio.** No se edita el código de otro servicio sin acordarlo en el grupo.
- **El contrato está congelado** en [`../api-contract.md`](../api-contract.md). Si algo no cierra, se discute antes de cambiar código.
- **Las migraciones Flyway `V1__init.sql` ya están mergeadas.** Para cambios de esquema se crea `V2__*.sql`; nunca se edita una migración aplicada.
- **La documentación va en el mismo PR:** README del servicio, OpenAPI y `docs/api-contract.md` si cambia un endpoint.
- **No se commitean** `target/`, `.env`, `.idea/`, `*.log` ni credenciales.

## 4. Qué ya está hecho (Día 0)

- Cuatro proyectos Maven independientes con Spring Boot 3.5.16, Java 21, Actuator y springdoc.
- `application.yml` de cada servicio con puerto, base y variables de entorno.
- Migraciones `V1__init.sql` de catalog, inventory y orders.
- Manejo global de errores RFC 7807 (`ApiException` + `GlobalExceptionHandler`) en los tres servicios.
- Gateway con rutas `/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`, filtro de identidad y `X-Correlation-Id`.
- `docker-compose.dev.yml` con las tres bases PostgreSQL 16.
- CI de GitHub Actions: build + tests por servicio y validación del compose.
- Contrato REST v1 en [`../api-contract.md`](../api-contract.md).
- Plan, checklist, specs y tareas en `openspec/changes/entrega-1-core/`.

## 5. Lectura mínima antes de codificar

1. [`../../README.md`](../../README.md) — cómo se ejecuta.
2. [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md) — reglas del equipo.
3. [`../api-contract.md`](../api-contract.md) — endpoints, DTOs y errores.
4. [`../../openspec/changes/entrega-1-core/design.md`](../../openspec/changes/entrega-1-core/design.md) — decisiones técnicas.
5. [`../../openspec/changes/entrega-1-core/tasks.md`](../../openspec/changes/entrega-1-core/tasks.md) — tareas y checkboxes.
6. [`../plan-entrega-1.md`](../plan-entrega-1.md) — cronograma día por día.

## 6. Comandos útiles

```bash
# Bases
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml logs -f catalog-db
docker compose -f docker-compose.dev.yml down          # conserva datos
docker compose -f docker-compose.dev.yml down -v       # borra datos

# Un servicio
cd catalog-service
mvn test
mvn spring-boot:run

# Health y Swagger
curl http://localhost:8081/actuator/health
open http://localhost:8081/swagger-ui.html

# Gateway
cd api-gateway && mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

## 7. Definición de terminado (para todos)

- [ ] La tarea de `tasks.md` está implementada y marcada.
- [ ] Tests nuevos pasan con `mvn test` en el servicio.
- [ ] El endpoint (si aplica) está en `docs/api-contract.md` y en el OpenAPI.
- [ ] El README del servicio refleja el cambio.
- [ ] El PR tiene evidencia (salida de tests o `curl`).
- [ ] Otro integrante revisó y aprobó.
- [ ] CI en verde.
