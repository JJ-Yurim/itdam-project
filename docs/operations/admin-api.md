# 관리자 API 사용 절차

`/api/admin/**`는 `ADMIN` 역할의 세션으로만 접근할 수 있다. 변경 요청은 세션 쿠키와
`X-XSRF-TOKEN` 헤더가 모두 필요하다. Swagger 문서는 배포 Nginx의 Basic Auth로 추가 보호한다.

**일상 운영은 관리자 웹 콘솔(`/admin/raids`)을 쓴다.** 이 문서는 콘솔이 내부적으로 호출하는
요청 순서와, 콘솔로 처리할 수 없을 때의 수동 절차를 남긴 것이다.

관리자 엔드포인트 계열은 다섯 가지다.

| 경로 | 용도 |
| --- | --- |
| `/api/admin/bosses` | 보스 생성·수정 |
| `/api/admin/boss-runs` | 레이드 회차 시작·종료 |
| `/api/admin/boss-images` | 페이즈 이미지 업로드 |
| `/api/admin/books` | 생성된 책 공개·숨김 |
| `/api/admin/book-cover-images` | 책 표지 이미지 업로드 |

전체 요청·응답 스키마는 Swagger를 본다.

## 로그인

```bash
BASE=https://app.example.com
J=cookies.txt

CSRF=$(curl -s -c $J "$BASE/api/v1/csrf" | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s -b $J -c $J -X POST "$BASE/api/v1/auth/sessions" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"email":"<관리자 이메일>","password":"<비밀번호>"}'

# 로그인 후 회전된 CSRF 토큰 사용
CSRF=$(grep XSRF-TOKEN $J | tail -1 | awk '{print $NF}')
```

관리자 계정은 `AdminAccountSeeder`가 배포 환경의 계정 설정으로 생성한다. 기존 일반 사용자 계정을
자동 승격하지 않으며, 비밀번호와 세션·토큰을 로그에 남기지 않는다.

## 보스 페이즈 이미지 생성 순서

관리자 화면의 저장 버튼은 아래 요청들을 순서대로 실행한다. 화면에서는 한 번의 저장처럼 보이지만,
이미지 업로드가 끝난 뒤 반환된 `imageAssetId`를 최종 보스 생성 요청에 포함한다.

```bash
# PNG/JPEG/WebP, 각 5MB 이하
FIRST_IMAGE=$(curl -s -b $J -X POST "$BASE/api/admin/boss-images" \
  -H "X-XSRF-TOKEN: $CSRF" -F "image=@./phase-1.png" \
  | sed 's/.*"imageAssetId":\([0-9]*\).*/\1/')

SECOND_IMAGE=$(curl -s -b $J -X POST "$BASE/api/admin/boss-images" \
  -H "X-XSRF-TOKEN: $CSRF" -F "image=@./phase-2.webp" \
  | sed 's/.*"imageAssetId":\([0-9]*\).*/\1/')

curl -s -b $J -X POST "$BASE/api/admin/bosses" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"name\":\"보스 이름\",\"description\":\"보스 설명\",\"moods\":[
        {\"code\":\"JOY\",\"role\":\"THEME\",\"weight\":1.0}],
       \"phaseTemplates\":[
        {\"maxHp\":1700,\"hardMinScore\":40.00,\"recommendedScore\":60.00,
         \"damageMultiplier\":1.0,\"imageAssetId\":$FIRST_IMAGE},
        {\"maxHp\":2000,\"hardMinScore\":50.00,\"recommendedScore\":70.00,
         \"damageMultiplier\":1.2,\"imageAssetId\":$SECOND_IMAGE}]}"
```

`phaseTemplates`는 한 개 이상 필수이며 배열 순서가 페이즈 번호가 된다. 같은 이미지 ID는 한 목록에서
두 번 사용할 수 없다. 최초 생성에서는 현재 관리자가 업로드한 임시 이미지만 사용할 수 있다.

## 조회와 보스런 생성

```bash
curl -s -b $J "$BASE/api/admin/bosses?page=0&size=20"
curl -s -b $J "$BASE/api/admin/bosses/1/phase-templates"

curl -s -b $J -X POST "$BASE/api/admin/boss-runs" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"bossId":1,"endsAt":"2026-08-06T15:00:00Z","activate":true,
       "phases":[{"hardMinScore":40.00,"recommendedScore":60.00,"imageAssetId":1}]}'
```

페이즈 HP를 생략하면 `boss.run.hp.max-damage-cap`과
`boss.run.hp.target-min-valid-submissions-per-phase`로 계산한 기본값을 사용한다.

업로드 후 최종 생성 요청이 실패한 임시 이미지는 24시간 뒤 정리된다. 참조되지 않는 오래된 활성
이미지는 30일 뒤 정리 대상이 되며, 템플릿이나 보스런 스냅샷이 참조 중인 파일은 삭제하지 않는다.
