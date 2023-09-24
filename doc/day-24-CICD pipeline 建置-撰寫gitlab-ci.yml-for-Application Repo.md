# Day-23-CI/CD pipeline 建置-撰寫gitlab-ci.yml-for-Application Repo

# 前言
昨天將 `Manifest Repo` 建置好了，並能將 `Manifest Repo` 中的 yaml 透過 `kubectl apply` 部署到 EKS cluster。

今天我們來配置 AWS ECR (Elastic Container Registry)、IAM Access token 與 配置 `Application Repo` 與 `gitlab-ci.yml`，讓程式碼改動時，能觸發 pipeline 進行編譯，並打包成 Container Image 上傳至 ECR，準備之後部署上 EKS Cluster 。
> "Application Repo 不是專有名詞，只是筆者的用語習慣，主要用來表示**任何存儲業務邏輯應用程序的 Git 存儲庫**。"

![CI-flow](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/application-repo-ci-flow.1gy6j16x5uw0.webp)

# AWS 配置
我們要在 AWS 上建立 ECR 用來存放 container image，與建立一個 IAM Access token 讓 GitLab runner 有權限 push image 到 ECR

## 建立 ECR
使用 `AWS CLI` 來建立 ECR
```shell
aws ecr create-repository --repository-name ithome-app-backend
```
> `--repository-name` 參數用來指定 ECR repository 名稱，能自行調整

執行結果
```
{
    "repository": {
        "repositoryArn": "arn:aws:ecr:ap-northeast-1:958069133531:repository/ithome-app-backend",
        "registryId": "958069133531",
        "repositoryName": "ithome-app-backend",
        "repositoryUri": "958069133531.dkr.ecr.ap-northeast-1.amazonaws.com/ithome-app-backend",
        "createdAt": "2023-09-25T01:44:18+08:00",
        "imageTagMutability": "MUTABLE",
        "imageScanningConfiguration": {
            "scanOnPush": false
        },
        "encryptionConfiguration": {
            "encryptionType": "AES256"
        }
    }
}
```
把 `repositoryUri` 欄位複製下來，稍後配置 `.gitlab-ci.yml` 會用到

