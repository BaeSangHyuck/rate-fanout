# rate-fanout

외부 호출과 동시 요청이 얽히는 백엔드 문제 세 가지를 실제 서버와 DB 트랜잭션으로 돌려 보는 데모입니다. 화면에서 방식을 바꿔 실행하면 서버가 처리한 결과가 그대로 나옵니다.

| 화면 | 문제 | 비교하는 방식 |
|---|---|---|
| 대출 조건 병렬 조회 | 금융사 12곳 호출을 가장 느린 호출 수준으로 묶고, 한 곳의 실패가 전체 실패로 번지지 않게 | 순차 vs 코루틴 병렬, 타임아웃·재시도·동시 호출 제한 |
| 주문 재고 정합성 | 여러 POS에서 같은 상품을 동시에 주문할 때 재고가 어긋나지 않게, 결제 실패 시 재고 복구, 이벤트 중복 처리 | 락 없음 vs 비관적 락, 보상 트랜잭션, 트랜잭셔널 아웃박스 |
| 선착순 쿠폰 발급 | 요청이 몰릴 때 한정 수량을 넘기지 않고, 같은 사용자에게 두 번 주지 않게 | 락 없음 vs 비관적 락 vs 조건부 UPDATE vs Redis 게이트 |

- 데모: https://rate-fanout.vercel.app
- API: https://rate-fanout-api.onrender.com (무료 인스턴스라 한동안 요청이 없으면 첫 응답이 30초~1분 걸립니다)

## 스택

Kotlin 2.4 · Spring Boot 4.2 (WebFlux) · Kotlin Coroutines · WebClient · Spring Data JPA (Hibernate 7) · Flyway · PostgreSQL (Neon) / H2 · Redis (Redisson) · Kafka (spring-kafka) · Micrometer Prometheus · JUnit 5 · Testcontainers · k6 · GitHub Actions

## 구조

도메인별로 Gradle 모듈을 나눈 모듈러 모놀리스입니다. 헥사고날 구조로 나눴습니다.

- **domain 모듈**: 규칙과 포트(인터페이스)만 둡니다. Spring 의존성이 없습니다.
- **application 모듈**: 유스케이스(트랜잭션 경계)와 어댑터(JPA, Redis, Kafka, WebClient)로 포트를 구현합니다. 엔티티는 `adapter/persistence`에 두고 도메인 객체와 분리했습니다. 저장소를 JDBC에서 JPA로 바꿀 때 domain과 서비스 코드는 건드리지 않고 어댑터만 갈아 끼웠습니다.
- **monolith 모듈**: 세 도메인을 묶어 하나의 서버로 띄웁니다.

도메인끼리는 서로 참조하지 않습니다.

```
api/
  libs/common                       logger(), blockingIo (블로킹 DB 호출을 IO 디스패처로), AssignedIdEntity(Persistable)
  domain/quote/quote-domain         Quote·InstitutionResult, 금리순 정렬 규칙, InstitutionClient 포트
  domain/quote/quote-application    QuoteAggregator(순차·병렬, 타임아웃, 재시도, 동시 호출 제한), HttpInstitutionClient(WebClient)
  domain/order/order-domain         Stock(0 밑으로 못 내려감), StockRepository·OutboxRepository·OrderEventPublisher 포트
  domain/order/order-application    StockCommandService(@Transactional 재고 변경 + 아웃박스 기록)
                                    OrderSimulationService(동시 주문 → 결제 → 실패 시 보상 트랜잭션)
                                    OrderEventOutbox(at-least-once 릴레이), OrderEventConsumer(eventId로 중복 처리 방지)
                                    JPA 어댑터, Kafka 발행·리스너 / 브로커 없을 때 직접 전달
  domain/coupon/coupon-domain       Coupon(남은 수량 규칙), AlreadyIssuedException, CouponRepository·CouponIssueGate 포트
  domain/coupon/coupon-application  CouponCommandService(락 없음 / 비관적 락 / 조건부 UPDATE), CouponIssueService
                                    JPA 어댑터, RedisCouponIssueGate(Redisson + Lua)
  monolith/fanout-app               부트 앱, <도메인>/presentation/restapi 컨트롤러, 모의 금융사 API, Flyway 마이그레이션
web/   정적 화면 (Vercel): index.html, orders.html, coupon.html
loadtest/            k6 부하 테스트 (coupon-issue.js, quote.js), 결과는 docs/load-test.md
docker-compose.yml   로컬 Postgres·Redis·Kafka
```

