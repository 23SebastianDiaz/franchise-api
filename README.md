# Franchise API

API REST reactiva para administrar **franquicias**, sus **sucursales** y los **productos** (con stock) de cada sucursal.

- **Stack:** Java 21 · Spring Boot 4 (WebFlux / Project Reactor) · AWS SDK v2 (`DynamoDbAsyncClient`) · DynamoDB · Maven
- **Persistencia:** DynamoDB en AWS, aprovisionada con CloudFormation. En local se usa DynamoDB Local (Docker).
- **Arquitectura:** hexagonal ligera (dominio aislado de Spring y de AWS).

## Requisitos

| Herramienta | Para qué | Versión |
| --- | --- | --- |
| JDK | compilar y ejecutar | 21 |
| Docker + Docker Compose | DynamoDB Local | cualquiera reciente |
| AWS CLI | crear la tabla local (y desplegar a AWS) | v2 |

Maven no se instala: el proyecto incluye el wrapper `./mvnw`.

## Ejecución en local

```bash
# 1. Levantar DynamoDB Local (puerto 8000)
docker compose up -d

# 2. Crear la tabla "franchises"
./scripts/create-table.sh

# 3. Configurar la app para usar DynamoDB Local
set -a; source .env.example; set +a

# 4. Arrancar la API
./mvnw spring-boot:run
```

- `.env.example` define `DYNAMODB_ENDPOINT`, `DYNAMODB_TABLE`, `AWS_REGION` y `SERVER_PORT` (8081). Si el puerto 8080 de tu máquina está libre puedes quitar `SERVER_PORT` y se usará el 8080.
- Sin `DYNAMODB_ENDPOINT` la app se conecta a AWS real (ver [Despliegue en AWS](#despliegue-en-aws)).

Comprobar que está arriba:

```bash
curl localhost:8081/actuator/health        # {"groups":["liveness","readiness"],"status":"UP"}
```

Documentación interactiva (Swagger UI): <http://localhost:8081/swagger-ui.html>

### Pruebas

```bash
./mvnw test
```

No requieren DynamoDB ni AWS: el dominio se prueba con el repositorio simulado y la capa REST con `WebTestClient`.

## API

Base: `/api/v1/franchises`. Todos los identificadores son UUID generados por el servidor.

| Método | Ruta | Body | Respuestas |
| --- | --- | --- | --- |
| POST | `/` | `{"name"}` | 201, 400 |
| POST | `/{franchiseId}/branches` | `{"name"}` | 201, 400, 404 |
| POST | `/{franchiseId}/branches/{branchId}/products` | `{"name","stock"}` | 201, 400, 404 |
| DELETE | `/{franchiseId}/branches/{branchId}/products/{productId}` | — | 204, 404 |
| PATCH | `/{franchiseId}/branches/{branchId}/products/{productId}/stock` | `{"stock"}` | 200, 400, 404 |
| GET | `/{franchiseId}/top-stock-products` | — | 200, 404 |

Errores con formato uniforme: `{"status","error","message","timestamp"}`.

### Ejemplo de uso

```bash
U=localhost:8081/api/v1/franchises
H='Content-Type: application/json'

# Crear franquicia -> devuelve {"id": "...", "name": "Burger Co"}
curl -s -X POST $U -H "$H" -d '{"name":"Burger Co"}'

# Crear sucursal y producto (reemplaza los ids con los devueltos)
curl -s -X POST $U/<franchiseId>/branches -H "$H" -d '{"name":"Centro"}'
curl -s -X POST $U/<franchiseId>/branches/<branchId>/products -H "$H" -d '{"name":"Fries","stock":60}'

# Modificar stock
curl -s -X PATCH $U/<franchiseId>/branches/<branchId>/products/<productId>/stock -H "$H" -d '{"stock":15}'

# Producto con más stock por sucursal
curl -s $U/<franchiseId>/top-stock-products
# [{"branchId":"...","branchName":"Centro","productId":"...","productName":"Fries","stock":60}]

# Eliminar producto
curl -s -X DELETE $U/<franchiseId>/branches/<branchId>/products/<productId>
```

## Supuestos

- **Empates en el top de stock:** se devuelve un solo producto por sucursal; en caso de empate gana el de nombre alfabéticamente menor (criterio determinista).
- **Sucursales sin productos:** se omiten del resultado del top de stock.
- **Nombres:** obligatorios, no vacíos, máximo 100 caracteres. No se exige unicidad: cada entidad se identifica por su UUID.
- **Stock:** entero mayor o igual a 0; un valor negativo responde 400.
- **Eliminar producto:** el enunciado dice "eliminar un nuevo producto"; se interpretó como eliminar un producto de una sucursal.

## Decisiones de diseño

- **Hexagonal ligera:** `domain` (modelo y puerto `FranchiseRepository`), `application/usecase` (casos de uso) e `infrastructure` (REST, adaptador DynamoDB, configuración). El dominio no depende de Spring ni de AWS.
- **Reactivo:** WebFlux + Reactor. Las llamadas del SDK asíncrono (`CompletableFuture`) se integran con `Mono.fromFuture`.
- **DynamoDB single-table** (`PK`, `SK`, modo `PAY_PER_REQUEST`):

  | Entidad | PK | SK |
  | --- | --- | --- |
  | Franquicia | `FRANCHISE#<id>` | `METADATA` |
  | Sucursal | `FRANCHISE#<id>` | `BRANCH#<id>` |
  | Producto | `FRANCHISE#<id>` | `BRANCH#<id>#PRODUCT#<id>` |

  - El top de stock usa **una sola `Query`** por partición (con paginación), sin `Scan`.
  - Crear sucursal/producto usa `TransactWriteItems`: verifica atómicamente que el padre exista antes de insertar.
  - Actualizar y eliminar usan `ConditionExpression attribute_exists(PK)`, así que no se crean ítems fantasma.
- **Configuración** por variables de entorno; no hay secretos en el repositorio.

## Despliegue en AWS

La tabla se aprovisiona con CloudFormation (`infra/cloudformation/dynamodb-table.yml`):

```bash
aws cloudformation deploy \
  --template-file infra/cloudformation/dynamodb-table.yml \
  --stack-name franchise-dynamodb \
  --profile <tu-perfil> --region us-east-1
```

Ejecutar la API local contra esa tabla (sin `DYNAMODB_ENDPOINT`):

```bash
AWS_PROFILE=<tu-perfil> AWS_REGION=us-east-1 SERVER_PORT=8081 ./mvnw spring-boot:run
```

Para eliminar los recursos: `aws cloudformation delete-stack --stack-name franchise-dynamodb`.

## Estructura

```
src/main/java/com/juan/franchise/
├── domain/            # modelo, excepciones y puerto del repositorio
├── application/       # casos de uso
└── infrastructure/
    ├── entrypoint/rest/   # controlador, DTOs, manejo global de errores
    ├── persistence/       # adaptador DynamoDB
    └── config/            # cliente DynamoDB, OpenAPI
infra/cloudformation/  # IaC
scripts/               # creación de tabla local
```
