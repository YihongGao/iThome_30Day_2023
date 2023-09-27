# Day-25-CI/CD pipeline 建置-小節-完整的 CI/CD 部署流程

# 前言
經過前三天的介紹，我們完成了
- 安裝 GitLab agent 到 EKS cluster，讓 GitLab Runner 能部署/操作 Kubernetes
- 配置 Manifest repo，透過 Manifest 管理 Kubernetes yaml，透過此 repo 發動 CD pipeline 來部署服務上 EKS cluster，實現 push 模式的 [GitOps]。
- 配置 Applcation repo，是個存放業務邏輯的應用程序，當原始碼改動時並 git push 到 main/develop 分支時，自動編譯、打包、推送至 Container Image registry。

今天我們走一次完整的 CI/CD 部署流程流程，從服務開發好到部署上 EKS Cluster。

# 部署範圍
假設我想部署一套服務，名稱為 app-backend，使用的 cotainer image 為 Day24 上傳至 ECR 的 Image，並使用 ingress 與 service 使其對外服務。

我們至少要配置以下 kubernetes 資源
- development
- service
- ingress

另外我們也一併部署 configMap 與 secret(sealed-secret) 讓 demo 在完整一點
- configMap
- secret
  
# 前置作業

## 安裝 `nginx-ingress-controller`
為了縮短篇幅與方便讀者實作，直接透過 helm 安裝 `nginx-ingress-controller`

```shell
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx

helm install nginx-ingress-controller ingress-nginx/ingress-nginx \
-n nginx-ingress --create-namespace \
--set controller.metrics.enabled=true \
--set-string controller.metrics.service.annotations."prometheus\.io/port"="10254" \
--set-string controller.metrics.service.annotations."prometheus\.io/scrape"="true" --dry-run
```

## 安裝 sealed-secrets
為了縮短篇幅與方便讀者實作，直接透過 `kubectl apply` 安裝
```shell
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml
```

## 準備 Application container image
依照 Day24 的流程，對 application repo 進行 commit 或 MR 合併至 main 分支，透過 pipeline 進行 編譯/打包 Image/ 推送 Image。

通常會依循以下流程
1. 開 feature 分支，進行開發
2. commit 並 push feature 分支，提出 MR 合併回 develop or main
3. code reviewer 對 MR 進行審查，通過後合併
4. 合併後自動觸發 pipeline
   1. 編譯
   2. build image
   3. push image 到 container image registry

因為昨天實作時，已經執行準備好 Container Image 了，我們直接使用 Day24 產生的 Image tag 來部署
```shell
# 找 repositoryUri
$ aws ecr describe-repositories

{
    "repositories": [
        {
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
    ]
}


# 找 imageTag
$ aws ecr describe-images --repository-name ithome-app-backend

{
    "imageDetails": [
        {
            "registryId": "958069133531",
            "repositoryName": "ithome-app-backend",
            "imageDigest": "sha256:93ba576c881fa6a8fa571731a18588b8c0a30d0977902139d8cdde6a6e678ade",
            "imageTags": [
                "6a7be12a"
            ],
            "imageSizeInBytes": 114506053,
            "imagePushedAt": "2023-09-27T00:04:50+08:00",
            "imageManifestMediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "artifactMediaType": "application/vnd.docker.container.image.v1+json",
            "lastRecordedPullTime": "2023-09-27T00:06:14.575000+08:00"
        }
    ]
}
```
拿到 `repositoryUri` 與 `imageTag`，用來配置到 deployment，以上述例子
- `repositoryUri`: 958069133531.dkr.ecr.ap-northeast-1.amazonaws.com/ithome-app-backend
- `imageTag`: 6a7be12a
部署時使用的 image 為: `958069133531.dkr.ecr.ap-northeast-1.amazonaws.com/ithome-app-backend:6a7be12a`

