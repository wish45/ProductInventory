# 재고 관리 시스템 MVP

상품 입고, 출고, 재고 조회 API입니다. 동시 요청이 몰려도 재고가 음수가 되거나 초과 출고가 나지 않는 것을 가장 중요하게 보고 설계했습니다.

호출하는 쪽이 사람이 아니라 프로그램(현장 디바이스, 연동 서버)이라고 가정했습니다. 요청이 한꺼번에 몰리고, 네트워크가 끊겨 같은 요청이 다시 오고, 실패를 사람이 읽는 메시지가 아니라 코드로 분기해야 합니다. 그래서 동시성 정합성 외에 멱등 키, 기계가 읽는 에러 형식(ProblemDetail + `code`), 헬스 체크를 기본으로 넣었습니다.

## 목표와 제약

과제에서 요구한 기능은 입고(미등록 상품이면 신규 등록), 출고(재고 음수 금지), 현재 재고 조회 세 가지입니다. 여기에 동시 입출고 요청에서도 재고 정합성을 지키고, 잘못된 요청에는 명확한 에러를 돌려줘야 합니다. 환경은 Java 21, Spring Boot 4.1, PostgreSQL입니다.

정합성 목표는 `ConcurrencyTest`로 고정해 두었습니다.

- 재고 100인 상품에 1개 출고 1,000건을 동시에 보내면 성공 100건, 409 900건, 최종 재고 0. 초과 출고는 0건.
- 같은 신규 상품 코드로 입고 100건을 동시에 보내면 상품은 1행만 생기고 수량은 합계.
- 입고와 출고가 섞여 동시에 들어와도 최종 재고 = 초기 + 입고 합 - 성공한 출고 합. 5xx 없음.
- 모든 동시성 테스트 끝에 현재 수량 = 입고 합 - 출고 합인지 이력과 대조.

응답 시간이나 처리량은 측정하지 않았습니다. 위 테스트는 정합성 확인이지 성능 측정이 아닙니다.

## 실행 방법

Docker(Desktop)와 JDK 17 이상이 필요합니다. 빌드에 쓰는 JDK 21은 Gradle 툴체인이 자동으로 내려받고, QueryDSL Q클래스는 컴파일할 때 생성됩니다.

```bash
# 테스트. Testcontainers가 PostgreSQL 18 컨테이너를 띄웁니다 (H2 안 씀)
./gradlew test

# 로컬 실행. compose.yaml의 PostgreSQL을 Spring Boot가 띄우고 연결합니다
./gradlew bootRun

# jar로 실행할 때
docker compose up -d
./gradlew bootJar && java -jar build/libs/inventory-0.0.1-SNAPSHOT.jar
```

실행 후 확인:

```bash
curl -X POST localhost:8080/api/products/inbound -H 'Content-Type: application/json' -d '{"code":"SKU-1","name":"상품 A","quantity":10}'
curl -X POST localhost:8080/api/products/1/outbound -H 'Content-Type: application/json' -d '{"quantity":3}'
curl localhost:8080/api/products/1
```

