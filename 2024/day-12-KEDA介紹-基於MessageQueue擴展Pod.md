
# Day-12 KEDA 介紹 - 基於 Message Queue 自動擴展 Pod

# 前言
昨天介紹了如何依據 Prometheus 中的 metrics 來自動擴展 Pod，今天藉由 GCP 的 Message Queue 服務: `Pub/Sub`，並且讓 Queue 沒有 message 要處理時，能把 Pod 降至 0 個節省運算資源。

# 環境準備
需要再 Kubernetes 中進行以下準備
- 配置 GCP Pub/Sub topic & subscription
- 配置 GCP Service Account 與 產生 gcp-service-account-credential.json
- 將 gcp-service-account-credential.json 透過 Kubernetes secrets 儲存到 Kubernetes cluster 內
- 部署 workload


## 配置 GCP Pub/Sub topic & subscription
透過 UI 或 `gcloud` command line tool 進行配置
```shell
# 需要先 login 與 指定 project
# gcloud auth login 
# gcloud config set project PROJECT_ID

# 建立 topic
TOPIC_NAME=ithome2024.day11.topic
gcloud pubsub topics create $TOPIC_NAME

SUBSCRIPTION=ithome2024.day11.subscription
# 建立 subscription
gcloud pubsub subscriptions create --topic $TOPIC_NAME $SUBSCRIPTION
```