## 配置 app-backend 用的 manifest 
1. 開啟 Day23 建置的 Manifest repo
2. 開啟新分支，例如：release/app-backend (可選)
> 團隊協作時，建議開新分支來改 yaml，後續用 MR 進行 code review
3. 配置 deployment 與 service
    1. 這裡直接下載預先寫好的 yaml
    ```shell
    mkdir kubernetes_config/ithome-demo/apps/ithome/app-backend

    curl -o kubernetes_config/ithome-demo/apps/ithome/app-backend/app-backend.yaml "https://gitlab.com/ithome2/cicd-playground/-/raw/main/kubernetes_config/ithome-demo/apps/ithome/app-backend/app-backend.yaml?ref_type=heads"
    ```
   1. 調整 `app-backend.yaml` 中 deployment 用的 image name 為你的 container image。
4. 配置 configMap 與 secret
   1. 建立 secret   
    ```shell
    # 建立暫存的 secret file 供 kubeseal 加密
    echo "
    apiVersion: v1
    data:
      APP.TOKEN: ZHVtbXktdG9rZW4=
    kind: Secret
    metadata:
      creationTimestamp: null
      name: demo-secret
      namespace: ithome
      labels:
        deploy-priority: '1'
    " > tmp-secret.yaml

    # 加密為 SealedSecret
    kubeseal -o yaml < tmp-secret.yaml > kubernetes_config/ithome-demo/apps/ithome/configs/demo-secret.yaml
    
    # 移除 暫存的 secret file
    rm tmp-secret.yaml
    ```
    > 注意！若 Secret 儲存的是真實的機敏資訊，務必將 Secret 加密為 SealedSecret 之後，再上傳至 git，避免機敏資訊外洩，使用方式能參考 Day 14

    2. 建立 configMap
    ```shell
    echo "
    apiVersion: v1
    data:
      APP.ENV: kind
      APP.WELCOME.MESSAGE: Hello, ConfigMap
    kind: ConfigMap
    metadata:
      creationTimestamp: null
      name: demo-config
      namespace: ithome
      labels:
        deploy-priority: '1'
    " > kubernetes_config/ithome-demo/apps/ithome/configs/demo-config.yaml
    ```
1. commit 並 push 上 GitLab
    ```shell
    git add kubernetes_config/ithome-demo/apps/ithome
    git commit -m 'added app-backend
    git push
    ```
2. 提出 MR (merge requests) 合併到 main 分支 (可選)
    > 能在此關卡透過 code review 檢查更改的範圍是否符合部署計畫。
1. (自動觸發) 執行 CD pipeline 
4. 檢查是否部署成功
    ```shell
    $ kubectl get deployments.apps,pod,svc,cm,secrets 

    NAME                          READY   UP-TO-DATE   AVAILABLE   AGE
    deployment.apps/app-backend   1/1     1            1           10h
    deployment.apps/nginx         1/1     1            1           10h

    NAME                               READY   STATUS    RESTARTS   AGE
    pod/app-backend-6646955fcb-9p4d7   1/1     Running   0          10h
    pod/nginx-ff6774dc6-96k24          1/1     Running   0          10h

    NAME                  TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)   AGE
    service/app-backend   ClusterIP   10.100.248.199   <none>        80/TCP    10h
    service/nginx         ClusterIP   10.100.82.219    <none>        80/TCP    10h

    NAME                         DATA   AGE
    configmap/demo-config        2      10h
    configmap/kube-root-ca.crt   1      10h

    NAME                 TYPE     DATA   AGE
    secret/demo-secret   Opaque   1      10h
    ```
    能看到服務已經部署上 EKS cluster，Pod 的 `STATUS` 與 `READY` 也正確。

## 配置 ingress
配置 ingress 將服務曝露到外部

1. 開啟 Day23 建置的 Manifest repo
2. 開啟新分支，例如：release/app-backend (可選)
3. 新增 ingress yaml
    ```shell
    mkdir kubernetes_config/ithome-demo/infra/ingress

    echo "
    apiVersion: networking.k8s.io/v1
    kind: Ingress
    metadata:
      name: demo-ingress
      namespace: ithome
      annotations:
        kubernetes.io/ingress.class: nginx
    spec:
      rules:
      - host: ithome-demo.ingress.com
        http:
          paths:
          - pathType: Prefix
            path: /        
            backend:
              service:
                name: app-backend
                port:
                  number: 80
    " > kubernetes_config/ithome-demo/infra/ingress/demo-ingress.yaml
    ```