모의 금융사 API는 같은 서버 안에 있지만 **실제 HTTP로** 호출합니다. 같은 `scenario` 번호면 기관별 지연과 에러가 똑같이 재현되므로 순차와 병렬을 같은 조건에서 비교할 수 있습니다. 기관 10%는 빠르게 에러를 내고, 10%는 2.5~5초 늦게 응답하며, 나머지는 0.25~1.55초 안에 응답합니다.

## 1. 대출 조건 병렬 조회

### API

```
GET /api/quotes?strategy=PARALLEL&timeoutMs=2000&retries=1&concurrency=12&scenario=7
```

| 파라미터 | 기본값 | 범위 | 설명 |
|---|---|---|---|
| strategy | PARALLEL | SEQUENTIAL, PARALLEL | 호출 방식 |
| timeoutMs | 2000 | 100–10000 | 기관별 호출 1회 타임아웃 |
| retries | 1 | 0–2 | 에러 응답 시 재시도 횟수 |
| concurrency | 12 | 1–12 | 동시에 나가는 호출 수 상한 |
| scenario | 1 | 1–1000000 | 모의 기관의 지연·에러 시드 |

응답에는 전체 소요 시간(`totalMs`), 성공 기관의 금리순 목록(`offers`), 기관별 호출 기록(`results[].attempts`, 요청 시작 기준 ms)이 담깁니다.

### 설계에서 고른 것

**병렬 호출은 코루틴 `async` + `awaitAll`.** 호출은 WebClient(`awaitBody`)라서 응답을 기다리는 동안 스레드를 잡지 않습니다. 12곳을 동시에 기다려도 스레드 12개가 필요하지 않습니다.

**타임아웃은 호출마다 `withTimeout`.** 늦는 기관 하나가 전체 응답을 붙잡지 않게 합니다.

**에러만 재시도하고 타임아웃은 재시도하지 않음.** 빠른 에러는 다시 물어볼 시간이 있지만, 이미 타임아웃까지 기다린 기관을 다시 기다리면 전체 응답이 그만큼 늦어집니다.

**실패를 예외가 아니라 결과로 담음.** `awaitAll`은 자식 하나가 예외를 던지면 나머지를 모두 취소합니다. 그래서 기관 호출 안에서 실패를 잡아 `ERROR`/`TIMEOUT` 결과로 바꾸고, 성공한 기관만으로 응답합니다(부분 응답). 다만 `CancellationException`은 삼키지 않고 다시 던집니다. 요청 자체가 취소됐을 때 코루틴이 멈추지 않는 문제를 막기 위해서입니다.

**동시 호출 수는 `Semaphore`로 제한.** 외부 기관의 호출 제한이나 커넥션 풀을 넘지 않게 합니다.

## 2. 주문 재고 정합성

```
POST /api/orders/run?mode=PESSIMISTIC&orders=20&stock=10&paymentFailPct=20&publishFailPct=25&gapMs=30&seed=1
```

주문마다 `재고 차감 → 커밋 → 외부 결제 승인` 순서로 처리하고, 승인이 실패하면 재고를 되돌립니다. 실행마다 상품 행을 새로 만들고 끝나면 지웁니다.

