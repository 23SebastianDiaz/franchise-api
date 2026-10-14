# Franchise API

API REST reactiva para administrar **franquicias**, sus **sucursales** y los **productos** (con stock) de cada sucursal.

- **Stack:** Java 21 · Spring Boot 4 (WebFlux / Project Reactor) · AWS SDK v2 (`DynamoDbAsyncClient`) · DynamoDB · Maven
- **Persistencia:** DynamoDB en AWS, aprovisionada con CloudFormation. En local se usa DynamoDB Local (Docker).
- **Arquitectura:** hexagonal ligera (dominio aislado de Spring y de AWS).

## Prueba rápida (API desplegada en AWS)

- **URL base:** <https://6lgdtmkag3yjftcutdcwop4keq0mknuu.lambda-url.us-east-1.on.aws>
- **Swagger UI** (probar los endpoints desde el navegador): <https://6lgdtmkag3yjftcutdcwop4keq0mknuu.lambda-url.us-east-1.on.aws/swagger-ui.html>
- **Postman:** importar [`postman/franchise-api.postman_collection.json`](postman/franchise-api.postman_collection.json) y ejecutar las peticiones en orden; los identificadores se guardan solos.
- Una regla de EventBridge hace un ping cada 5 minutos para mantener la función caliente (respuestas en ~0,3 s). Si AWS recicla la instancia, la primera petición puede tardar unos 10 s (arranque en frío de la JVM).

## Requisitos

| Herramienta | Para qué | Versión |
| --- | --- | --- |
| Docker + Docker Compose | levantar todo (Opción A) o solo DynamoDB Local (Opción B) | cualquiera reciente |
| JDK | solo en la Opción B y para correr los tests | 21 |
| AWS CLI | solo en la Opción B (script de tabla) y para desplegar a AWS | v2 |

Maven no se instala: el proyecto incluye el wrapper `./mvnw`.

## Ejecución en local

### Opción A: todo con Docker (recomendada)

Solo necesitas Docker. Un comando construye la imagen, levanta DynamoDB Local, crea la tabla y arranca la API:

```bash
APP_PORT=8081 docker compose up -d --build
```

`APP_PORT` es el puerto del host (por defecto 8080; cámbialo si está ocupado). Para detener todo: `docker compose down`.

### Opción B: API con Java y DynamoDB Local en Docker

```bash
# 1. Levantar solo DynamoDB Local (puerto 8000) y crear la tabla
docker compose up -d dynamodb-local
./scripts/create-table.sh

# 2. Configurar la app para usar DynamoDB Local
set -a; source .env.example; set +a

# 3. Arrancar la API
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
| GET | `/` | — | 200 (extra: todas las franquicias con sus sucursales y productos) |
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

# Listar todas las franquicias con sucursales y productos (para obtener los ids)
curl -s $U

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
- **Listado de franquicias:** `GET /api/v1/franchises` usa un `Scan` paginado de la tabla (es un endpoint de consulta pensado para el volumen de una demo; con muchos datos convendría un índice secundario y paginación hacia el cliente). El resto de operaciones usan acceso por clave, y el top de stock una sola `Query`.
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

## Despliegue en la nube (AWS Lambda)

La API está desplegada como **imagen de contenedor en AWS Lambda** con una Function URL pública, y usa la tabla DynamoDB del stack anterior.

**URL pública:** <https://6lgdtmkag3yjftcutdcwop4keq0mknuu.lambda-url.us-east-1.on.aws/>

- Health: `/actuator/health` · Swagger UI: `/swagger-ui.html` · API: `/api/v1/franchises`
- **Arranque en frío:** una regla de EventBridge (`rate(5 minutes)`) mantiene la función caliente; aun así, si AWS recicla la instancia, la primera petición puede tardar unos 10 s (arranca la JVM). Los ping llegan a la app como `POST /actuator/health` gracias a `AWS_LWA_PASS_THROUGH_PATH` y no tienen costo apreciable (capa gratuita).
- **Por qué Lambda y no ECS:** es serverless y se factura por uso, así que puede quedar siempre disponible sin costo por estar encendida. El [Lambda Web Adapter](https://github.com/awslabs/aws-lambda-web-adapter) traduce los eventos de Lambda a HTTP, por lo que el código de la aplicación no cambia.
- **Seguridad:** la función usa un rol IAM de mínimo privilegio (solo las acciones de datos que usa la API, solo sobre la tabla `franchises`); no hay credenciales en el código ni en la imagen. La URL es pública y sin autenticación, como pide la prueba.

Para reproducir el despliegue:

```bash
P="--profile <tu-perfil> --region us-east-1"

# 1. Tabla DynamoDB (si aún no existe) y repositorio de imágenes
aws cloudformation deploy --template-file infra/cloudformation/dynamodb-table.yml --stack-name franchise-dynamodb $P
aws cloudformation deploy --template-file infra/cloudformation/ecr.yml --stack-name franchise-ecr $P

# 2. Construir y publicar la imagen (arm64)
REPO=<account-id>.dkr.ecr.us-east-1.amazonaws.com/franchise-api
aws ecr get-login-password $P | docker login --username AWS --password-stdin ${REPO%/*}
docker buildx build --platform linux/arm64 --provenance=false -f Dockerfile.lambda -t $REPO:v1 --push .

# 3. Función Lambda + URL pública
aws cloudformation deploy --template-file infra/cloudformation/lambda.yml --stack-name franchise-lambda \
  --capabilities CAPABILITY_IAM --parameter-overrides ImageUri=$REPO:v1 $P
aws cloudformation describe-stacks --stack-name franchise-lambda $P --query 'Stacks[0].Outputs'
```

Para eliminar todo: `aws cloudformation delete-stack --stack-name <franchise-lambda | franchise-ecr | franchise-dynamodb>` (vaciar antes el repositorio ECR).

## Estructura

```
src/main/java/com/juan/franchise/
├── domain/            # modelo, excepciones y puerto del repositorio
├── application/       # casos de uso
└── infrastructure/
    ├── entrypoint/rest/   # controlador, DTOs, manejo global de errores
    ├── persistence/       # adaptador DynamoDB
    └── config/            # cliente DynamoDB, OpenAPI
infra/cloudformation/  # IaC (tabla DynamoDB, ECR, Lambda)
Dockerfile             # imagen para ejecución local / docker-compose
Dockerfile.lambda      # imagen para AWS Lambda (incluye Lambda Web Adapter)
scripts/               # creación de tabla local
```
