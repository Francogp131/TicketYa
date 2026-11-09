# P4 — Gateway + infraestructura (Tiago)

**Servicio:** `api-gateway` (puerto **8080**)
**Rama base:** `main`
**Tareas OpenSpec:** 1.1, 1.4 a 1.7, 5.3, 5.4, 5.6, 5.8, 5.9
**Estado al 4/11:** 1.4 y 1.7 cerradas en el PR #1 (handlers RFC 7807 y CI verde). Quedan **1.1** (colaboradores y branch protection) y arrancás con **5.3 (diagrama de módulos)** + apoyo en **2.4**.

## 1. Antes de empezar

1. Leer [`../api-contract.md`](../api-contract.md) (secciones 1 y 2).
2. Leer `design.md`, decisiones 2, 3 y 11.
3. Leer [`../plan-entrega-1.md`](../plan-entrega-1.md) y [`../README.md`](../README.md).
4. Levantar las bases y el gateway:

```bash
docker compose -f docker-compose.dev.yml up -d
cd api-gateway
mvn test
mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

## 2. Alcance

Sos dueño de:

- `api-gateway/` (rutas, filtros, tests del gateway).
- `docker-compose.dev.yml`, `.github/workflows/ci.yml` y `.env.example`.
- README raíz, `docs/arquitectura.md`, diagramas de módulos y secuencia.
- Coordinación de la integración y del entorno de pruebas.

**No toques** el código de catalog, inventory ni orders. Si una ruta o rol no cierra, se acuerda con el dueño y se actualiza el contrato.

## 3. Tareas y criterios de aceptación

### 1.4 Errores comunes y validaciones (soporte)

- El handler RFC 7807 ya está creado en los tres servicios (`ApiException` + `GlobalExceptionHandler`).
- Tu tarea es **revisarlo y unificarlo**: mismos códigos, mismo `type`, mismo `errors[]`.
- Agregar tests que verifiquen 400/401/403/404/409 con `application/problem+json` en al menos un servicio.
- Si encontrás una diferencia entre servicios, se corrige en un PR coordinado con el dueño.

### 1.5 Docker y PostgreSQL

- `docker-compose.dev.yml` ya tiene las tres bases con healthchecks.
- Verificar `docker compose up -d`, que las tres estén `healthy` y que Flyway cree las tablas al levantar cada servicio.
- Agregar un script (`scripts/dev-up.sh`) o documentar el comando exacto en el README raíz.
- Si hace falta, agregar `pgAdmin` o un volumen de backups **solo si el grupo lo pide**; no sumar infraestructura innecesaria.

### 1.6 Gateway: rutas, identidad y propagación

- Rutas `/api/catalog/**`, `/api/inventory/**`, `/api/orders/**` ya configuradas.
- `IdentityHeaderFilter` ya existe: exige `X-User-Id` (UUID) y `X-User-Role`, aplica roles del contrato y propaga `X-Correlation-Id`.
- Tu tarea:
  - Revisar que las rutas públicas del contrato no pidan identidad y que las protegidas devuelvan 401/403 en formato RFC 7807.
  - Agregar tests del filtro (sin identidad → 401, rol incorrecto → 403, rol correcto → llega al servicio).
  - Verificar con `curl` que el gateway rutea a los tres servicios.
- Importante: en la Entrega 2 este filtro se reemplaza por JWT, pero debe seguir propagando los mismos headers.

### 1.7 CI

- El workflow ya compila los cuatro servicios con Java 21 y valida el compose.
- Tu tarea:
  - Verificar que el workflow esté verde en `main` y en un PR de prueba.
  - Agregar cache de Maven (ya está) y, si hace falta, un job de `openspec validate`.
  - Documentar en el README cómo correr lo mismo localmente.
- No agregues tests de concurrencia al job del gateway: van en inventory.

### 5.3 Diagrama de módulos

- `docs/arquitectura.md` con Mermaid: cliente → gateway → tres servicios → tres bases; pasarela mock y llamadas internas.
- Debe mostrar claramente que catalog es dueño del catálogo, inventory de la disponibilidad y orders de la compra.
- Verificar que GitHub renderice el diagrama.

### 5.4 Diagrama de secuencia de compra

- Tres secuencias en Mermaid: pago aprobado, pago rechazado y reserva vencida.
- Debe incluir gateway, inventory, orders, pasarela mock y los estados (`VIGENTE`, `PAGADA`, `RECHAZADA`, `ERROR_PAGO`).
- Contraste con `design.md` (decisión 7).

### 5.6 README raíz

- Requisitos (Java 21, Maven, Docker), cómo clonar, levantar bases, correr cada servicio, URLs y variables.
- Datos de prueba (cuando existan, tarea 5.1).
- Enlaces a docs, OpenSpec y briefs del equipo.
- Verificar siguiendo el README en un clone limpio.

### 5.8 OpenAPI agregado

- Revisar que los tres servicios tengan Swagger UI y `openapi.json` con descripciones, ejemplos y errores.
- Mantener `openapi/` con los tres archivos exportados.
- Verificar que cada endpoint del contrato esté en el OpenAPI correspondiente.

### 5.9 Índice y ADRs

- `docs/README.md` ya existe; mantenerlo actualizado con dueño y estado.
- Crear `docs/decisiones.md` con ADRs cortas: decisión, alternativas y consecuencias.
- Verificar que no haya enlaces rotos.

## 4. Ramas sugeridas

| Rama | Tarea |
|---|---|
| `feat/gateway-identidad-tests` | 1.4, 1.6 |
| `chore/infra-docker-ci` | 1.5, 1.7 |
| `docs/arquitectura-diagramas` | 5.3, 5.4 |
| `docs/readme-raiz` | 5.6 |
| `docs/openapi-indice` | 5.8, 5.9 |

## 5. PR

Título: `feat(gateway): <tema>` o `docs(infra): <tema>`.

En el cuerpo:

- Qué se verificó y cómo (comandos).
- Salida de tests y de `docker compose ps`.
- Captura o salida de `curl` del gateway.
- Enlaces a los diagramas y documentos.

## 6. Coordinación

- **Con P1:** rutas de catálogo y roles ADMIN/ORGANIZER.
- **Con P2:** rutas públicas de disponibilidad y asientos.
- **Con P3:** roles ADMIN en aprobar/rechazar devoluciones.
- **Con P5:** el entorno de pruebas y la colección Postman deben usar el gateway, nunca los puertos de los servicios.

## 7. Terminado cuando

- [ ] 1.4 a 1.7, 5.3, 5.4, 5.6, 5.8 y 5.9 marcadas en `tasks.md` (las que correspondan).
- [ ] `mvn test` verde en `api-gateway`.
- [ ] CI verde en `main` y en un PR de prueba.
- [ ] Gateway rutea a los tres servicios con `curl`.
- [ ] Diagramas y README raíz revisados por el grupo.