| 파라미터 | 기본값 | 범위 | 설명 |
|---|---|---|---|
| mode | PESSIMISTIC | NONE, PESSIMISTIC | 재고 차감 방식 |
| orders | 20 | 1–60 | 동시 주문 수 |
| stock | 10 | 1–60 | 초기 재고 |
| paymentFailPct | 20 | 0–50 | 결제 승인 실패율(%) |
| publishFailPct | 25 | 0–50 | 이벤트 발행 실패율(%). 절반은 전달 실패, 절반은 전달 후 응답 유실 |
| gapMs | 30 | 0–100 | 재고 조회와 저장 사이 처리 시간 |
| seed | 1 | 1–1000000 | 결제 지연·실패와 발행 실패 시드 |

**락 없이 조회 후 저장하면 차감이 사라짐.** 두 주문이 같은 재고 값을 읽고 각자 1을 빼서 저장하면 차감 하나가 덮어써집니다(lost update). 재고 10개에 20건을 넣으면 결제 완료가 재고보다 많아집니다.

**비관적 락(`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`).** 같은 상품의 차감을 줄 세웁니다. 읽은 엔티티 값만 바꾸고 커밋 때 dirty checking으로 update가 나갑니다. 결제 승인은 트랜잭션 밖에서 기다려서, 외부 응답을 기다리는 동안 락과 커넥션을 잡지 않습니다.

**보상 트랜잭션.** 외부 승인은 DB 트랜잭션으로 묶을 수 없어서, 실패하면 이미 커밋한 차감을 반대 연산(재고 +1)으로 되돌립니다.

**트랜잭셔널 아웃박스.** 재고 변경과 같은 트랜잭션에 이벤트를 기록하므로, 재고는 바뀌었는데 이벤트가 사라지는 일이 없습니다. 릴레이는 발행에 실패하면 남겨 두고 다음 주기에 다시 보내고, 전달은 됐지만 응답을 못 받으면 같은 이벤트를 또 보냅니다(at-least-once). 컨슈머는 `eventId` 유니크 키로 중복을 걸러 이벤트마다 한 번만 처리합니다. 실서비스에서는 릴레이가 스케줄러로 돌지만, 데모에서는 결과를 한 번에 보여 주려고 주문이 끝난 뒤 같은 요청 안에서 돌립니다.

**Kafka로 보내기.** 발행은 `OrderEventPublisher` 포트 뒤에 있습니다. `APP_KAFKA_ENABLED=true`면 `runId`를 키로 Kafka 토픽에 보내고 브로커 ack까지 기다리며, ack를 못 받으면 published를 찍지 않아 다음 라운드에 다시 보냅니다. `@KafkaListener` 컨슈머가 `processed_event` PK로 중복을 거릅니다. 무료 배포 서버에는 브로커가 없어서 같은 컨슈머를 프로세스 안에서 바로 부르는 어댑터로 돕니다. 컨슈머 동작은 같습니다.

**`Persistable`로 insert 보장.** id를 직접 넣는 엔티티에 `save()`를 쓰면 Spring Data가 merge로 처리해 select가 한 번 더 나가고, 이미 있는 id면 insert 대신 update가 됩니다. 그러면 중복 이벤트가 PK 위반으로 걸리지 않습니다. 그래서 새 엔티티는 `isNew()`가 true를 돌려 persist로 가게 했습니다.

## 3. 선착순 쿠폰 발급

```
POST /api/coupons/run?mode=CONDITIONAL&requests=300&users=250&quantity=100&gapMs=5
```

| 파라미터 | 기본값 | 범위 | 설명 |
|---|---|---|---|
| mode | CONDITIONAL | NONE, PESSIMISTIC, CONDITIONAL, REDIS | 발급 방식. REDIS는 Redis가 연결된 서버에서만(아니면 503) |
| requests | 300 | 1–1000 | 동시 요청 수 |
| users | 250 | 1–1000 | 사용자 수. 요청보다 적으면 같은 사용자가 연달아 누른 요청이 생긴다 |
| quantity | 100 | 1–1000 | 발급 수량 |
| gapMs | 5 | 0–20 | 발급 자격 확인 등 처리 시간 |

**조건부 UPDATE.** `UPDATE coupon SET remaining = remaining - 1 WHERE id = ? AND remaining > 0` 한 문장으로 확인과 차감을 같이 합니다. 갱신된 행이 0이면 품절입니다. 처리 시간을 락 밖에서 쓰고, 행 잠금은 이 문장 동안만 잡습니다.

