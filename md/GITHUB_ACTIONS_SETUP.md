# GitHub Actions CI/CD 설정 가이드

이 문서는 GitHub Actions를 사용하여 자동 배포를 설정하는 방법을 설명합니다.

## 개요

main 브랜치에 PR이 머지되면 자동으로:
1. 백엔드 Docker 이미지 빌드 및 ECR 푸시
2. ECR에 이미 존재하는 Python 이미지 URI 사용
3. CDK를 통한 AWS 인프라 배포

**중요**: Python 이미지는 별도 레포지토리에서 빌드되어 ECR에 이미 존재해야 합니다.

## 사전 요구사항

1. AWS 계정 및 IAM 역할 설정 (OIDC)
2. GitHub Secrets 설정
3. Python 이미지가 ECR에 빌드되어 있어야 함

## 1. AWS IAM 역할 설정 (OIDC)

GitHub Actions가 AWS에 접근하기 위해 OIDC를 사용합니다.

### 1.1 IAM 역할 생성

AWS 콘솔에서 다음 정책을 가진 IAM 역할을 생성하세요:

**신뢰 관계 (Trust Policy):**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::YOUR_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_USERNAME/YOUR_REPO_NAME:*"
        }
      }
    }
  ]
}
```

**권한 정책 (Permissions Policy):**
다음 권한이 필요합니다:
- ECR: `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:CreateRepository`, `ecr:DescribeRepositories`
- ECS: `ecs:UpdateService`, `ecs:DescribeServices`, `ecs:ListServices`, `ecs:ListClusters`
- CloudFormation/CDK: `cloudformation:*`, `s3:*`, `iam:*`, `ec2:*`, `ecs:*`, `rds:*`, `secretsmanager:*`, `acm:*`, `elasticloadbalancing:*`, `logs:*`, `ssm:*`

또는 다음 관리형 정책을 사용할 수 있습니다:
- `PowerUserAccess` (개발 환경용, 프로덕션에서는 최소 권한 원칙 적용)

**권장**: 개발 환경에서는 `PowerUserAccess` 정책을 사용하는 것이 간단하고 편리합니다. 이미 생성된 IAM 역할에 `PowerUserAccess` 정책을 연결하면 됩니다.

### 1.2 OIDC Provider 생성 (최초 1회)

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

## 2. GitHub Secrets 설정

GitHub 저장소의 Settings > Secrets and variables > Actions에서 다음 Secrets를 추가하세요:

### 필수 Secrets

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `AWS_ROLE_ARN` | AWS IAM 역할 ARN | `arn:aws:iam::YOUR_ACCOUNT_ID:role/github-actions-role` |
| `ECR_REPOSITORY_NAME` | ECR 리포지토리 이름 (백엔드, 선택적) | `final-be` (기본값) |
| `PYTHON_IMAGE_URI` | Python 이미지 URI (ECR에 이미 빌드되어 있어야 함) | `YOUR_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/final-py:latest` |
| `JWT_SECRET` | JWT 토큰 시크릿 키 | `your-jwt-secret-key` |
| `FRONT_URL` | 프론트엔드 URL | `https://your-frontend.com` |
| `INTERNAL_TOKEN` | 내부 서비스 통신 토큰 | `your-internal-token` |
| `CERTIFICATE_ID` | ACM 인증서 ID | `your-certificate-id` |

### 선택적 Secrets

| Secret 이름 | 설명 | 기본값 |
|------------|------|--------|
| `ECS_CLUSTER_NAME` | ECS 클러스터 이름 | `Cluster` |
| `ECS_BACKEND_SERVICE_NAME` | Backend ECS 서비스 이름 | - |
| `ECS_PYTHON_SERVICE_NAME` | Python ECS 서비스 이름 | - |
| `ECS_DESIRED_COUNT` | ECS 서비스 desired count | `1` |

## 3. Python 이미지 설정

**중요**: Python 이미지는 별도 레포지토리에서 빌드되어 ECR에 이미 존재해야 합니다.

1. Python 프로젝트의 CI/CD에서 이미지를 빌드하고 ECR에 푸시하세요
2. GitHub Secrets에 `PYTHON_IMAGE_URI`를 설정하세요 (예: `YOUR_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/final-py:latest`)
3. 백엔드 workflow는 ECR에 이미 존재하는 Python 이미지를 사용합니다

## 4. Workflow 파일 확인

`.github/workflows/deploy.yml` 파일이 올바르게 생성되었는지 확인하세요.

## 5. 테스트

### 5.1 수동 실행

GitHub Actions 탭에서 "Deploy to AWS" workflow를 선택하고 "Run workflow" 버튼을 클릭하여 수동으로 실행할 수 있습니다.

### 5.2 자동 실행

main 브랜치에 PR을 머지하면 자동으로 실행됩니다.

## 6. 트러블슈팅

### 6.1 OIDC 인증 실패

- IAM 역할의 Trust Policy에서 GitHub 저장소 이름이 정확한지 확인
- OIDC Provider가 올바르게 생성되었는지 확인

### 6.2 ECR 푸시 실패

- IAM 역할에 ECR 권한이 있는지 확인
- ECR 리포지토리가 존재하는지 확인 (자동 생성되지만 권한이 필요)

### 6.3 CDK 배포 실패

- GitHub Secrets의 모든 필수 값이 설정되었는지 확인
- CDK 스택이 이미 배포되어 있는지 확인 (최초 배포는 수동으로 해야 할 수 있음)

### 6.4 Python 이미지 URI 오류

- `PYTHON_IMAGE_URI` Secret이 올바르게 설정되었는지 확인
- Python 이미지가 ECR에 실제로 존재하는지 확인
- 이미지 URI 형식이 올바른지 확인 (예: `ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/REPO_NAME:TAG`)

## 7. 보안 권장사항

1. **최소 권한 원칙**: IAM 역할에 필요한 최소한의 권한만 부여
2. **Secrets 관리**: 민감한 정보는 GitHub Secrets에 저장
3. **환경 분리**: 개발/스테이징/프로덕션 환경별로 별도의 IAM 역할 사용
4. **정기 검토**: IAM 역할 권한을 정기적으로 검토하고 불필요한 권한 제거

## 8. 추가 설정

### 8.1 배포 알림 설정

Slack, Discord 등으로 배포 알림을 받으려면 workflow에 알림 단계를 추가하세요.

### 8.2 롤백 자동화

배포 실패 시 자동 롤백을 구현하려면 CDK의 `deploymentCircuitBreaker`를 활용하세요 (이미 설정되어 있음).

## 참고 자료

- [GitHub Actions OIDC](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services)
- [AWS CDK GitHub Actions](https://docs.aws.amazon.com/cdk/v2/guide/cdk_pipeline.html)
- [AWS ECR GitHub Actions](https://github.com/aws-actions/amazon-ecr-login)

