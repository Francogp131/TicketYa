# Primer PR — guía para el equipo

Esta guía es para el primer aporte de cada integrante. No hace falta saber Git de memoria: alcanza con seguir los pasos.

## 1. Glosario mínimo

| Término | Qué es |
|---|---|
| **Repositorio (repo)** | La carpeta del proyecto en GitHub. |
| **Rama (branch)** | Una copia paralela para trabajar sin romper `main`. |
| **Commit** | Un guardado con un mensaje que explica qué cambiaste. |
| **Push** | Subir tus commits a GitHub. |
| **PR (Pull Request)** | Un pedido para que revisen y mergeen tu rama en `main`. Es una página de GitHub, no un archivo. |
| **Review** | Otro integrante lee tu PR y lo aprueba o pide cambios. |
| **Merge** | Unir tu rama aprobada a `main`. |
| **CI** | Los tests automáticos que corren en cada PR (GitHub Actions). |

## 2. Antes de empezar

1. Aceptá la invitación de GitHub. Si ya la aceptaste, aparecés como colaborador.
2. Instalá:
   - [GitHub Desktop](https://desktop.github.com/) (recomendado para el primer PR).
   - Java 21, Maven 3.9+ y Docker Desktop.
3. Cloná el repo:

```bash
git clone https://github.com/Francogp131/TicketYa.git
cd TicketYa
```

> El repo es público: podés clonarlo sin invitación. Para **pushear** necesitás ser colaborador con permiso Write.

## 3. Camino A — GitHub Desktop (recomendado)

1. Abrí GitHub Desktop e iniciá sesión con tu cuenta.
2. `File` → `Clone repository` → pestaña `URL` → pegá `https://github.com/Francogp131/TicketYa.git` → `Clone`.
3. Arriba, donde dice `Current Branch`, hacé clic → `New Branch` → nombre `feat/<servicio>-<tema>` → `Create Branch`.
4. Abrí la carpeta en VS Code o IntelliJ y hacé el cambio.
5. Corré los tests de tu servicio (ver sección 5).
6. Volvé a GitHub Desktop: vas a ver los archivos cambiados.
7. Abajo a la izquierda escribí un resumen, por ejemplo `feat(inventory): límite por cliente`, y hacé `Commit to feat/...`.
8. Arriba hacé `Push origin`.
9. GitHub Desktop te ofrece `Create Pull Request`. Elegí base `main`, completá título y descripción, y creá el PR.
10. Mandá el link del PR al grupo para que lo revisen.

## 4. Camino B — terminal

```bash
git clone https://github.com/Francogp131/TicketYa.git
cd TicketYa
git checkout -b feat/inventory-limite
# ... editás los archivos ...
cd inventory-service
mvn test
cd ..
git add -A
git commit -m "feat(inventory): límite de entradas por cliente"
git push -u origin feat/inventory-limite
```

GitHub te devuelve un link para crear el PR. Si no, entrá al repo y usá el botón `Compare & pull request`.

## 5. Cómo correr el entorno

```bash
# desde la raíz del repo
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

Verificá Java 21:

```bash
java -version
mvn -version
```

Tests de tu servicio:

```bash
cd catalog-service && mvn test        # Iván
cd inventory-service && mvn test      # Pablo
cd orders-service && mvn test         # Caro
cd api-gateway && mvn test            # Tiago
```

Levantar tu servicio:

```bash
mvn spring-boot:run
```

## 6. Camino C — sin instalar nada (solo documentación)

Sirve para cambios de README o docs, no para código:

1. Entrá al archivo en GitHub.
2. Clic en el lápiz (`Edit this file`).
3. Escribí el cambio.
4. Abajo elegí `Create a new branch for this commit`.
5. `Propose changes` → `Create pull request`.

No lo uses para código: no vas a poder correr los tests.

## 7. Reglas de un buen PR

- **Un PR por tarea**, chico y con un objetivo claro.
- **Tests verdes** antes de pedir review.
- **No toques** archivos de otro servicio sin acordarlo con su dueño.
- **Cambios de contrato** (`docs/api-contract.md`) se avisan antes.
- Título con formato: `feat(catalog): búsqueda con filtros`, `fix(inventory): liberar zona`, `docs: diagrama de módulos`.
- Si el CI falla, entrá a `Details`, leé el error y preguntá con el mensaje exacto.

## 8. Si te trabás

No pierdas horas solo. Mandá al grupo:

1. Qué comando corriste.
2. El texto completo del error.
3. Una captura de pantalla si ayuda.

Y aclará si estás en Windows, macOS o Linux.

## 9. Primer PR sugerido por persona

| Persona | Primer PR (chico, para aprender el circuito) |
|---|---|
| Iván | Un test de validación de sector numerado en `catalog-service`. |
| Pablo | Un test de disponibilidad de zona en `inventory-service`. |
| Caro | Un test de detalle de orden inexistente en `orders-service`. |
| Tiago | El esqueleto del diagrama de módulos en `docs/arquitectura.md`. |

Después de ese PR, cada uno sigue con las tareas del Día 2 de su brief.