- 헬스 체크: `http://localhost:8080/actuator/health` (DB 연결 포함. 다른 Actuator 엔드포인트는 기본값대로 닫혀 있습니다)
- API 문서(Swagger UI): `http://localhost:8080/swagger-ui.html`, OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- DDL: [`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql). Flyway가 기동할 때 적용합니다.
- 로컬 DB 접속 정보는 [`compose.yaml`](compose.yaml)과 [`application.yml`](src/main/resources/application.yml)에 있습니다 (`localhost:5432/inventory`, `inventory` / `inventory`). `bootRun`과 테스트에서는 compose 지원과 Testcontainers가 컨테이너의 접속 정보를 주입하므로 이 값은 jar로 실행할 때만 쓰이고, `SPRING_DATASOURCE_URL` 같은 환경 변수로 덮어쓸 수 있습니다.

## API 명세

| 메서드 | 경로 | 요청 | 성공 | 실패 |
|---|---|---|---|---|
| `POST` | `/api/products/inbound` | `{"code":"SKU-1","name":"상품 A","quantity":10}` (선택 헤더 `Idempotency-Key`) | `200` 상품 현재 상태 | `400` |
| `POST` | `/api/products/{id}/outbound` | `{"quantity":3}` (선택 헤더 `Idempotency-Key`) | `200` 상품 현재 상태 | `400` `404` `409` |
| `GET` | `/api/products` | 쿼리 `page`(0부터), `size`(1~100, 기본 20) | `200` 상품 페이지, 최근 등록순 | `400` |
| `GET` | `/api/products/{id}` | 없음 | `200` 상품 현재 상태 | `404` |
| `GET` | `/api/products/{id}/history` | 쿼리 `type`(INBOUND/OUTBOUND, 선택), `page`(0부터), `size`(1~100, 기본 20) | `200` 이력 페이지, 최신순 | `400` `404` |

같은 내용을 Swagger UI(`/swagger-ui.html`)와 OpenAPI JSON(`/v3/api-docs`)으로도 볼 수 있습니다. 각 API의 설명과 에러 코드는 컨트롤러의 OpenAPI 어노테이션에 있고, `OpenApiDocsTest`가 다섯 API가 모두 실려 있는지 확인합니다.

성공 응답(공통):

```json
{ "id": 1, "code": "SKU-1", "name": "상품 A", "quantity": 7 }
```

입고와 출고 요청에는 `Idempotency-Key` 헤더(64자 이하)를 붙일 수 있습니다. 오프라인이었던 디바이스가 쌓아 둔 요청을 다시 보내거나 연동 서버가 타임아웃 뒤 재시도하는 상황을 위한 것입니다. 같은 키로 재시도해도 두 번 반영되지 않고, 처음 반영됐을 때의 응답을 그대로 돌려줍니다. 키는 `stock_history.request_id`(UNIQUE)에 저장되고, 헤더가 없으면 매 요청이 그대로 반영됩니다.

이력 응답:

```json
{
  "content": [
    { "id": 3, "type": "INBOUND",  "quantity": 5, "quantityAfter": 12, "createdAt": "2026-10-20T20:30:12.345+09:00" },
    { "id": 2, "type": "OUTBOUND", "quantity": 3, "quantityAfter": 7,  "createdAt": "2026-10-20T20:30:11.901+09:00" }
  ],
  "page": 0, "size": 20, "totalElements": 3
}
```

상품 목록 응답도 같은 페이지 형식이고 `content`에 상품 현재 상태가 들어갑니다.

이력 순서는 `id` 기준입니다. `created_at`의 `now()`는 트랜잭션 시작 시각이라, 행 잠금을 기다렸다가 반영된 요청의 시각이 먼저 반영된 요청보다 앞설 수 있습니다. `id`는 재고 행 잠금을 잡은 뒤에 발급되므로 실제 반영 순서와 같습니다.

에러 응답은 RFC 9457 `ProblemDetail` 형식에 `code`를 더해 통일했습니다. 검증 실패(400)에는 어떤 값이 왜 틀렸는지 알려주는 `errors`(`field`, `message`) 목록이 함께 갑니다. 본문 필드는 필드명, 헤더와 쿼리 파라미터는 클라이언트가 보낸 이름(`Idempotency-Key`, `size`)입니다.

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 수량이 1~1,000,000 범위 밖, 코드나 이름 누락, 본문 형식 오류, 목록과 이력 조회의 `page`/`size`/`type` 오류, 64자를 넘는 `Idempotency-Key` |
| 404 | `PRODUCT_NOT_FOUND` | 없는 상품 id |
| 409 | `INSUFFICIENT_STOCK` | 재고 부족 (`requested`, `available` 포함) |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 오류. 내부 메시지는 로그에만 남기고 응답에는 넣지 않습니다 |

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "재고가 부족합니다.",
  "instance": "/api/products/1/outbound",
  "code": "INSUFFICIENT_STOCK",
  "requested": 5,
  "available": 3
}
```

## 가정 (과제 원문에 없어서 정한 것)

| 모호한 점 | 결정 | 이유 |
|---|---|---|
| "등록되지 않은 상품"을 무엇으로 판별하나 | 상품 코드(`code`, UNIQUE)를 추가하고 입고 요청의 키로 쓴다. 출고와 조회는 `id` | 미등록 상품에는 아직 `id`가 없고, 상품명은 동명 상품이 있을 수 있어 키로 약하다. 원문이 "필요에 따라 변경"을 허용한다 |
| 이미 있는 `code`로 다른 `name`이 들어오면 | 기존 이름을 유지하고 수량만 더한다 | 이름 변경은 입고가 아니라 별도 상품 수정 API의 몫이다 |
| 수량 범위 | 요청 1건당 1 ~ 1,000,000의 정수 | 0과 음수는 400으로 거절. 정수 오버플로 방지 |

## 프로젝트 구조

단일 모듈의 계층형 구조입니다. 기능이 늘면 각 계층 패키지에 클래스를 더하고, 더 커지면 계층을 그대로 모듈(`controller / service / domain / repository`)로 올립니다.

```
com.deepfine.inventory
├── common/       횡단 관심사: ApiExceptionHandler(ProblemDetail), ErrorCode, QuerydslConfig, OpenApiConfig
├── controller/   HTTP 계약: ProductController
├── dto/          요청/응답 record: InboundRequest, OutboundRequest, ProductResponse, StockHistoryResponse
├── service/      ProductService(진입점, 멱등 재조회는 트랜잭션 밖), StockCommandService(재고 변경 + 이력 기록 트랜잭션)
├── domain/       엔티티 Product, StockHistory, StockHistoryType, StockChange, PagedResult, 도메인 예외 2개 (Q클래스는 빌드 시 생성)
└── repository/   StockRepository(재고 숫자를 바꾸는 SQL이 있는 유일한 곳. 재고를 바꾸는 두 문장은 네이티브 SQL + RETURNING, 조회는 QueryDSL), StockHistoryRepository(이력 기록과 조회)
```

의존 방향은 `controller → service → repository`이고, `domain`은 모두가 공유하며, `dto`는 `controller`만 씁니다(서비스는 DTO 대신 원시 값을 받습니다). 이 방향은 문서가 아니라 ArchUnit 테스트(`ArchitectureTest`)가 강제합니다. 컨트롤러가 리포지토리를 직접 부르는 식으로 어기면 `./gradlew test`가 실패합니다. `service`가 `repository`의 구현을 직접 쓰는 것은 의도한 선택입니다. 구현이 하나뿐인데 포트 인터페이스를 두면 이름만 하나 늘어나므로, 두 번째 구현(예: Redis 원자 차감)이 생길 때 뽑을 생각입니다.

## 설계 판단

재고 정합성이 깨지는 전형적인 경로는 읽고, 계산하고, 쓰는 사이에 다른 요청이 끼어드는 갱신 손실입니다. JPA 엔티티를 읽어서 `quantity -= n` 하고 더티 체킹으로 UPDATE하는 방식이 딱 이 구조라서, 락 없이 동시에 실행되면 초과 출고가 납니다.

그래서 재고를 바꾸는 작업은 SQL 한 문장으로 끝내고, 락은 애플리케이션이 아니라 PostgreSQL의 행 잠금에 맡겼습니다.

```sql
-- 출고: 조건부 UPDATE. 갱신된 행이 없으면 "없는 상품" 또는 "재고 부족". RETURNING으로 갱신 결과를 같은 왕복에 받는다
UPDATE product SET quantity = quantity - :q, updated_at = now()
 WHERE id = :id AND quantity >= :q
RETURNING *;

-- 입고: 신규면 등록, 기존이면 증가. 원자적
INSERT INTO product (code, name, quantity) VALUES (:code, :name, :q)
ON CONFLICT (code) DO UPDATE
   SET quantity = product.quantity + EXCLUDED.quantity, updated_at = now()
RETURNING *;
```

- READ COMMITTED에서 두 트랜잭션이 같은 행을 UPDATE하면, 뒤에 온 쪽은 앞 트랜잭션의 커밋을 기다린 뒤 갱신된 행으로 `WHERE`를 다시 평가합니다 ([PostgreSQL 문서 13.2.1](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED)). 그래서 `quantity >= :q`는 항상 최신 재고 기준으로 검사되고 초과 출고가 생기지 않습니다.
- `INSERT … ON CONFLICT DO UPDATE`는 동시에 들어와도 INSERT 또는 UPDATE 중 하나의 결과를 원자적으로 보장합니다 ([PostgreSQL 문서 INSERT](https://www.postgresql.org/docs/18/sql-insert.html#SQL-ON-CONFLICT)). 같은 신규 상품에 입고가 동시에 들어와도 상품이 두 번 만들어지지 않습니다.
- 데이터 접근은 JPA + QueryDSL이고, 재고를 바꾸는 두 문장만 네이티브 SQL입니다. JPQL에는 `ON CONFLICT`도 `RETURNING`도 없습니다. 네이티브로 두면 두 문장 모두 `RETURNING *`로 갱신된 행을 같은 왕복에 받아 엔티티로 매핑하므로, 행 잠금을 쥔 채 다시 읽지 않습니다. 조회, 페이지, 동적 조건은 QueryDSL입니다. "UPDATE 해 보고 0행이면 INSERT, 고유 제약에 걸리면 재시도" 방식은 트랜잭션을 다시 시작해야 해서 경쟁 처리를 한 번 더 설계해야 하므로 택하지 않았습니다.
- `Product` 엔티티에는 `decrease()` 같은 재고 변경 메서드를 일부러 두지 않았습니다. 엔티티를 읽어 계산하고 더티 체킹으로 UPDATE하는 방식은 락 없이는 갱신 손실이 나기 때문입니다.
- 멱등 키는 두 겹으로 처리합니다. 트랜잭션 안에서 먼저 같은 키의 이력을 찾아 있으면 그 결과를 돌려주고(일반적인 재시도), 그 검사를 둘 다 통과할 만큼 동시에 들어온 같은 키 요청은 `request_id`의 UNIQUE 제약이 막습니다. 이때 두 번째 트랜잭션은 재고 변경까지 통째로 롤백되고, 롤백된 트랜잭션 안에서는 다시 조회할 수 없으므로 트랜잭션 밖(`ProductService`)에서 같은 키의 결과를 다시 읽어 돌려줍니다. 트랜잭션 경계를 `StockCommandService`로 분리한 이유가 이것입니다.
- `CHECK (quantity >= 0)`를 DDL에 두어, 애플리케이션 코드에 버그가 생겨도 DB가 마지막으로 막습니다.
- 재고 변경과 `stock_history` INSERT는 한 트랜잭션에서 처리합니다. 행 잠금은 커밋까지 유지되므로 트랜잭션 안에서 외부 호출을 하지 않고 짧게 끝냅니다.
- 재고 숫자를 바꾸는 SQL은 `repository/StockRepository` 한 곳에만 둡니다. 동시성 처리 방식을 바꿔야 할 때 이 클래스만 고치면 됩니다.

이력이 기준이고 수량은 파생 값입니다. `stock_history`에 모든 변경이 쌓이고 `product.quantity`는 조회를 빠르게 하려고 둔 값입니다. 둘이 어긋날 수 있다는 것이 이 구조의 약점이라서, 동시성 테스트마다 수량 = 입고 합 - 출고 합을 확인합니다.

### 대안 비교

| 방식 | 정합성 | 단점 | 판단 |
|---|---|---|---|
| 조건부 UPDATE / UPSERT | 보장 | 여러 상품 동시 차감처럼 조건이 복잡해지면 SQL 한 문장으로 쓰기 어렵다 | 채택. 요구가 단일 행 증감이라 가장 단순하고 왕복 1회 |
| 비관적 락 `SELECT … FOR UPDATE` | 보장 | 왕복 2회, 락을 잡은 채 애플리케이션 로직 실행 | 여러 행을 읽고 판단해야 할 때 |
| 낙관적 락 `@Version` + 재시도 | 보장 | 재고는 같은 행에 요청이 몰려 충돌과 재시도가 폭증 | 충돌이 드문 데이터에 맞음 |
| Redis 분산 락 | 조건부 | 장애점이 늘고, 락 만료 중에 지연되면 깨진다. 결국 DB 조건이 최종 방어선 | DB가 하나라 불필요 |

채택안에서 가장 민감한 곳은 `WHERE quantity >= :q` 조건 하나가 초과 출고를 막는다는 점이고, 동시 출고 테스트가 그걸 고정합니다. 단순함과 왕복 1회를 얻는 대신 여러 상품을 한 번에 차감하는 복합 조건은 한 문장으로 쓰기 어려운데, 그 요구가 오면 id 순서로 `FOR UPDATE`를 거는 방식으로 바꾸면 됩니다. 한 상품에 요청이 몰려 행 잠금 대기가 길어지는 핫 로우 문제는 아래 확장 고려에 적었습니다.

## 공통 처리 전략

- 입력 검증: 요청 본문은 record에 `@NotBlank`, `@Min(1)`, `@Max(1_000_000)`과 `@Valid`, 쿼리 파라미터와 헤더는 메서드 파라미터 제약. 어기면 전부 400. 메서드 파라미터에 제약이 있으면 Spring MVC가 메서드 검증을 적용해 본문 오류도 `HandlerMethodValidationException`으로 오므로, 핸들러는 이 예외에서 `errors` 목록을 만든다
- 에러 응답: `@RestControllerAdvice`가 모든 예외를 `ProblemDetail`로 바꾸고 `code`(enum)를 붙인다. 예상하지 못한 예외도 500 `INTERNAL_ERROR`로 같은 형식이고 내부 메시지는 로그에만 남긴다. 컨트롤러에는 try/catch가 없다
- 운영 가시성: Actuator `/actuator/health`만 노출한다(DB 연결 포함). 다른 엔드포인트는 기본값대로 닫혀 있다
- 트랜잭션: 재고 변경과 이력 기록은 `StockCommandService`의 `@Transactional` 한 단위, 조회는 `readOnly = true`. 멱등 키 재조회만 트랜잭션 밖(`ProductService`)에서 한다
- 스키마: Flyway 마이그레이션으로만 관리한다. Hibernate는 `ddl-auto=validate`라서 기동할 때 엔티티 매핑과 실제 스키마가 어긋나면 바로 실패한다. `open-in-view=false`로 트랜잭션 밖에서 영속성 컨텍스트가 열려 있지 않게 했다
- 데이터 접근: 조회는 QueryDSL(타입 안전, 동적 조건), 재고를 바꾸는 두 문장은 네이티브 SQL + `RETURNING`. 원조 `com.querydsl`은 5.1.0(2024년 1월) 이후 릴리스가 멈춰 있어서, Spring Data 문서가 안내하는 OpenFeign 포크(`io.github.openfeign.querydsl` 7.7, Hibernate 7 대상)를 쓴다. Q클래스는 `querydsl-apt`가 빌드할 때 생성한다. PostgreSQL 중심 설계에는 쓰기와 읽기를 한 DSL로 통일할 수 있는 jOOQ가 더 맞지만, 빌드 시 코드 생성 설정 비용과 써 본 적 없는 도구라는 점 때문에 택하지 않았다. 네이티브 문장이 지금의 둘을 넘어 쌓이면 가장 먼저 검토할 대안이다
- 구조: 패키지 의존 방향은 ArchUnit 테스트로 고정한다 (프로젝트 구조 절 참고)

## 테스트

Testcontainers로 실제 PostgreSQL 18에서 검증합니다. H2를 쓰지 않는 이유는 이 설계의 정합성이 PostgreSQL의 행 잠금과 재평가 동작에 기대고 있어서, 다른 DB로는 증명이 안 되기 때문입니다. 전체 39건이고 모두 통과합니다.

| 테스트 | 시나리오 | 기대 |
|---|---|---|
| 동시 출고 | 재고 100, 1개씩 출고 1,000건 동시 | 성공 100, 409 900, 최종 0, 출고 이력 100 |
| 동시 신규 입고 | 같은 code로 수량 1~100을 100건 동시 | 상품 1행, 수량 5,050, 입고 이력 100 |
| 입출고 혼합 | 재고 50에 입고 1개 100건 + 출고 1개 200건 동시 | 응답은 200/409뿐, 최종 = 50 + 100 - 성공한 출고 수, 이력 수 일치 |
| 대조 검증 | 위 세 테스트 끝 | 수량 = 입고 합 - 출고 합, 마지막 이력의 `quantity_after` = 수량 |
| API (10건) | 입고, 출고, 조회 정상 경로와 0, 범위 초과, 빈 코드, 없는 id, 재고 부족. 400에는 틀린 필드명 | 200 / 400 / 404 / 409 |
| 이력 조회 (6건) | 최신순, type 필터, 페이지, 없는 상품, size/type 오류 | 200 / 404 / 400 |
| 상품 목록 (3건) | 최근 등록순, 페이지, size 오류 | 200 / 400 |
| 멱등 키 (5건) | 같은 키 2회(입고, 출고)는 1회만 반영되고 같은 응답, 다른 키는 각각 반영, 같은 키 20건 동시에도 1회만 반영, 65자 키는 400 | 200 / 400 |
| 스키마 | Flyway 적용, `CHECK (quantity >= 0)` | 테이블 생성, 음수 INSERT 실패 |
| 아키텍처 | 패키지 의존 방향 5개 규칙 (ArchUnit) | 위반 시 실패. 컨트롤러에 리포지토리 필드를 넣어 실제로 실패하는 걸 확인하고 되돌림 |
| OpenAPI (2건) | `/v3/api-docs`에 다섯 API 경로, Swagger UI 응답 | 200 |
| 에러 형식 | 처리되지 않은 예외 | 500 `ProblemDetail` + `INTERNAL_ERROR`, 내부 메시지 미노출 |
| 헬스 체크 | `/actuator/health` | `UP` |

동시 실행은 가상 스레드 1,000개를 `CountDownLatch`로 같은 출발선에 세운 뒤 HTTP로 실제 API를 호출합니다. 동시에 열어 두는 연결은 200개(톰캣 기본 워커 수)로 제한했습니다. 한순간에 1,000개 연결을 열면 OS 연결 대기열이 넘쳐서 환경에 따라 연결이 거절되는데, 그건 DB 정합성과 무관한 실패이기 때문입니다. 테스트 데이터도 입고 API로 만들고 DB에 직접 INSERT하지 않습니다.

한 가지 짚어 두면, 1,000건을 동시에 보내도 DB에 동시에 들어가는 요청은 커넥션 풀 크기(HikariCP 기본 10)만큼입니다. 이 테스트는 풀 크기만큼의 동시 트랜잭션에서 정합성이 깨지지 않는다는 걸 보이는 것이지 처리량을 재는 것이 아닙니다.

## 범위 밖

- 멀티모듈 구조: MVP에는 과하다고 봐서 단일 모듈로 뒀습니다. 커지면 지금의 계층 패키지(`controller / service / domain / repository`)를 그대로 모듈로 올리면 됩니다.
- Redis, 메시지 큐, 인증: 요구 밖입니다.
- 여러 상품을 한 번에 출고: 하게 되면 id 정렬 순서로 잠가 데드락을 피하고, 전부 성공하거나 전부 실패하게 합니다.
- 창고나 옵션별 재고, 예약 후 확정 모델: 아래 확장 고려에 방향만 적었습니다.
- 재고를 캐시하지 않습니다. 캐시가 10인데 실제 재고가 0이면 초과 주문이 나므로, 재고 숫자는 항상 DB에서 읽습니다.

## 확장 고려

- 핫 로우: 한 상품에 요청이 몰리면 재고 행을 여러 개로 쪼개거나 `(상품, 창고)` 단위 `stock` 테이블로 분리합니다. 이번에 분리하지 않은 이유는, 재고가 `product`에 있어야 신규 상품 입고가 UPSERT 한 문장으로 원자적으로 끝나기 때문입니다. 분리하면 상품 UPSERT 뒤에 재고 UPSERT, 두 문장이 되어 동시 신규 입고의 경쟁을 한 번 더 설계해야 합니다.
- 후속 처리: 알림이나 정산 같은 후속 작업은 Outbox와 이벤트로 트랜잭션 밖으로 뺍니다.
- 수평 확장: 애플리케이션은 상태가 없어 수평 확장이 되고, 정합성은 DB가 보장합니다. 그래도 병목이면 Redis 원자 차감 + 비동기 DB 반영, 또는 대기열로 바꿉니다. 재고를 바꾸는 SQL이 `StockRepository` 한 곳에 있어서 바꿀 범위가 좁습니다.
- 재고 상태 모델: 예약과 확정이 필요해지면 `stock_history.type`에 `RESERVE / CONFIRM / RELEASE`를 추가합니다.
- 멱등 키 만료: 지금은 키를 영구 보관합니다. 이력이 쌓이면 키 조회 비용과 저장 공간이 늘어나므로, 운영에서는 보관 기간(예: 24시간)을 정하고 만료된 키의 재요청은 새 요청으로 취급하는 정책이 필요합니다.
- 요구사항을 조절할 수 있다면: "입고 시 미등록 상품이면 신규 등록"을 상품 등록 API와 분리하면 동시 신규 등록 경쟁 자체가 사라집니다. 이번에는 원문대로 구현했습니다.
- UPSERT는 충돌로 UPDATE가 되어도 identity 값을 소비하므로 `product.id`에 빈 번호가 생길 수 있습니다. 기능에는 영향이 없습니다.