**비관적 락과의 차이.** 둘 다 수량을 지키지만, 비관적 락은 처리 시간 동안 쿠폰 행 하나의 락을 잡고 있어 모든 요청이 한 줄로 섭니다. 요청이 몰릴수록 대기 시간이 처리 시간 × 요청 수로 늘어납니다.

**중복 발급은 유니크 키로.** (쿠폰, 사용자) 유니크 키에 걸리면 트랜잭션이 롤백돼 차감도 함께 되돌아갑니다. `saveAndFlush`로 그 자리에서 flush해 제약 위반을 도메인 예외(`AlreadyIssuedException`)로 바꿉니다.

**Redis 게이트.** 중복 확인(SISMEMBER), 수량 확인, 차감(DECR), 사용자 기록(SADD)을 Lua 스크립트 하나로 실행합니다. Redis는 스크립트를 한 번에 하나씩 실행하므로 사이에 다른 요청이 끼어들지 않습니다. 통과한 요청(수량만큼)만 DB 트랜잭션까지 가고, 나머지는 DB 커넥션을 잡지 않고 바로 돌아갑니다. DB 쪽은 조건부 UPDATE와 유니크 키로 한 번 더 지킵니다.

### 로컬 실측

기본값으로 3회 실행했습니다(H2 인메모리, 커넥션 10개, JDBC 어댑터 시절 측정). Postgres·Redis 게이트 수치는 아직 측정하지 않았습니다.

| | 주문: 판매 / 재고 10 | 주문: DB 재고 = 기대 재고 | 쿠폰: 발급 / 수량 100 | 쿠폰 대기 p95 |
|---|---|---|---|---|
| 락 없음 | 12–16건 (초과 판매) | 불일치 3/3 | 250건 (초과 발급) | 177–288ms |
| 비관적 락 | 8–9건 | 일치 3/3 | 100건 | 1603–1713ms |
| 조건부 UPDATE | - | - | 100건 | 159–165ms |

## 부하 테스트

GitHub Actions 러너(4 vCPU)에 Postgres·Redis와 함께 띄우고 k6로 초당 요청 수를 단계적으로 올렸습니다. 전체 결과와 원인 분석, 대응은 [docs/load-test.md](docs/load-test.md)에 있습니다.

- **쿠폰 발급**(수량 5,000, 목표 초당 1,500건)
  - 비관적 락은 초당 140~170건에서 막혀 요청의 51%가 10초를 넘겼습니다. 락을 기다리는 요청이 커넥션 10개를 모두 쥐고, 대기가 54건에 묶였습니다.
  - 조건부 UPDATE와 Redis 게이트는 초당 883건을 실패 없이 처리했습니다. p95는 각각 8ms, 7ms였습니다.
  - 모든 실행에서 초과 발급은 0건이었습니다.
- **대출 조회 fan-out**: 초당 60건에서 기관 호출 타임아웃이 기준선 11.7%에서 44.6%로 올랐습니다.
  - CPU와 스레드에는 여유가 있었습니다. 모의 기관을 분리하고 구간별로 시간을 재 보니, 원인은 WebClient 커넥션 풀 부족이었습니다.
  - 리틀의 법칙으로 필요한 커넥션 수(약 900)를 계산해 풀을 2000으로 늘렸습니다. 이후 초당 120건까지 기준선과 같았습니다(타임아웃 11.2%, p95 2.33초).

## 테스트

```
cd api && ./gradlew test
```

- `StockTest`, `CouponTest`: Spring 없이 도메인 규칙만 검증합니다(0 밑으로 차감 불가, 보상 시 복구).
- `QuoteAggregatorTest`: `runTest` 가상 시간으로 돌려 실제로 기다리지 않고 ms 단위로 검증합니다.
  - 순차는 지연의 합(1600ms), 병렬은 가장 느린 호출(800ms)
  - 동시 호출 2개 제한 시 세 번째 호출이 300ms에 시작
  - 타임아웃 1초면 4초짜리 기관을 기다리지 않고 1000ms에 응답, 나머지 2곳으로 결과 반환
  - 에러 후 재시도 성공, 재시도를 다 써도 전체 요청은 실패하지 않음
