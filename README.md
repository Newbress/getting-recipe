# getting-recipe

YouTube 쇼츠/영상 URL을 입력하면 Gemini API가 영상(음성+화면)을 직접 분석해 레시피를 생성하는 서비스.

> 이 문서는 1단계(프로젝트 셋업) 기준 초안이며, 8단계에서 아키텍처 다이어그램·API·브랜치 보호 설정 안내까지 완성한다.

## 실행 방법 (local)

```bash
cp .env.example .env        # 값 채우기 (DB 계정, GEMINI_API_KEY, YOUTUBE_API_KEY)
docker compose up -d        # PostgreSQL 16
./gradlew bootRun           # 기본 프로파일: local (.env 자동 로드)
```

## 테스트

```bash
./gradlew test              # Testcontainers(PostgreSQL) 사용 -> Docker 필요
```

Docker 29+에서는 Testcontainers 1.21.4 이상이 필요하다 (`build.gradle`에 반영됨).

## 브랜치 전략

- `main`: **운영 배포 전용**. 직접 push 금지, `dev`에서 PR(Merge commit)로만 병합
- `dev`: **개발 통합 전용**. 모든 작업 브랜치는 `dev`에서 분기해 `dev`로 PR(Squash merge)
- 작업 브랜치: `feat/{이슈번호}-{기능명}`, `fix/…`, `refactor/…`, `docs/…`, `test/…`, `chore/…`
- `hotfix/{버그명}`: 운영 긴급 수정만. `main`에서 분기 → `main`과 `dev` 양쪽에 병합

커밋 메시지: Conventional Commits (`type: 한글 요약`, 50자 이내).