## 建立 IAM Access key 給 gitlab runner
這裡依據最小權限原則，我們建立一個 policy 只有 push image 到 ECR 的權限。並建立一個 User 使用該 policy。
> 權限參考 [AWS 官方指南](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-push.html#image-push-iam)   
> 
會分成幾個階段
- 建立最小權限 policy
- 建立 User
- 建立 access key

### 建立最小權限 policy
建立 policy.json
```shell
echo '{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ecr:CompleteLayerUpload",
                "ecr:GetAuthorizationToken",
                "ecr:UploadLayerPart",
                "ecr:InitiateLayerUpload",
                "ecr:BatchCheckLayerAvailability",
                "ecr:PutImage"
            ],
            "Resource": "*"
        }
    ]
}' > ecr-policy.json
```
建立 AWS IAM policy
```shell
aws iam create-policy --policy-name ECRPolicy --policy-document file://ecr-policy.json
```
返回結果
```json
{
    "Policy": {
        "PolicyName": "ECRPolicy",
        "PolicyId": "ANPA56EK62DN7DJYUESSK",
        "Arn": "arn:aws:iam::958069133531:policy/ECRPolicy",
        "Path": "/",
        "DefaultVersionId": "v1",
        "AttachmentCount": 0,
        "PermissionsBoundaryUsageCount": 0,
        "IsAttachable": true,
        "CreateDate": "2023-09-24T17:53:20+00:00",
        "UpdateDate": "2023-09-24T17:53:20+00:00"
    }
}
```
將 `Arn` 的值複製起來，稍後會用到。

### 建立 User

```
IAM_USER_NAME=ithome-demo-gitlab-runner

aws iam create-user --user-name $IAM_USER_NAME
aws iam attach-user-policy --user-name $IAM_USER_NAME --policy-arn <policy Arn>
```

`<policy Arn>` 填寫上一步的 `Arn`
> 例如：arn:aws:iam::958069133531:policy/ECRPolicy

### 建立 access key
```
aws iam create-access-key --user-name $IAM_USER_NAME
```
返回結果
```
{
    "AccessKey": {
        "UserName": "ithome-demo-gitlab-runner",
        "AccessKeyId": "AKIA56EK62DN5W5ABYNS",
        "Status": "Active",
        "SecretAccessKey": "SBsIS4UigZU2vvxBDamMNMod2**************************",
        "CreateDate": "2023-09-24T18:04:02+00:00"
    }
}
```
複製出 `AccessKeyId` 與 `SecretAccessKey` 的值，稍後會用到。
> 任何人都能使用 `AccessKeyId` 與 `SecretAccessKey` 代表你的帳號操作 AWS 平台，請不要外洩此值。

經過上面的步驟，我們應該至少複製了三個資訊
- `repositoryUri`: 代表我們 Image 要 push 的 ECR 位址
- `AccessKeyId` 與 `SecretAccessKey`: 用來提供 gitlab runner 有權限 push image 到 ECR

到這邊 AWS 端的設定就完成了，接下來我們來建置 `Application Repo`。

# 配置 `Application Repo` 
因為 建議直接 fork 筆者的範例 [repo](https://gitlab.com/ithome2/app-backend) 到你的 Gitlab project，其中包含了 demo 程式碼、Dockerfile 與 .gitlab-ci.yml。

## fork project
直接 fork 此 [repo](https://gitlab.com/ithome2/app-backend) 到你個人的 Gitlab project。

## CI/CD 環境變數配置
1. 進入 fork 好的 Project，點選 `左側選單 / Settings / CI/CD`
2. 展開 `Variables` 區塊
3. 新增以下 `CI/CD Variables`
   
| Key       | Value            | Description                  |
| -------------- | --------------------- | ------------------------------------ |
| AWS_ACCESS_KEY_ID | ${AccessKeyId}            | 建立 access key 時，複製的 `AccessKeyId`                |
| AWS_SECRET_ACCESS_KEY | ${SecretAccessKey}      | 建立 access key 時，複製的 `SecretAccessKey`            |
| ECR_REGISTRTY      | ${repositoryUri}                    | 建立 ECR 時，複製的 `repositoryUri`                         |
| AWS_DEFAULT_REGION  |  ${regionCode}                |      你 AWS 選的 region code, ex: ap-northeast-1          |
> 這些變數用來授權 gitlab runner 透過 access key 操作 AWS，能參考[官方文檔](https://docs.gitlab.com/ee/ci/cloud_deployment/#authenticate-gitlab-with-aws)

## 檢視 `Dockerfile`
```docker
FROM eclipse-temurin:17.0.8.1_1-jre

WORKDIR /app

# 創建一個非 root 用戶
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 切換到用戶 appuser
USER appuser

COPY target/*.jar /app/application.jar

# 定義容器啟動命令，運行 JAR 文件
CMD ["java", "-jar", "application.jar"]

# 暴露 8080 端口
EXPOSE 8080
```
內容與 Day05 的 Dockerfile 雷同，都是將 jar 複製到容器中，並使用 non-root user 來運行 application。

## 檢視 `.gitlab-ci.yml`
我希望在改動程式碼時，能觸發 CI pipeline，並依序自動完成以下作業
1. 將原始碼編譯成可執行檔
2. 將可執行檔與執行環境打包成 container image
3. 將上傳至 AWS ECR 儲存
  
所以大致能將 gitlab-ci 分成三個 job 來運行，以下是我的 pipeline 流程
![pipeline-job-detail](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/application-repo-ci-pipeline.219yrpurmkkg.webp)
流程圖排版靈感來自 - [ 
uccuz / DevOps 好想學!新手也能打造雲端 Study Lab系列 第 19 篇](https://ithelp.ithome.com.tw/articles/10266998)


1. `compile`: 將 java 原始碼編譯並打包成 jar
2. `build`: 執行 docker build ，並將 build 好的 image 暫存至 gitlab container registry
3. `push-to-ecr`: 將 `build` 階段產生的 image 推送至 ECR

上述流程的 `.gitlab-ci.yml` 如下
```yml
stages:
  - compile
  - build
  - push-to-ecr

variables:
  IMAGE_NAME: ithome-app-backend
  CI_IMAGE: $CI_REGISTRY_IMAGE/$IMAGE_NAME:$CI_COMMIT_SHORT_SHA
  ECR_CI_IMAGE: $ECR_REGISTRTY:$CI_COMMIT_SHORT_SHA

compile:
  stage: compile
  image: maven:3.9.4-eclipse-temurin-17-alpine
  script:
    - ./mvnw clean package
  artifacts:
    paths:
      - target/*.jar

build:
  stage: build
  image: docker:24.0.6
  services:
    - docker:dind
  before_script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
  script:
    - ls -ltr target
    - docker build -t $CI_IMAGE .
    - docker push $CI_IMAGE
  dependencies:
    - compile


push-to-ecr:
  stage: push-to-ecr
  image: docker:24.0.6
  services:
    - docker:dind
  before_script:
    - apk add --no-cache python3 py3-pip
    - pip3 install --no-cache-dir awscli
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $ECR_REGISTRTY
  script:
    - docker pull $CI_IMAGE
    - docker image ls
    - docker tag $CI_IMAGE $ECR_CI_IMAGE
    - docker push $ECR_CI_IMAGE
  dependencies:
    - build
```

這個 `gitlab-ci.yml` 定義了以下內容
- `variables` 區塊: 用來定義變數
    - `IMAGE_NAME`: Image 名稱
    - `CI_IMAGE`: Gitlab container image registory 的存放位址
    - `ECR_CI_IMAGE`: ECR 的存放位址
- `stages`: 定義 job 執行順序  
> compile > build > push-to-ecr
  
我們分別說明每個 Job 的屬性用途
- `compile`: 
  - `stage`: 定義一個唯一的名稱，讓 `stages` 定義順序時使用
  - `image`: 因此作業依賴 jdk 與 maven，故使用 `maven:3.9.4-eclipse-temurin-17-alpine` 來運行
  - `script`: 執行自定義語法，這裡用來執行編譯指令
  - `artifacts.paths`: 將編譯出來的 jar 保存下來供其他 job 使用
  > `build` stage 需要將 jar 放入 image

- `build`
  - `stage`: 定義一個唯一的名稱，讓 `stages` 定義順序時使用
  - `image`: 因此作業依賴 docker cli，故使用 `docker:24.0.6` 來運行
  - `services`: 因為我們是透過 container 中的 docker engine 來建構 image，故需要添加此參數，細節能參考 [官方文檔]([engine](https://docs.gitlab.com/ee/ci/docker/using_docker_build.html#use-docker-in-docker))
    ```yaml
    services:
      - docker:dind
    ```
  - `before_script`: 在執行作業的 `script` 之前，會先執行此區塊指令，這裡用來登入 Gitlab container registry
    > 登入 Gitlab container registry 的授權方式，能參考 [官方文檔](https://docs.gitlab.com/ee/user/packages/container_registry/authenticate_with_container_registry.html#use-gitlab-cicd-to-authenticate)
  - `script`: 執行 `docker build` 並將 image push 到 GitLab container registry
  - `dependencies`: 定義此 job 依賴 `compile` stage

- `push-to-ecr`
  - `stage`: 定義一個唯一的名稱，讓 `stages` 定義順序時使用
  - `image`: 因此作業依賴 docker cli，故使用 `docker:24.0.6` 來運行
  - `services`: 因為我們是透過 container 中的 docker engine 來建構 image，故需要添加此參數，細節能參考 [GitLab 官方文檔]([engine](https://docs.gitlab.com/ee/ci/docker/using_docker_build.html#use-docker-in-docker))
    ```yaml
    services:
      - docker:dind
    ```
  - `before_script`: 在執行作業的 `script` 之前，會先執行此區塊指令，這裡執行幾個指令
    - 安裝 python3，用來安裝 AWS CLI
    - 安裝 AWS CLI，用來登入 ECR
    - 登入 Gitlab container registry
    - 登入 ECR
    > 登入 ECR 的方式能參考 [AWS 官方文檔](https://docs.aws.amazon.com/zh_tw/AmazonECR/latest/userguide/getting-started-cli.html)
  - `script`: 
    - 執行 `docker pull` 取的 Image
    - 執行 `docker tag` 將 Image 改為 ECR 的位址
    - 執行 `docker push` 將 Image 推上 ECR
  - `dependencies`: 定義此 job 依賴 `build` stage

# 測試
當有 commit 被 push 上 GitLab 後，能到該 Repo 左側選單/Build/Pipeline

![Build/Pipeline](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.18.58.2yztvrvx1ww0.webp)

能看到一個新的 pipeline 被建立，我們一樣點進 pipeline 中能看到兩個 job
![ci-pipeline](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-25-上午4.05.12.2criizhp51s0.webp)

點進去能看到執行結果，以 push-to-ecr 為例
![push-to-ecr-log](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-25-上午4.06.26.3hdialtx59o0.webp)

透過 AWS CLI 查詢是否有成功把 Image 推進 ECR
```
aws ecr describe-images --repository-name ithome-app-backend
```
> `--repository-name` 參數用來指定 ECR repository 名稱，能自行調整

返回結果
```
{
    "imageDetails": [
        {
            "registryId": "958069133531",
            "repositoryName": "ithome-app-backend",
            "imageDigest": "sha256:ac058e5fab5dc55b3ad67078384070370368bbfe05ea62a0545022d6cf5fa25d",
            "imageTags": [
                "2091cdd5"
            ],
            "imageSizeInBytes": 114506031,
            "imagePushedAt": "2023-09-25T04:04:08+08:00",
            "imageManifestMediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "artifactMediaType": "application/vnd.docker.container.image.v1+json"
        }
    ]
}
```
能看到有 imageTag 為 `imageTags`，能跟 `push-to-ecr` 的 Log 相符，代表有把 Image 推進 ECR 當中。

# 總結
今天學會了如何在 GitLab CI 中，透過 IAM Access token 授權將 Image 推送到 ECR，並使用 `artifacts` 與 GitLab container registry 將 jar 或 image 傳遞給不同的 job 執行。

明天我們再回頭透過 `Manifest Repo`，將這個 Image 部署上 EKS Cluster。

# 參考 
- [uccuz / DevOps 好想學!新手也能打造雲端 Study Lab系列 第 19 篇](https://ithelp.ithome.com.tw/articles/10266998)