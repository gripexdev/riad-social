# Contributing

This repo enforces CI + SonarCloud Quality Gates on every push. Follow the steps below to keep checks green.

## Quality Gate expectations
- No new Reliability issues (must be rating A)
- New code coverage >= 80%
- No new Security Hotspots

## Local checks (required before push)
Backend:
```
./backend/mvnw -q test
```

Frontend:
```
cd frontend
npm test -- --watch=false --code-coverage
```

Or use the PowerShell helpers from the repo root:
```
./scripts/ci-backend.ps1
./scripts/ci-frontend.ps1
./scripts/ci-all.ps1
```

## Pre-push hook (recommended)
This repo ships a pre-push hook that runs tests only for the parts you changed.

Setup (Windows PowerShell):
```
./scripts/setup-git-hooks.ps1
```

Setup (macOS/Linux):
```
git config core.hooksPath .githooks
chmod +x .githooks/pre-push
```

Skip hook when needed:
```
SKIP_PRE_PUSH=1 git push
```

## SonarCloud (optional local scan)
Use this when you want the same analysis as CI.

Backend:
```
./backend/mvnw -B verify org.sonarsource.scanner.maven:sonar \
  -Dsonar.projectKey=$SONAR_PROJECT_KEY_BACKEND \
  -Dsonar.organization=$SONAR_ORGANIZATION \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

Frontend (requires sonar-scanner):
```
cd frontend
npx sonar-scanner \
  -Dsonar.projectKey=$SONAR_PROJECT_KEY_FRONTEND \
  -Dsonar.organization=$SONAR_ORGANIZATION \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.sources=src \
  -Dsonar.exclusions=**/*.spec.ts \
  -Dsonar.coverage.exclusions=src/test.ts \
  -Dsonar.javascript.lcov.reportPaths=coverage/frontend/lcov.info \
  -Dsonar.typescript.lcov.reportPaths=coverage/frontend/lcov.info
```

## Common pitfalls (and how to avoid them)
- Integer overflow warnings: use `1024L * 1024` instead of `1024 * 1024` when assigning to long.
- Missing tests on new code: add unit tests for services/controllers touching new logic.
- Optional chaining / null rules in Angular templates: align template types with component nullability.
- Resource handling: ensure files are readable/exist checks are strict (`exists && readable`).

## If CI fails
1) Open the SonarCloud report and filter "New Code".
2) Fix Reliability issues first (these gate the build).
3) Run local tests again, then push.
