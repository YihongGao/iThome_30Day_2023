# Day-23-CI/CD pipeline 建置-配置 Manifest repo


# 前言
昨天建置 `Manifest Repo`，且將 GitLab agent 安裝於 EKS Cluster 中，並測試 GitLab runner 能透過 `kubectl` 操作 EKS Cluster了。

今天來配置 `Manifest Repo` 中的 Kubernetes yaml 並透過實作 `.gitlab-ci.yml`，來部署上 EKS cluster。

# 配置 `Manifest Repo`
依照 GitOps 原則，我們要部署上 Kubernetes 的 manifast(yaml)都要透過 Git 控管，並透過自動化程序，將 Git Repo 的內容來進修部署。
我們先來配置一些 manifast(yaml) 到 `Manifest Repo` 中

## 目錄結構
以下是我個人喜歡的目錄結構
```
- kubernetes_config
  └─ <cluster name> (k8s 叢集名稱)
      ├─ infra（基礎設施）
      │   ├─ configs
      │   │   └─ YAML 檔案(configMap/secret)
      │   └─ <k8s Object> 或 <service name>
      │       └─ YAML 檔案 
      │
      └─ apps（應用程序）
          └─ <namespace>
              ├─ configs
              │   └─ YAML 檔案(configMap/secret)
              └─ <service name>
                  └─ YAML 檔案 (deployment/service)
```
> 有些團隊會把 infra 跟 apps 拆開在不同 repo，獨立管理，為了 demo 方便，這裡選擇合併再一起

- `kubernetes_config`: 頂層目錄，用來明確標示目錄下的內容是什麼。
- `<cluster name>`: cluster 名稱，能同時管理多個 cluster，每個 cluster 目錄下又有兩個目錄
  - `infra`: 存放基礎建設相關的 yaml, 如 Namespace, Role, RBAC 等 k8s 基礎資源 或者像 prometheus 這樣的基礎設施。
  - `apps`: 存放與業務邏輯相關的應用程序的 YAML 文件

透過 `infra` 跟 `apps` 把基礎建設跟業務服務分開，因為通常是業務服務依賴底層的基礎建設(如 Redis 等中間件)，比較好控制部署順序。