- `ApiApplicationTests`: 실제 서버를 띄워 HTTP로 12곳을 호출합니다. 같은 시나리오에서 병렬이 순차보다 3배 넘게 빠르고 기관별 결과가 같은지, 잘못된 파라미터에 400을 주는지, `/actuator/prometheus`에 HTTP 지표가 나오는지 확인합니다.
- `SimulationTests`: 실제 DB 트랜잭션으로 돌립니다.
  - 비관적 락이면 DB 재고 = 초기 재고 − 결제 완료, 락이 없으면 어긋남
  - 발행 실패율 50%에서도 남은 이벤트 0건, 컨슈머는 이벤트마다 정확히 한 번 처리, 중복 전달은 무시
  - 조건부 UPDATE와 비관적 락은 정확히 수량만큼 발급, 락이 없으면 초과 발급
  - Redis가 없는 서버에서 REDIS 모드는 503
- `InfraIntegrationTests`: Testcontainers로 Postgres·Redis·Kafka를 띄워 같은 시나리오를 돌립니다. Flyway 마이그레이션이 Postgres에서 돌고, 비관적 락·lost update 재현, Kafka 중복 전달 후 정확히 한 번 처리, Redis 게이트로 정확히 수량만큼 발급되는지 확인합니다. 도커가 없으면 건너뛰고, GitHub Actions(`.github/workflows/test.yml`)에서 돕니다.

대출 조회 로컬 실측(시나리오 7, 타임아웃 2초, 재시도 1회): 순차 13.2초, 병렬 1.76초.

## 실행

```
cd api && ./gradlew :monolith:fanout-app:bootRun   # http://localhost:8080, 인메모리 H2
# 화면: web/index.html 을 열고 ?api=http://localhost:8080 를 붙인다 (orders.html, coupon.html 도 같다)
```

배포 구성(Postgres + Redis + Kafka)으로 띄우려면 `docker compose up -d` 뒤 `docker-compose.yml` 맨 위 주석의 환경변수로 실행합니다. 부하 테스트는 `k6 run -e BASE=http://localhost:8080 -e MODE=CONDITIONAL loadtest/coupon-issue.js`, 또는 GitHub Actions의 `loadtest` 워크플로를 수동 실행합니다.

### 배포

- DB: Neon 무료 Postgres. 쓰지 않으면 컴퓨트가 잠들었다가 첫 연결 때 자동으로 깨어납니다(Supabase 무료 프로젝트는 일주일 쓰지 않으면 일시정지돼 직접 다시 켜야 합니다). Render와 같은 지역(Oregon, `aws-us-west-2`)에 만들고 pooler가 아닌 direct 연결 주소를 씁니다.
- API: Render Blueprint(`render.yaml`). 웹 서비스와 무료 Key Value(Redis)를 같이 만들고, Neon 접속 정보는 Blueprint 생성 때 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`로 입력합니다.
- Kafka는 무료로 상시 띄울 곳이 없어 배포 서버에서는 끄고, 로컬 compose와 CI 통합 테스트로 확인합니다.

## 아직 하지 않은 것

- 배포 서버의 Kafka: 무료 브로커가 없어 라이브 데모에서는 프로세스 안 전달로 돈다
- 아웃박스 릴레이 스케줄러: 지금은 요청 안에서 한 번 돈다
- 결과 캐시: 같은 사용자가 짧은 시간에 다시 조회하면 기관 호출을 생략하는 캐시(Redis TTL)
- 서킷 브레이커: 계속 실패하는 기관은 일정 시간 호출 자체를 건너뛰기
- QueryDSL: 조회가 단건·집계뿐이라 Spring Data 쿼리 메서드와 JPQL로 충분했다
