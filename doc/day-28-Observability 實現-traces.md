# Day-28-Observability 實現-trace

# 前言
昨天透過 Prometheus 與 Grafana 實現的 Metrics，今天要來介紹可觀測性的第二支柱 Trace

# 什麼是 Traces
Traces 是一種用於分析和監控分佈式應用程式性能的關鍵元素。Traces通常用於分析應用程式的運行情況，特別是當應用程式由多個服務、組件或微服務組成時，特別適合。

![micro-service-archtiecture](https://aionys.com/wp-content/uploads/2020/02/Microservice-Architecture-Of-UBER-Microservice-Architecture-Edureka-1-1024x1016.png)    
圖檔來自-[How to Benefit from Microservices Architecture Implementation](https://aionys.com/how-to-benefit-from-microservices-architecture-implementation/)

在微服務架構下，當一個 API 被調用時，背後可能依賴了許多個應用程序，當我們想知道這個請求經過了哪些應用程序，甚至每個應用程序花費多久時間處理時，若只透過 Logging 或 Metrics，我們不只需要事先埋好 Log，檢視時也是件費時費力的事情。

透過 Traces 我們能輕鬆的檢視每個請求經過了哪些應用程序、使用的資料庫語具，使用哪些中間件(Redis、MQ)，甚至每個操作花費的處理時間，能透過 jaeger、skywalking 這類工具收集 Traces 並圖形化。

![trace](https://www.jaegertracing.io/img/trace-detail-ss.png)
圖檔來自 - [jaeger 官方](https://www.jaegertracing.io/docs/1.49/)

## Opentelemetry
![](https://hackage.haskell.org/package/hs-opentelemetry-sdk-0.0.3.6/docs/docs/img/traces_spans.png)
圖檔來自 [hackage.haskell.org/OpenTelemetry.Trace](https://hackage.haskell.org/package/hs-opentelemetry-sdk-0.0.3.6/docs/OpenTelemetry-Trace.html)

以主流的 Trace 規範: [Opentelemetry] 為例，[Opentelemetry] 定義了 Trace 由幾個核心概念組成，用來實現分散式追蹤。

- Trace： Trace 代表單一請求的整個生命週期，詳細描述了在整個系統中的執行過程，每個 Trace 有唯一的 trace Id，包含一個或多個 Span，表示不同階段或事件。

- Span： Span 是 Trace 的基本組成部分，代表操作的特定部分或事件，具有 Id，包括開始時間、結束時間、持續時間等信息。
  
- Context Propagation：是實現分散式追蹤的核心概念，透過 Context Propagation，Span 可以相互關聯並組裝成 Trace，例如透過 Parent Span Id 將 Span 關聯在一起形成 Trace。

> 更完整詳細的核心概念能參考 [Opentelemetry 官方文檔](https://opentelemetry.io/docs/concepts/signals/traces/)

# Jaeger  
[Jaeger] 是一個開源的分佈式追蹤系統，用於監控和分析分佈式應用程序的性能和行為，並支援 Opentelemetry 標準，並包含 Web UI 能將收集到的 Trace 進行圖形化。

![](https://miro.medium.com/v2/resize:fit:2000/format:webp/1*SHV-GY18huaiBn9sPzQLeQ.jpeg)
圖檔來自-[Distributed Tracing with OpenTelemetry and Jaeger](https://iqfarhad.medium.com/distributed-tracing-with-opentelemetry-and-jaeger-e21e53b5c24e)

上圖是與 Opentelemetry 一起使用時的架構圖，大致分為幾個關鍵組件
- agent: 負責接收來自應用程序的 Trace 數據，然後將數據發送到收集器。
- collecotor: 負責接收、存儲和索引來自 Agent 的跟蹤數據。
- database: 持久化 Trace 數據
- jaeger Query: 提供 API 來查詢數據
- jaeger UI: 提供查詢 Trace 等數據的 Web UI
 
## 安裝 Jaeger
接來下透過 jaeger Operator 安裝 jaeger

1. 先安裝 cert-manager，因為 jaeger Operator 依賴此組件
  ```shell
  helm repo add jetstack https://charts.jetstack.io
  helm repo update

  helm install \
    cert-manager jetstack/cert-manager \
    --namespace cert-manager \
    --create-namespace \
    --version v1.13.1 \
    --set installCRDs=true
  ```
2. 安裝 jaeger Operator
  ```shell
  kubectl create namespace observability

  kubectl create -f https://github.com/jaegertracing/jaeger-operator/releases/download/v1.49.0/jaeger-operator.yaml -n observability
  ```

3. 透過 jaeger CRD 建立 jaeger instance
  ```shell
  cat <<EOF | kubectl apply -f -
  apiVersion: jaegertracing.io/v1
  kind: Jaeger
  metadata:
    name: jaeger
  EOF
  ```
4. 檢查是否安裝成功
  ```shell
  $ kubectl get pod -n observability 

  NAME                               READY   STATUS    RESTARTS   AGE
  jaeger-5c87cf7b88-5w2cr            1/1     Running   0          4h57m
  jaeger-operator-77b9f69c99-99mbk   2/2     Running   0          4h58m
  ```
5. 將 jaeger Web UI 轉發至本地
  ```
  kubectl port-forward svc/jaeger-query 16686:16686 -n observability
  ```
6. 開啟瀏覽器連至 `http://localhost:16686/`
  ![jaeger-ui](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231001/截圖-2023-10-01-下午6.13.40.7dwak5rfnkg0.png)

  能順利連到 jaeger Web UI 代表安裝基本上成功了。

## 配置 Opentelemetry agent 到 application
1. 添加 Opentelemetry agent 的依賴，新增以下內容到 pom.xml
    ```xml
    <build>
      <plugins>
        <plugin>
          <artifactId>maven-dependency-plugin</artifactId>
          <version>3.1.1</version>
          <executions>
            <execution>
              <phase>prepare-package</phase>
              <goals>
                <goal>copy</goal>
              </goals>
              <configuration>
                <artifactItems>
                  <artifactItem>
                    <groupId>io.opentelemetry.javaagent</groupId>
                    <artifactId>opentelemetry-javaagent</artifactId>
                    <version>1.30.0</version>
                  </artifactItem>
                </artifactItems>
                <stripVersion>true</stripVersion>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
    ```
2. 調整 Dockerfile
   1. 新增 `ADD target/dependency/opentelemetry-javaagent.jar /app/`，將 agent 的 jar 包入 container

   2. 調整啟動指令，使用 agent
   ```docker
   CMD ["java", "-jar", "-javaagent:opentelemetry-javaagent.jar", "application.jar"]
   ```

    完整 dockerfile 如下
    ```docker
    FROM eclipse-temurin:17.0.8.1_1-jre

    WORKDIR /app

    # 創建一個非 root 用戶
    RUN groupadd -r appgroup && useradd -r -g appgroup appuser

    # 切換到用戶 appuser
    USER appuser
    
    ADD target/dependency/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
    COPY target/*.jar /app/application.jar

    # 定義容器啟動命令，運行 JAR 文件
    CMD ["java", "-jar", "-javaagent:opentelemetry-javaagent.jar", "application.jar"]

    # 暴露 8080 端口
    EXPOSE 8080
    ```
    3. 透過 Gitlab CI 或本地進行 docker build

3. 新增個 ConfigMap 定義 agent 要用的參數
    ```yaml
    apiVersion: v1
    data:
      OTEL_TRACES_EXPORTER: jaeger
      OTEL_EXPORTER_JAEGER_ENDPOINT: http://jaeger-collector.observability.svc.cluster.local:14250 
      OTEL_METRICS_EXPORTER: none
    kind: ConfigMap
    metadata:
      creationTimestamp: null
      name: otel-agent-config
      namespace: ithome
      labels:
        deploy-priority: '1'
    ```
  - `OTEL_TRACES_EXPORTER`: 值為 jaeger，代表使用 jaeger exporter 
  - `OTEL_EXPORTER_JAEGER_ENDPOINT`: 值為 http://jaeger-collector.observability.svc.cluster.local:14250，是先前安裝 jaeger 實例中的 collecotor 連線位址
    > 能透過 `kubectl get svc -n observability` 查看
  - `OTEL_METRICS_EXPORTER`: 值為 none，讓 agent 不需導出 metrics

4. 更新 app-backend yaml
    1. 將 `image` 改為最新的 image tag
    2. 將 `otel-agent-config` 注入到環境變數
    ```yaml
    envFrom: 
      - configMapRef:
          name: otel-agent-config      
    ```
    3. 配置 `OTEL_SERVICE_NAME` 環境變數，值等同 Pod Name
    ```
    env:
      - name: OTEL_SERVICE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
    ```
    > OTEL_SERVICE_NAME 用來識別 Trace 數據來自什麼服務
5. 將 app-backend 部署至 EKS

到目前為止，app-backend 的 java 應用程序就會搭配 OpenTelemetry agent 一起運行了。
   

# 測試
完整的原始碼
- [Java appliction](https://gitlab.com/ithome2/app-backend)
- [Kubernetes yaml](https://gitlab.com/ithome2/cicd-playground/-/raw/main/kubernetes_config/ithome-demo/apps/ithome/app-backend/app-backend.yaml?ref_type=heads)

使用兩個服務來測試 Trace 
- app-backend: 提供 `GET /product/{id}` API，並依賴 `product-backend` 服務的 API。
- product-backend: 提供 `GET /productInfo/{id}`，依 id 回傳資料

![test](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231001/截圖-2023-10-01-下午6.33.34.4q74wmrkgho0.webp)
1. 部署 app-backend，參考 [app-backend.yaml](https://gitlab.com/ithome2/cicd-playground/-/raw/main/kubernetes_config/ithome-demo/apps/ithome/app-backend/app-backend.yaml?ref_type=heads)
2. 部署 product-backend，參考 [product-backend.yaml](https://gitlab.com/ithome2/cicd-playground/-/raw/main/kubernetes_config/ithome-demo/apps/ithome/product-backend/product-backend.yaml?ref_type=heads)

3. 呼叫 app-backend `GET /product/{id}`
  ```shell 
  $ kubectl exec -it app-backend-75fdf9895-p5s5n -- curl localhost:8080/product/1
  {"name":"【YONEX】NF1000Z","price":5000}

  $ kubectl exec -it app-backend-75fdf9895-p5s5n -- curl localhost:8080/product/2
  {"name":"【YONEX】ARC 11 PRO","price":4000}            
  ```
  調用成功，會依據 id 返回 product 資料
4. 檢視 jaeger UI
  1. 左側 `Service` 欄位選擇 Pod name，`Operation` 選 GET /product/{id}，並點選 `Find Traces`
  ![jaeger query](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231001/截圖-2023-10-01-下午7.29.26.7i53xvc3uxk0.webp)
  2. 右側就會展示 Traces 的數據，點選任一個能看到詳細的關聯與耗時
  ![jaeger-query-detail](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231001/截圖-2023-10-01-下午7.32.38-1.6o9hlc4x6600.webp)
  能看到此請求在 app-backend 與 product-backend 的**調用順序**、**操作方式**、**操作位址**、**花費時間**，這些能幫助我們了解該請求的調用關係與性能調適。
  

# 總結
今天使用了 jaeger 與 Opentelemetry agent 來收集並圖形化 Trace 資訊，能更容易理解請求的流向跟過程中的操作與花費時間，幫助我們解決微服務架構上，追蹤調用鏈不易的問題，此外進行效能調校時，也能一目瞭然的找到瓶頸點，能更精準的進行優化。

# 參考
- [Jaeger / Operator for Kubernetes](https://www.jaegertracing.io/docs/1.29/operator/)


[Opentelemetry]: https://opentelemetry.io/

[Jaeger]: https://www.jaegertracing.io/