### 初始化目錄結構
於 repo 根目錄，執行以下語法 或 直接 clone 此 [repo](https://gitlab.com/ithome2/cicd-playground)
```shell
mkdir -p kubernetes_config/ithome-demo/infra/namespace
mkdir -p kubernetes_config/ithome-demo/apps/ithome
mkdir kubernetes_config/ithome-demo/apps/ithome/configs
```

先我們來部署一個 nginx 服務在指定 namspace 中，用來測試 pipeline
- 建立一個名稱為 ithome 的 `namespace` 的 yaml，檔案位址為 `infra/namespace/ithome.yaml`
- 建立一個 nginx 的 `deployment` 與 `service` 的 yaml，檔案位址為 `apps/ithome/nginx.yaml`

執行以下語法建立 `infra/namespace/ithome.yaml`
```shell
mkdir kubernetes_config/ithome-demo/infra/namespace

echo "apiVersion: v1
kind: Namespace
metadata:
  creationTimestamp: null
  name: ithome
spec: {}
status: {}" > kubernetes_config/ithome-demo/infra/namespace/ithome.yaml
```

執行以下語法建立 `infra/namespace/ithome.yaml`
```shell
mkdir kubernetes_config/ithome-demo/apps/ithome/nginx

echo "apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: nginx
  name: nginx
  namespace: ithome
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nginx
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: nginx
    spec:
      containers:
      - image: nginx
        name: nginx
        ports:
        - containerPort: 80
        resources: {}
status: {}

---

apiVersion: v1
kind: Service
metadata:
  name: nginx
  namespace: ithome
spec:
  selector:
    app: nginx
  type: ClusterIP
  ports:
    - protocol: TCP
      port: 80
      targetPort: 80" > kubernetes_config/ithome-demo/apps/ithome/nginx/nginx.yaml
```

# .gitlab-ci.yml 基本介紹
.gitlab-ci.yml 是 GitLab CI/CD 系統中的配置文件，它用來定義項目的持續集成和持續部署（CI/CD）流程。

只要在 GitLab project 的 根目錄建置名為 `.gitlab-ci.yml` 的檔案，GitLab 就會依據檔案內容定義的觸發時機與腳本內容來執行。

先將以下內容複製到 `.gitlab-ci.yml` 中
```yml
stages:
  - deploy-infra
  - deploy-apps

deploy-infra:
  stage: deploy-infra
  image:
    name: bitnami/kubectl:latest
    entrypoint: ['']
  script:
    - kubectl config get-contexts
    - kubectl config use-context ithome2/cicd-playground:eks-ithome-demo
    - kubectl apply -R -f kubernetes_config/ithome-demo/infra

deploy-apps:
  stage: deploy-apps
  image:
    name: bitnami/kubectl:latest
    entrypoint: ['']
  script:
    - kubectl config get-contexts
    - kubectl config use-context ithome2/cicd-playground:eks-ithome-demo
    - kubectl apply -R -f kubernetes_config/ithome-demo/apps
  only:
    - main
```
我們用這個 yml 當例子來說明。

`.gitlab-ci.yml` 檔案內容主要由幾個元素組成
- `job`: GitLab CI 中的執行單位，每個 Job 都能自定義名稱，並能自定義你要執行的腳本內容，依此 yml 為例子，就是有兩個 job
  -  `deploy-infra`: 負責將 `infra` 目錄的 yaml，透過 `kubectl apply` 來部署
  -  `deploy-apps`: 負責將 `apps` 目錄的 yaml，透過 `kubectl apply` 來部署

- `stages`: 定義每個 Job 的執行順序，依此例會先執行 `deploy-infra` 之後才執行 `deploy-apps`

我們細看 job 中的屬性，以 `deploy-infra` 為例，包含以下屬性
- `stage`: 定義一個唯一的名稱，讓 `stages` 定義順序時使用
- `image`: 指定使用 `Docker image` 來運行此 Job
  - `name`: image 名稱
  - `entrypoint`: 定義容器進入點指令
- `script`: 執行的腳本語法
- `only`: 指定哪些 git 分支 commit 時，觸發此 pipeline

簡單來說，當 main 分支被 commit 時，會觸發此 pipeline，並依照 `stages` 定義的順序，先執行 `deploy-infra`，啟動一個 Container 並執行 `script` 中的語法，當 `deploy-infra` 完成後，會接著觸發 `deploy-apps`。

當將 `.gitlab-ci.yml` 的改動 commit 並 push 上 GitLab 後，能到該 Repo 左側選單/Build/Pipeline

![Build/Pipeline](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.18.58.2yztvrvx1ww0.webp)

能看到一個新的 pipeline 被建立，我們一樣點進 pipeline 中能看到兩個 job
![pipeline-list](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-24-上午1.34.04.5zduyop45t00.webp)

分別點進去能看到執行結果
- deploy-infra
    ![deploy-infra-log](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-24-上午1.34.26.4tyfbvbjp280.webp)
- deploy-apps
    ![deploy-apps-log](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-24-上午1.34.34.2rmnzsrcrtk0.webp)

能看到 pipeline 都執行成功了，再來透過 `kubectl` 檢查是否符合預期

```shell
$ kubectl get ns ithome

NAME     STATUS   AGE
ithome   Active   7m2s

$ kubectl get all -n ithome

NAME                        READY   STATUS    RESTARTS   AGE
pod/nginx-ff6774dc6-cc5rq   1/1     Running   0          81s

NAME            TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)   AGE
service/nginx   ClusterIP   10.100.175.49   <none>        80/TCP    81s

NAME                    READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/nginx   1/1     1            1           81s

NAME                              DESIRED   CURRENT   READY   AGE
replicaset.apps/nginx-ff6774dc6   1         1         1       81s
```

能看到也成功生成對應的 kubernetes resource 在 EKS cluster 中。

# 總結
今天配置了 `Manifest Repo` 中的目錄結構 與 一個 nginx 服務的 yaml，並透過了 GitLab CI 來進行部署。

明天我們會配置 `Application Repo` 與其 `gitlab-ci.yml` 來進行 compile、build image、push image。

# 延伸討論 - 優化 `gitlab-ci.yml`
你們肯定發現了 `.gitlab-ci.yml` 內容有很多重複的部分，身為 Engineer 一定會想降低重複的代碼，這時候能使用 job template 來重複利用相同的 script，能將 `.gitlab-ci.yml` 改成以下內下
```yml
stages:
  - deploy-infra
  - deploy-apps

variables:
  KUBECTL_IMAGE: bitnami/kubectl:latest
  KUBE_CONTEXT: ithome2/cicd-playground:eks-ithome-demo
  KUBE_CONFIG_DIR: kubernetes_config/ithome-demo

.deploy_template: &deploy  
  image:
    name: ${KUBECTL_IMAGE}
    entrypoint: ['']
  script:
    - kubectl config get-contexts
    - kubectl config use-context ${KUBE_CONTEXT}
    - kubectl apply -R -f ${KUBE_CONFIG_DIR}/$MODULE
  only:
    - main

deploy-infra:
  <<: *deploy
  stage: deploy-infra
  variables:
    MODULE: infra

deploy-apps:
  <<: *deploy
  stage: deploy-apps
  variables:
    MODULE: apps
```

建立一個 job template `.deploy_template`，內容為重複的 scripte，並標記一個錨點(anchor)為 `deploy`，讓其他 job 來引用。

在 `deploy-infra` 與 `deploy-apps` 透過 `<<: *deploy` 引用時，帶入 `variables` 變數，來部署不同的目錄。


