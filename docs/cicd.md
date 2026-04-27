# CI/CD — GitHub Actions ↔ Jenkins

## GitHub Actions konfigürasyonu

Bu repoda üç workflow var:

- `.github/workflows/backend.yml` — backend modülleri için matrix build (her servis ayrı job).
  Test başarılı olursa main branch'te Jib ile GHCR'a image push.
- `.github/workflows/frontend.yml` — Vite proje için lint + test + build, dist artifact
  upload.
- `.github/workflows/deploy.yml` — `v*` tag push ya da manual dispatch ile tetiklenir;
  ECR'a Jib build/push, Beanstalk'a `Dockerrun.aws.json` ile deploy, Slack notify.

## Jenkins ile karşılaştırma

| Konu | GitHub Actions | Jenkins |
|---|---|---|
| Pipeline tanımı | YAML in repo (`.github/workflows/*.yml`) | Groovy `Jenkinsfile` (declarative or scripted) |
| Tetikleyici | `on: push/pull_request/workflow_dispatch/schedule/tags` | `triggers`, `pollSCM`, webhooks |
| Çalışma ortamı | GitHub-hosted runners (ubuntu-latest, macos, windows) ya da self-hosted | Bağımsız Jenkins master + worker nodes |
| Cache & artifact | `actions/cache`, `actions/upload-artifact` | Built-in artifact archive + plugins |
| Secrets | `secrets.*`, OIDC ile bulut sağlayıcı federation | Credentials plugin (vault, file, secret text) |
| Matrix | `strategy.matrix` ile birden fazla job paralel | `parallel` step (declarative) ya da `for` (scripted) |
| Marketplace | `actions/...`, `aws-actions/...`, herhangi bir repo'dan action | Jenkins Plugin ekosistemi (1800+) |
| Cost | Public repo'da ücretsiz, private repo'da dakika başı | Self-hosted: donanım maliyeti + admin yükü |

## Aynı pipeline'ın Jenkinsfile karşılığı (örnek)

```groovy
pipeline {
  agent any
  tools { jdk 'temurin-21' }
  options { timestamps() }

  stages {
    stage('Backend matrix') {
      matrix {
        axes { axis { name 'MODULE'; values 'common','auth-service','product-service','cart-service','order-service','payment-service','notification-service','api-gateway' } }
        stages {
          stage('verify') {
            steps {
              dir('backend') {
                sh "mvn -B -pl ${MODULE} -am verify"
              }
            }
          }
        }
      }
    }

    stage('Frontend') {
      steps {
        dir('frontend') {
          sh 'npm ci && npm run lint && npm test && npm run build'
        }
      }
    }

    stage('Build images (main)') {
      when { branch 'main' }
      steps {
        withCredentials([usernamePassword(credentialsId: 'ghcr', usernameVariable: 'U', passwordVariable: 'P')]) {
          sh 'echo $P | docker login ghcr.io -u $U --password-stdin'
          dir('backend') {
            sh '''
              for s in api-gateway auth-service product-service cart-service order-service payment-service notification-service; do
                mvn -B -DskipTests -pl $s -am package
                mvn -B -DskipTests -pl $s -Djib.to.image=ghcr.io/n11/${s}:${BUILD_NUMBER} jib:build
              done
            '''
          }
        }
      }
    }

    stage('Deploy on tag') {
      when { tag 'v*' }
      steps {
        sh 'aws elasticbeanstalk update-environment ...'
      }
    }
  }

  post {
    success { slackSend(color: 'good',  message: "Build #${BUILD_NUMBER} OK — ${env.GIT_COMMIT}") }
    failure { slackSend(color: 'danger', message: "Build #${BUILD_NUMBER} FAILED — ${env.GIT_COMMIT}") }
  }
}
```

### Aynı kavramların eşleştirilmesi

| GitHub Actions | Jenkins |
|---|---|
| `jobs.<name>.steps[]` | `stage('name') { steps { ... } }` |
| `strategy.matrix` | `matrix { axes { axis { values ... } } }` |
| `if: github.ref == 'refs/heads/main'` | `when { branch 'main' }` |
| `secrets.X` | `credentials('id')` ya da `withCredentials` |
| `actions/cache@v4` | Maven local repo on agent ya da Pipeline Maven Integration plugin |
| `aws-actions/configure-aws-credentials` (OIDC) | AWS Credentials plugin / IAM instance profile |
| `slack-notify` step | `slackSend` (Slack Notification plugin) |

## Neden bu projede GitHub Actions tercih edildi?

- Projeyle birlikte versiyonlanan pipeline (PR review'da workflow değişikliği görünür).
- Public repo ödemesiz minutes.
- Marketplace'tan AWS OIDC, Jib, Slack, Maven cache action'ları hazır.
- Ek altyapı yok — Jenkins master + worker'a bakım gerekmiyor.
