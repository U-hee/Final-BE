## 태스크 수 설정 방법

### 방법 1: 기본값 사용 (권장)
기본값이 이미 **1**로 설정되어 있으므로, 별도 설정 없이 배포하면 됩니다:

```bash
cd infra
npx cdk deploy
```

### 방법 2: 명시적으로 파라미터 전달
태스크 수를 명시적으로 지정하려면:

```bash
cd infra
npx cdk deploy --parameters EcsDesiredCount=1
```

### 방법 3: 여러 태스크 실행
고가용성을 위해 여러 태스크를 실행하려면:

```bash
cd infra
npx cdk deploy --parameters EcsDesiredCount=2
```

## 배포 전 필수 확인 사항

### 1. 환경 변수 설정

프로젝트 루트의 `.env` 파일 또는 배포 시 환경 변수 설정:

```bash
# 필수
BACKEND_IMAGE_URI=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-be:latest
PYTHON_IMAGE_URI=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-python:latest

# 선택사항 (기본값 사용 가능)
JWT_SECRET=your-secret-key
FRONT_URL=https://your-vercel-app.vercel.app
INTERNAL_TOKEN=your-internal-token-here

# PYTHON_URL은 설정하지 않으면 자동으로 내부 ALB URL이 사용됩니다
```

### 2. AWS 자격 증명 확인

```bash
aws sts get-caller-identity
```

### 3. CDK Bootstrap 확인

```bash
cd infra
npx cdk bootstrap aws://$AWS_ACCOUNT_ID/$AWS_REGION
```

## 배포 명령어

### 기본 배포 (태스크 수 1개)

```bash
cd infra

# Windows (CMD)
set BACKEND_IMAGE_URI=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-be:latest
set PYTHON_IMAGE_URI=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-python:latest
set JWT_SECRET=your-secret-key
set FRONT_URL=https://your-vercel-app.vercel.app
set INTERNAL_TOKEN=your-internal-token-here
npx cdk deploy

# Linux/Mac
export BACKEND_IMAGE_URI="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-be:latest"
export PYTHON_IMAGE_URI="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-python:latest"
export JWT_SECRET="your-secret-key"
export FRONT_URL="https://your-vercel-app.vercel.app"
export INTERNAL_TOKEN="your-internal-token-here"
npx cdk deploy
```

### 태스크 수 명시적 지정

```bash
# 태스크 수 1개
npx cdk deploy --parameters EcsDesiredCount=1

# 태스크 수 2개 (고가용성)
npx cdk deploy --parameters EcsDesiredCount=2

# 태스크 중지 (비용 절감)
npx cdk deploy --parameters EcsDesiredCount=0
```

## 배포 후 확인

### 1. 스택 상태 확인

```bash
aws cloudformation describe-stacks \
  --stack-name FinalBeInfra \
  --query "Stacks[0].StackStatus" \
  --region ap-northeast-2
```

### 2. ECS 서비스 상태 확인

```bash
# Backend 서비스
aws ecs describe-services \
  --cluster Cluster \
  --services BE-Service \
  --region ap-northeast-2 \
  --query "services[0].{desiredCount:desiredCount,runningCount:runningCount}"

# Python 서비스
aws ecs describe-services \
  --cluster Cluster \
  --services Python-Service \
  --region ap-northeast-2 \
  --query "services[0].{desiredCount:desiredCount,runningCount:runningCount}"
```

### 3. 태스크 수 확인

```bash
# Backend 태스크
aws ecs list-tasks \
  --cluster Cluster \
  --service-name BE-Service \
  --region ap-northeast-2 \
  --query "taskArns | length(@)"

# Python 태스크
aws ecs list-tasks \
  --cluster Cluster \
  --service-name Python-Service \
  --region ap-northeast-2 \
  --query "taskArns | length(@)"
```

## Python 서비스 배포 전 준비사항

### 1. Python 서비스 Docker 이미지 빌드 및 ECR 푸시

Python 프로젝트에서 다음 명령어를 실행하여 ECR에 이미지를 푸시해야 합니다:

```bash
# Python 프로젝트 디렉토리에서
docker build -t final-python:latest .
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com
docker tag final-python:latest 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-python:latest
docker push 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/final-python:latest
```

### 2. ECR 리포지토리 생성 (없는 경우)

```bash
aws ecr create-repository \
  --repository-name final-python \
  --region ap-northeast-2
```

## 아키텍처 개요

```
Internet
  └─ ALB (포트 80) - 외부 노출
      └─ Backend Service (포트 8080)
          ├─ RDS Oracle
          └─ Python Internal ALB (포트 8000) - 내부 전용
              └─ Python Service (포트 8000)
```

- **Backend Service**: 외부 ALB를 통해 인터넷에 노출
- **Python Service**: 내부 ALB를 통해 Backend에서만 접근 가능
- **통신 흐름**:
  - Backend → Python: `http://python-internal-alb-dns:8000`
  - Python → Backend: `http://external-alb-dns`

