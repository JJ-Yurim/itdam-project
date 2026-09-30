# 단어 사전 시딩 절차 (develop)

`WordSeedRunner`는 애플리케이션 시작 시 서버에 배치된 NDJSON 파일로 단어 사전을 한 번 적재한다.

## 동작 방식

- 경로: `word.seed.path`, 기본값 `/app/data/seed/words.ndjson`
- 활성 단어가 한 건이라도 있으면 실행하지 않는다. A/B가 동시에 시작되어도 이미 적재된 환경에서는 건너뛴다.
- 파일이 없으면 로그만 남기고 기동은 계속한다.
- 각 줄 형식은 `{"word","partOfSpeech","definition","example(선택)"}`다. 잘못된 줄은 건너뛰고 rejected로 집계한다.
- 원본 데이터는 `word-datasets/words-001.ndjson`이다.

## EC2 배치 절차

두 컨테이너는 `backend-data:/app/data` 볼륨을 공유한다. 파일은 A 슬롯을 통해 한 번만 복사하고, 재기동은 한 슬롯씩 순차 수행한다.

```bash
# 1) 로컬에서 EC2로 복사
scp words-001.ndjson ubuntu@<EC2_HOST>:/tmp/

# 2) 공유 볼륨에 배치
docker exec backend-app-a mkdir -p /app/data/seed
docker cp /tmp/words-001.ndjson backend-app-a:/app/data/seed/words.ndjson

# 3) A를 먼저 재기동하고 healthy 확인
docker restart backend-app-a
until curl -fsS http://127.0.0.1:8081/actuator/health; do sleep 2; done

# 4) B 재기동 후 healthy 확인
docker restart backend-app-b
until curl -fsS http://127.0.0.1:8082/actuator/health; do sleep 2; done

# 5) 양쪽 로그 확인
for container in backend-app-a backend-app-b; do
  docker logs "$container" 2>&1 | grep "Word seed" || true
done
```

정상 적재 로그 예시는 `Word seed finished from /app/data/seed/words.ndjson: N imported, M rejected.`다. 먼저 시작한 컨테이너가 적재를 끝내면 다른 컨테이너의 idempotent runner는 활성 단어를 발견하고 건너뛸 수 있다.

마지막으로 로그인 세션으로 `GET /api/v1/daily-words/today`가 200과 5개 카드를 반환하는지 확인한다.

## 주의

- 공유 볼륨에 있으므로 컨테이너 교체 후에도 파일은 유지된다.
- 재적재가 필요하면 데이터 변경 절차와 백업을 먼저 승인받는다. 활성 단어가 있으면 runner는 실행되지 않는다.
- 두 컨테이너를 동시에 재시작하지 않는다. 한 슬롯의 health를 확인한 뒤 다음 슬롯을 재시작한다.
