# 관리자는 별도 테이블·전용 로그인으로 분리한다

ADR 0005는 이용자·호스트·관리자를 `accounts` 1개 + 역할(`MEMBER`, `HOST`, `ADMIN`)로 통합했다. 이용자 SSR(Epic #23)이 끝난 뒤 관리자 콘솔(#43)이 필요해졌는데, 현재 구조에서는 통합 계정의 `ADMIN` 역할로 로그인하므로 자격증명 침해 시 일반 계정에서 최고 권한까지 한 단계다. 레거시는 관리자 전용 계정·로그인으로 경계가 있었고, 그 경계가 필요하다. 이 결정은 ADR 0005를 **부분 개정**한다.

## 0005에서 바뀌는 것

- **바뀐다**: `ADMIN` 역할은 `accounts`에서 제거한다. 관리자는 `admin_users`(이메일·Argon2id 비밀번호·이름·닉네임) 전용 테이블을 쓴다. 로그인 식별자는 여전히 email(대소문자 무시 unique), 비밀번호는 Argon2id로 신규와 동일하다.
- **바뀌지 않는다**: 이용자/호스트 통합. 한 계정이 `MEMBER`와 `HOST`를 함께 보유할 수 있다. `sanctions` 등 감사 컬럼의 `admin_id`는 `admin_users.id`를 가리킨다.
- **바뀐다**: 관리자 전용 `/admin/login` GET/POST와 `/admin/**` 전용 체인을 추가한다. 관리자는 `/login` 인증이 실패하고(일반 `accounts`에만 조회한다), 일반 회원은 `/admin/login` 인증이 실패한다.
- **바뀌지 않는다**: 단일 `ADMIN` 권한. 세부 관리자 권한(영역별 분리)은 후속이다.
- **바뀌지 않는다**: 기존 두 체인. `/api/**`는 헤더 JWT 전용·CSRF 없음, 나머지는 쿠키 JWT + CSRF·`STATELESS`다.

## 체인 세 개

순서는 `@Order(1)` API, `@Order(2)` 관리자, `@Order(3)` HTML이다.

| 항목 | API 체인 | 관리자 체인 | HTML 체인 |
|---|---|---|---|
| 대상 | `/api/**`, `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` | `/admin/**` | 나머지 전부(`/**`) |
| 인증 수단 | `Authorization: Bearer` 헤더만 | `access_token` 쿠키만 | `access_token` 쿠키만 |
| CSRF | 끔 | 켬(`CookieCsrfTokenRepository`) | 켬 |
| 세션 | `STATELESS` | `STATELESS` | `STATELESS` |
| 토큰 발급 | `TokenService`(`refresh_tokens`) | `AdminTokenService`(`admin_refresh_tokens`) | `TokenService` |
| 401·403 처리 | `ProblemDetail` JSON + `@PreAuthorize` 403 | 미인증은 `/admin/login?next=` 302, 인증됐으나 `ROLE_ADMIN`이 아니면 403 | `/login?next=` 302 |

- 관리자 access 토큰의 `roles`는 항상 `["ADMIN"]`이고 subject는 `admin_users.id`다. `/api/v1/admin/**`의 `@PreAuthorize("hasRole('ADMIN')")`는 그대로 동작한다. 일반 `TokenService`는 `MEMBER`/`HOST`만 발급하므로 회원 토큰이 `ADMIN`을 갖지 않는다.
- 관리자 쿠키는 `admin_access_token`/`admin_refresh_token`으로 회원 쿠키(`access_token`/`refresh_token`)와 이름을 분리한다. 한 브라우저에 두 세션이 있어도 서로의 토큰을 읽지 않는다. 또 회원 HTML 체인은 `MEMBER`/`HOST` 역할만 허용하므로, 관리자 토큰이 회원 쿠키에 섞여도 회원 화면에서 403이다. `admin_users.id`와 `accounts.id`가 같은 숫자여도 서로 다른 테이블의 주체로만 취급된다.
- 관리자 체인의 bearer 리졸버는 관리자 쿠키를 우선하고, 없으면 회원 쿠키를 읽는다. 회원이 `/admin/**`에 닿으면 인증은 되지만 `ROLE_ADMIN`이 없어 403이다(로그인 화면으로 리다이렉트되지 않는다).
- 관리자 체인도 투명 갱신(`AdminAccessTokenRefreshFilter`, 유예 10초)을 쓴다. refresh가 만료·폐기되면 쿠키를 지우고 `/admin/login?expired=1`로 보낸다.
- `/admin/**` 비관리자는 403으로 거부한다(콘솔 URL 유출 방지). 비로그인은 `/admin/login`으로 보낸다.

## 레이아웃·팝업 대체

- 콘솔 레이아웃은 `templates/admin/layout.html`이다. 좌측 사이드바 5개 그룹(공지관리/신고관리/회원관리/마일리지관리/공간관리) + 상단 헤더로 레거시 Admin_Sidebar 메뉴 구조를 그대로 옮긴다.
- 스타일은 SB Admin 2 재이식(레거시 자산) + Bootstrap 4 공존. 이용자 화면의 `app.css`와 별도 번들이다.
- 레거시 `*Popup.jsp` 12개는 `/admin/**/{id}` 상세 페이지로 대체한다. 브라우저 팝업을 쓰지 않는다.
- URL은 전부 `/admin/**` 신규 경로다. 레거시 `.action` 리다이렉트를 만들지 않는다.

## 정리

- `accounts`의 `ADMIN` 역할·시드·테스트는 깨끗이 제거했다(구경로 호환 없음). 새 DB는 `docker compose down -v`로 갱신한다.
- 기존 DB 업그레이드: V4가 `accounts`의 `ADMIN` 역할 계정을 같은 id로 `admin_users`에 복사해 감사 컬럼(`admin_id`)의 귀속을 보존하고, ADMIN 역할이 없던 비정상 `admin_id`는 NULL로 남긴다. 복사 후 identity 시퀀스를 최대 id 뒤로 옮긴다. 업그레이드 경로는 `AdminMigrationUpgradeTest`가 V3→V4로 검증한다.
- `AccountRole`에서 `ADMIN`을 삭제했다. `ActorType.ADMIN`(예약 이력 감사)은 유지한다.
- 관리자 시드 계정은 로컬 프로필에서 발급한다(`AdminSeeder`가 `admin_users`에 insert-if-absent).
- 기존 DB에 남은 관리자 `accounts` 행은 삭제하지 않는다. 대신 `AuthService`가 `admin_users`에 있는 이메일의 일반 로그인·가입을 거부하므로, 구 관리자 계정으로 `/login` 토큰을 받을 수 없다. 관리자 접근은 `/admin/login`으로만 가능하다.

## 대안

- **통합 유지 + 경로 분리만**: `ADMIN` 역할을 유지하고 `/admin/**`만 막으면 침해 시 한 단계라는 문제는 그대로 남는다.
- **관리자도 회원가입 경로**: 일반 가입으로 만들면 자격증명 유출 면적이 넓어진다.

## 결과

- HTML 컨트롤러는 `com.lookation.view`에 둔다(ADR 0009). API용 `Admin*Controller`(REST)와 이름이 충돌하지 않도록 뷰 컨트롤러는 `*ViewController`로 둔다.
- 대시보드는 통계 API 없이 기존 목록 API의 처리 대기 건수만 표시한다(검수·신고·충전·출금).
