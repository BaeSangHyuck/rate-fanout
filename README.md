# rate-fanout

금융사 12곳에 대출 조건을 묻고, 모인 결과를 금리순으로 돌려주는 API입니다. 기관마다 응답 시간이 다르고 일부는 에러를 내거나 늦게 응답하는 상황에서, **전체 응답 시간을 가장 느린 호출 수준으로 묶고 한 기관의 실패가 전체 실패로 번지지 않게** 만드는 것이 목표입니다.

- 데모: (배포 후 Vercel 주소)
- API: (배포 후 Render 주소)/api/quotes

## 스택

Kotlin 2.4 · Spring Boot 4.2 (WebFlux) · Kotlin Coroutines · WebClient · JUnit 5 · kotlinx-coroutines-test

## 구조

```
api/   Spring Boot 서버
  quote/QuoteAggregator.kt      순차·병렬 호출, 타임아웃, 재시도, 동시 호출 제한
  quote/InstitutionClient.kt    WebClient로 금융사 API 호출 (논블로킹)
  quote/QuoteController.kt      GET /api/quotes
  mock/MockInstitutionController.kt  가상의 금융사 API (지연·에러 재현)
web/   정적 화면 (Vercel)
```

모의 금융사 API는 같은 서버 안에 있지만 **실제 HTTP로** 호출합니다. 같은 `scenario` 번호면 기관별 지연과 에러가 똑같이 재현되므로 순차와 병렬을 같은 조건에서 비교할 수 있습니다. 기관 10%는 빠르게 에러를 내고, 10%는 2.5~5초 늦게 응답하며, 나머지는 0.25~1.55초 안에 응답합니다.

## API

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

## 설계에서 고른 것

**병렬 호출은 코루틴 `async` + `awaitAll`.** 호출은 WebClient(`awaitBody`)라서 응답을 기다리는 동안 스레드를 잡지 않습니다. 12곳을 동시에 기다려도 스레드 12개가 필요하지 않습니다.

**타임아웃은 호출마다 `withTimeout`.** 늦는 기관 하나가 전체 응답을 붙잡지 않게 합니다.

**에러만 재시도하고 타임아웃은 재시도하지 않음.** 빠른 에러는 다시 물어볼 시간이 있지만, 이미 타임아웃까지 기다린 기관을 다시 기다리면 전체 응답이 그만큼 늦어집니다.

**실패를 예외가 아니라 결과로 담음.** `awaitAll`은 자식 하나가 예외를 던지면 나머지를 모두 취소합니다. 그래서 기관 호출 안에서 실패를 잡아 `ERROR`/`TIMEOUT` 결과로 바꾸고, 성공한 기관만으로 응답합니다(부분 응답). 다만 `CancellationException`은 삼키지 않고 다시 던집니다. 요청 자체가 취소됐을 때 코루틴이 멈추지 않는 문제를 막기 위해서입니다.

**동시 호출 수는 `Semaphore`로 제한.** 외부 기관의 호출 제한이나 커넥션 풀을 넘지 않게 합니다.

## 테스트

```
cd api && ./gradlew test
```

- `QuoteAggregatorTest`: `runTest` 가상 시간으로 돌려 실제로 기다리지 않고 ms 단위로 검증합니다.
  - 순차는 지연의 합(1600ms), 병렬은 가장 느린 호출(800ms)
  - 동시 호출 2개 제한 시 세 번째 호출이 300ms에 시작
  - 타임아웃 1초면 4초짜리 기관을 기다리지 않고 1000ms에 응답, 나머지 2곳으로 결과 반환
  - 에러 후 재시도 성공, 재시도를 다 써도 전체 요청은 실패하지 않음
- `ApiApplicationTests`: 실제 서버를 띄워 HTTP로 12곳을 호출합니다. 같은 시나리오에서 병렬이 순차보다 3배 넘게 빠르고 기관별 결과가 같은지, 잘못된 파라미터에 400을 주는지 확인합니다.

로컬 실측(시나리오 7, 타임아웃 2초, 재시도 1회): 순차 13.2초, 병렬 1.76초.

## 실행

```
cd api && ./gradlew bootRun          # http://localhost:8080
# 화면: web/index.html 을 열고 ?api=http://localhost:8080 를 붙인다
```

## 아직 하지 않은 것

- 결과 캐시: 같은 사용자가 짧은 시간에 다시 조회하면 기관 호출을 생략하는 캐시(Redis TTL)
- 서킷 브레이커: 계속 실패하는 기관은 일정 시간 호출 자체를 건너뛰기
- 부하 테스트: 동시 사용자 수에 따른 처리량·p95 (k6)