![https://github.com/YihongGao/picx-images-hosting/raw/master/20240903/截圖-2024-09-03-下午11.47.24.26lf8djyue.webp](https://github.com/YihongGao/picx-images-hosting/raw/master/20240903/截圖-2024-09-03-下午11.47.24.26lf8djyue.webp)

## 配置 GCP Service Account 與 產生 gcp-service-account-credential.json
透過 UI 或 `gcloud` command line tool 進行配置
```shell
PROJECT_ID=${you GCP project_id}
SERVICE_ACCOUNT_NAME=ithome-demo

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/pubsub.subscriber"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/pubsub.viewer"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/monitoring.viewer"

# Download service account key
# ⚠️注意：這個 key 不能外流，否則有 GCP 被外人操控的風險
gcloud iam service-accounts keys create ./gcp-service-account-credential.json \                                                       
    --iam-account=$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com
```

![https://github.com/YihongGao/picx-images-hosting/raw/master/20240903/截圖-2024-09-03-下午11.46.56.1zi7cxx3jc.webp](https://github.com/YihongGao/picx-images-hosting/raw/master/20240903/截圖-2024-09-03-下午11.46.56.1zi7cxx3jc.webp)

## 將 gcp-service-account-credential.json 透過 Kubernetes secrets 儲存到 Kubernetes cluster 內
透過 kubectl 建立 secret
```shell
kubectl create secret generic pubsub-secret --from-file=gcp-service-account-credential.json=./gcp-service-account-credential.json
```
檢查是否成功建立
```shell
kubectl get secrets 

NAME            TYPE     DATA   AGE
pubsub-secret   Opaque   1      110m
```

## 部署 workload
我們部署一個 listen GCP Pub/Sub 的 spring boot Application
```yaml
# keda-pubsub-demo.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: keda-pubsub-demo
  name: keda-pubsub-demo
  namespace: ithome
spec:
  replicas: 1
  selector:
    matchLabels:
      app: keda-pubsub-demo
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: keda-pubsub-demo
        scrape: spring-boot-prometheus-exporter
    spec:
      volumes:
        - name: gcp-sa-credentials
          secret:
            secretName: pubsub-secret
            items:
            - key: gcp-service-account-credential.json
              path: gcp-service-account-credential.json
      containers:
      - image: luciferstut/spring-boot-application-for-ithome2024:day12
        imagePullPolicy: Always
        name: spring-boot-application-for-ithome2024
        env:
        - name: GOOGLE_CLOUD_PROJECT
          value: yihonggao-1548227860432
        - name: PUBSUB_ITHOME2024_DAY11_SUBSCRIPTION
          value: projects/yihonggao-1548227860432/subscriptions/ithome2024.day11.subscription
        - name: GOOGLE_APPLICATION_CREDENTIALS
          value: /etc/gcp/gcp-service-account-credential.json
        - name: GOOGLE_APPLICATION_CREDENTIALS_JSON
          valueFrom:
            secretKeyRef:
              name: pubsub-secret
              key: gcp-service-account-credential.json
        resources:
          requests:
            memory: "64Mi"
            cpu: "50m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        ports:
        - name: web
          containerPort: 8080
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          failureThreshold: 18
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          failureThreshold: 3
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          failureThreshold: 3
          periodSeconds: 10
        volumeMounts:
        - name: gcp-sa-credentials
          readOnly: true
          mountPath: "/etc/gcp/"
```

部署該 Deployment ，並嘗試 Push 一個 message 看是否能成功消化。
```shell
# 推送 message to Pub/Sub Topic
gcloud pubsub topics publish $TOPIC_NAME --message='Hello, Pub/Sub!'

# 檢視 Pod log
kubectl logs -f $YOU_POD_NAME
```

看到這一行時，代表 Spring boot Application 成功與 GCP Pub/Sub 連上並能處理 message 了。
```log
2024-09-03T16:12:26.052Z  INFO 1 --- [iThome-2024] [sub-subscriber2] c.e.iThome_2024.config.PubSubConfig      : Message arrived via an inbound channel adapter from sub-one! Payload: Hello, Pub/Sub!
```

接下來要來配置 KEDA 來自動擴展此 Deployment

# 配置 KEDA

## 配置 TriggerAuthentication
為了讓 KEDA 能順利與 GCP 溝通，需要配置 `TriggerAuthentication`，讓 KEDA 知道用什麼方式攜帶憑證給 GCP 進行認證與授權檢查。

```yaml
# keda-scaled-auth.yml
apiVersion: keda.sh/v1alpha1
kind: TriggerAuthentication
metadata:
  name: keda-trigger-auth-gcp-credentials
spec:
  secretTargetRef:
  - parameter: GoogleApplicationCredentials
    name: pubsub-secret
    key: gcp-service-account-credential.json
```
重點欄位如下
- `secretTargetRef`: 從 secrets 取得憑證
    - `parameter`: 憑證類型，`GoogleApplicationCredentials` 代表 GCP service account 的 json key.
    - `name`: 從哪個 secrets 找憑證
    - `key`: 憑證儲存於 secrets 中哪個欄位

稍後 `ScaledObject` 會使用到此物件

```shell
kubectl apply -f keda-scaled-auth.yml
```

## 配置 ScaledObject
```yaml
# keda-scaled-object.yml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: keda-pubsub-demo
  namespace: ithome
spec:
  fallback:
    failureThreshold: 3
    replicas: 1
  pollingInterval: 5
  cooldownPeriod: 60
  idleReplicaCount: 0
  minReplicaCount: 1
  maxReplicaCount: 3
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: keda-pubsub-demo
  triggers:
  - type: gcp-pubsub
    authenticationRef:
      name: keda-trigger-auth-gcp-credentials
    metadata:
      mode: "SubscriptionSize"
      value: "10"
      activationValue: "5"
      subscriptionNameFromEnv: PUBSUB_ITHOME2024_DAY11_SUBSCRIPTION
```
能發現添加了不少欄位，先從 `triggers` 開始說明
- `type`: 改為 gcp-pubsub，代表監聽的目標改為 GCP Pub/Sub 服務
    - `authenticationRef.name`: 使用上一步建立的 `TriggerAuthentication` 做為向 GCP 溝通的憑證
    - `mode`, `value`: 這兩個屬性一起看得意思就是，當 Pub/Sub subscription 累積超過 10 筆 message 待處理時，會 scale out Pod。
    - `idleReplicaCount`, `activationValue`: 這兩個屬性一起看得意思就是，當 subscription 中低於 5 筆 message 待處理時，會轉為 `Inactive` 狀態，將 Pod 數量轉為 **0**，反之會進入 `Active` 狀態，並由 HPA 接手保證 Pod 數量介於 `minReplicaCount` 與 `maxReplicaCount` 之間。
        > 關於 `Active` 與 `Inactive` 狀態，更多資訊，能參閱此[官方文件](https://keda.sh/docs/2.15/concepts/scaling-deployments/#activating-and-scaling-thresholds)
    - `cooldownPeriod`: subscription 中低於 5 筆 message 的狀況超國 60 秒才進入 `Inactive` 狀態

其他欄位上一篇介紹過就不重複說明，更詳細的介紹能參閱[官方文件](https://keda.sh/docs/2.15/reference/scaledobject-spec/) 

簡單來說，這個 ScaledObject 使用 GCP monitoring 的 metrics，來檢查 Pub/Sub subscription queue 長度作為擴縮容依據，當 queue 長度低於 5 時，持續超過 60s 時，將 Pod 歸 0 節省資源，當 queue 長度超過 5 時，啟動 Pod 來消化 message，若 queue 長度超過則擴展更多 Pod 協同處理 message。

配置 ScaledObject
```shell
kubectl apply -f keda-scaled-object.yml
```
過段時間應該能看到，以下資訊
```shell
kubectl get scaledobjects.keda.sh 

NAME               SCALETARGETKIND      SCALETARGETNAME    MIN   MAX   TRIGGERS     AUTHENTICATION                      READY   ACTIVE   FALLBACK   PAUSED    AGE
keda-pubsub-demo   apps/v1.Deployment   keda-pubsub-demo   1     3     gcp-pubsub   keda-trigger-auth-gcp-credentials   True    False    False      Unknown   9m50s
```

此 demo 中
- `READY` 欄位: 代表是否成功連上 GCP
- `ACTIVE` 欄位: 代表 queue 長度是否超過 `activationValue` 配置

能看到 `ACTIVE` 為 False 時，Deployment 的 Pod 會歸 0
```shell
kubectl get deployments.apps 

NAME               READY   UP-TO-DATE   AVAILABLE   AGE
keda-pubsub-demo   0/0     0            0           162m

kubectl get pod
No resources found in ithome namespace.
```

讓我們打些 message 到 GCP Pub/Sub 試試看
```shell
for i in {1..40}; do gcloud pubsub topics publish $TOPIC_NAME --message='Hello, Pub/Sub'; done
```
> ⚠️ 因為 GCP monitoring 採樣數據頻率較慢，需要稍等一下

能看到 `ScaledObject` ACTIVE 改為 `True` 並啟動 Pod 了
```shell
kubectl get scaledobjects.keda.sh
NAME               SCALETARGETKIND      SCALETARGETNAME    MIN   MAX   TRIGGERS     AUTHENTICATION                      READY   ACTIVE   FALLBACK   PAUSED    AGE
keda-pubsub-demo   apps/v1.Deployment   keda-pubsub-demo   1     3     gcp-pubsub   keda-trigger-auth-gcp-credentials   True    True     False      Unknown   19m

kubectl get pod
NAME                                READY   STATUS    RESTARTS   AGE
keda-pubsub-demo-7f8944cf7f-9qfxb   1/1     Running   0          46s
keda-pubsub-demo-7f8944cf7f-h6br5   0/1     Running   0          31s
keda-pubsub-demo-7f8944cf7f-rphh5   0/1     Running   0          31s
```

# 環境清理
因 `ScaledObject` 監聽 `Pub/Sub` 的方式是透過 [GCP Monitoring](https://cloud.google.com/monitoring) 來感知 Queue 長度，所以驗證完成後，記得把 `ScaledObject` 移除，避免產生 GCP 費用。
```shell
kubectl delete scaledobjects.keda.sh keda-pubsub-demo
```

# 小結
今天我們使用了 KEDA 依據 Message Queue 的長度來作為 autoscaling 的策略依據，在 message consumer 特別消耗資源 且 message 頻率很不固定的案例中，能使用此方式避免大量資源被長時間佔用與浪費。

明天會介紹本系列最後一個 KEDA 使用案例，若對更多使用案例有興趣也能參閱 [官方文件/scalers](https://keda.sh/docs/2.15/scalers/)

# Refernce
- [KEDA]

[KEDA]: https://keda.sh/
[HPA]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/
[HPA (Horizontal Pod Autoscaling)]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/

[2023/day-29-Kubernetes 介紹-Pod 水平自動伸縮 (Horizontal Pod Autoscaler)]: https://ithelp.ithome.com.tw/articles/10336846

[kind]: https://kind.sigs.k8s.io/

[helm]: https://helm.sh/

[Kube-Prometheus]: https://prometheus-operator.dev/docs/getting-started/installation/