2. 提出 MR (merge requests) 合併到 main 分支 (可選)
    > 能在此關卡透過 code review 檢查更改的範圍是否符合部署計畫。
1. (自動觸發) 執行 CD pipeline 
2. 檢查是否部署成功
    ```shell
    $ kubectl get ingress

    NAME                                     CLASS    HOSTS                     ADDRESS   PORTS   AGE
    ingress.networking.k8s.io/demo-ingress   <none>   ithome-demo.ingress.com             80      6m11s
    ```
    能看到也部署成功了。

## 測試-訪問該服務
1. 先找到 nginx-ingress-controller 的外部存取位址
    ```shell
    $ kubectl get svc -n nginx-ingress nginx-ingress-controller-ingress-nginx-controller

    NAME                                                          TYPE           CLUSTER-IP       EXTERNAL-IP                                                                   PORT(S)                      AGE
    nginx-ingress-controller-ingress-nginx-controller             LoadBalancer   10.100.104.184   abbbd283855x84e3da9d4ba0f711fc0d-552512304.ap-northeast-1.elb.amazonaws.com   80:32234/TCP,443:32557/TCP   14h
    ```
    將 `EXTERNAL-IP` 的值複製出來，例如上例: `abbbd283855x84e3da9d4ba0f711fc0d-552512304.ap-northeast-1.elb.amazonaws.com` 
2. 使用 curl 訪問服務
    因為 ingress 基於 Host 來分配流量，所以需要添加 http header `"Host: ithome-demo.ingress.com"`
    > 生產環境建議使用 DNS 服務，如 Amazon Route 53

   1. 訪問 root path
        ```shell
        curl -i -H "Host: ithome-demo.ingress.com" http://abbbd283855x84e3da9d4ba0f711fc0d-552512304.ap-northeast-1.elb.amazonaws.com

        HTTP/1.1 200 
        Date: Wed, 27 Sep 2023 05:58:04 GMT
        Content-Type: text/plain;charset=UTF-8
        Content-Length: 16
        Connection: keep-alive

        Hello, ConfigMap
        ```
        能看到正常返回了 Http 200，Http body 也返回 demo-configMap 中配置的 message，而不是原始碼中的預設訊息，代表 Pod 有正常注入 configMap 到環境變數
    2. 訪問 token path
        ```shell
        $ curl -i -H "Host: ithome-demo.ingress.com" http://abbbd283855x84e3da9d4ba0f711fc0d-552512304.ap-northeast-1.elb.amazonaws.com/token

        HTTP/1.1 200 
        Date: Wed, 27 Sep 2023 06:09:41 GMT
        Content-Type: text/plain;charset=UTF-8
        Content-Length: 11
        Connection: keep-alive

        dummy-token
        ```
        一樣返回了 Http 200，Http body 也返回 demo-secret 中配置的 token，而不是原始碼中的預設 token，代表 Pod 有正常注入 secret 到環境變數

# 總結
今天我們已經完整的執行了一次服務發布的流程，從原始碼發動 CI pipeline(編譯/打包/推送 Image) 到 配置 Manifest repo 觸發 CD pipeline(deploy to EKS)，流程中都能透過 MR(merge request) 的關卡來進行 review，保持產品穩定。

# 清理環境
因為 nginx ingress controller 會建立 AWS Load balancers，會產生 AWS 費用，當實作完成後，能執行以下指令將 nginx ingress controller uninstall。

刪除 nginx namespace，使其自動刪除
```shell
kubectl delete namespace nginx-ingress
```

檢查 ELB 是否已刪除
```shell
aws elbv2 describe-load-balancers
```
[GitOps]: https://about.gitlab.com/topics/gitops/
