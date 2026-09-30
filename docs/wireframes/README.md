# 잇담 와이어프레임 분리본

원본 `figma-preview.html`을 화면별 정적 HTML로 분리한 결과다. 디자인 수정 시 공통 스타일은 `assets/styles.css`에서 관리한다.

## 파일 구성

| 기능 | 파일 | 범위 |
| --- | --- | --- |
| 홈 | `home.html` | MVP |
| 작업실 · 문장 작성/평가 | `workshop.html` | MVP |
| 도서관 | `library.html` | MVP |
| 명예의 전당 | `hall-of-fame.html` | 후속 |
| 도서 상세 | `book-detail.html` | MVP |
| 도서 상세 · 댓글 | `book-detail-comments.html` | 후속 |
| 공동 도서 | `collaborative-book.html` | MVP |
| 마이페이지 | `my-page.html` | 후속 |
| 내 서고 | `my-archive.html` | MVP |
| 프로필 드로어 | `profile-drawer.html` | MVP 일부 |
| 레이드 메인 | `raid-main.html` | MVP 핵심 |
| 레이드 상세 | `raid-detail.html` | MVP 핵심 |
| 레이드 전투 | `raid-battle.html` | MVP 핵심 |
| 레이드 클리어 | `raid-clear.html` | MVP 핵심 |
| 베스트 문장 | `best-sentences.html` | 후속 |
| 알림 설정 | `notification-settings.html` | 후속 |
| 서비스 설정 | `service-settings.html` | 후속 |

## 사용 방법

1. `index.html`을 열어 전체 화면 목록을 확인한다.
2. 각 화면 HTML은 독립적으로 열 수 있다.
3. 공통 디자인은 `assets/styles.css`를 수정한다.
4. 이 파일들은 디자인 참고용 정적 와이어프레임이며 React 구현 파일은 아니다.
