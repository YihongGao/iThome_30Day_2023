# Day-26-Observability 實現-logging

# 前言
前幾天我們實現了 CI/CD 讓服務能部署上 Kubernetes，但部署上去之後，還需要能知道服務的狀態是否正常或符合預期並能快速處理發生的異常問題，這時候依賴接下來要介紹的 Observability（可觀測性）。

# 什麼是 Observability（可觀測性）
[Observability] 是指能夠通過檢查系統或應用的**輸出**、**日誌**和**性能指標**來監控、測量和理解系統或應用的狀態的能力。   

簡單說就是讓開發者與維護者能**更了解系統的狀況**，而最終目的為能快速的排除問題甚至提前預防問題發生，這裡的問題類型包含了異常錯誤、效能問題、資源洩漏等狀況。

# Observability 三本柱
![Three-Pillars-of-Observability](https://www.skedler.com/blog/wp-content/uploads/2022/03/Three-Pillars-of-Observability.png)
圖檔來自-[Three Pillars of Observability](https://www.skedler.com/blog/defeat-downtime-with-observability/three-pillars-of-observability/)

而要怎麼實踐 Observability 呢？
我們能從 Logging、Metrics、Traces 三個面向開始進行，這三個面向通長被稱為 可觀測性的三本柱(Three Pillars of Observability)

`Logging`： Logging 是詳細記錄事件和資訊的紀錄。它們包含應用程式、系統或服務的操作、錯誤、警報和其他活動。Logging 對於故障排除、安全性分析和法規合規性至關重要，因為它們提供了有關事件發生的完整上下文。

`Metrics`： Metrics 提供了有關系統性能的數值數據。這些數據包括 CPU 使用率、記憶體消耗、請求速率等等。Metrics 數據可視化為圖表和儀表板，用於識別性能趨勢、瓶頸和容量規劃。

`Traces`： Traces 用於分佈式系統和微服務，它們允許跟蹤請求或交易在多個服務之間的流動。Traces 數據展示了請求的完整路徑和每個步驟的執行時間，有助於識別性能問題和分佈式系統中的故障。

今天先來介紹如何實踐 Loggin in kubernetes

# Kubernetes 如何處理 Log
Pod 中的 Container 運行時，產生的 Log 會透過 `stdout` 和 `stderr` 輸出到運行該 Node 的 host disk 上。

Node 上的 kubelet 會負責自動清理 Log 資料，釋放 Disk 空間
> Log 檔案大小超過設定 或 檔案數量超過一定數量 時自動清整該 Node 上的 Log 資料    

另外當 Pod 被終止或被驅逐時，該 Pod 的日誌也會被刪除。

![k8s-log](https://d33wubrfki0l68.cloudfront.net/59b1aae2adcfe4f06270b99a2789012ed64bec1f/4d0ad/images/docs/user-guide/logging/logging-node-level.png)

圖檔來自-[Kubernetes官方](https://kubernetes.io/docs/concepts/cluster-administration/logging/)

這些特性導致我們在查詢日誌時會遇到許多阻礙
- 難以使用：日誌散落在各個 Node，查詢起來非常困難
- 低可用性：Pod 被終止後，查不到歷史日誌，可能導致無法查到服務終止原因   
... etc

我們將透過其中一個主流解決方案 `ELK`，來實現 Loggin
# 透過 ELK 實踐 Loggin
> ELK 是一套集中式的日誌管理和分析解決方案，由 Elasticsearch 、Logstash、Kibana 三個字首組成的，這三個元件互相合作來幫助用戶於 Kubernetes 中進行收集、儲存、查詢、分析日誌內容。

- `Elasticsearch`（E）： Elasticsearch 是一個分散式的搜索和分析引擎，設計用於快速搜尋、分析和可視化大量結構化和非結構化數據。它廣泛用於構建搜索引擎、日誌和指標分析、業務智能等應用。

- `Logstash`（L）： Logstash 是一個數據收集和轉換工具，用於將不同來源的日誌和事件數據集成到統一的格式，然後將其傳輸到Elasticsearch或其他存儲和分析系統中。

- `Kibana`（K）： Kibana 是一個數據可視化和分析工具，用於創建豐富的圖形、圖表和儀表板，以實現對Elasticsearch中的數據進行探索、分析和監控。

我們會再搭配另一個組件 `Filebeat` 用來將 Kubernetes Node 上的 log 傳送給 `Logstash`。

## 架構圖
![Architectur](https://www.infobeans.com/wp-content/uploads/2020/04/Application-Log-Analysis-Architecture-1-1.png)

- `Filebeat`: 負責收集日誌，透過 DaemonSet 部署在每個 Worker Node 上，用來收集 Pod Logs，並將 Logs 傳遞給 `Logstash`。
- `Logstash`: 負責資料轉換，收到 Log 資料後轉換為適合的格式儲存進 `ElasticSearch`。
- `Elasticsearch`: 資料庫，用來儲存日誌資料。
- `Kibana`: 圖形化工具，提供開發/維護者查詢日誌。

## 安裝
透過 [elastic operatior] 來安裝 ECK
1. 安裝 CRD 與 operator
    ```
    kubectl create -f https://download.elastic.co/downloads/eck/2.9.0/crds.yaml

    kubectl apply -f https://download.elastic.co/downloads/eck/2.9.0/operator.yaml

    kubectl -n elastic-system logs -f statefulset.apps/elastic-operator
    ```
2. 安裝 `Filebeat`、`Logstash`、`Elasticsearch`、`Kibana`
    ```shell
    kubectl create ns logging

    kubectl apply -n logging -f https://raw.githubusercontent.com/YihongGao/iThome_30Day_2023/main/k8s-yaml/ELK/ELK.yaml
    ```
    > 此 yaml 會一次安裝上述四個組件，並配置 Filebeat 使用 kubernetes 的 auto-discover 來收集日誌
3. 檢查服務是否部署成功
    ```shell
    $ kubectl get pod -n logging 

    NAME                         READY   STATUS    RESTARTS   AGE
    elasticsearch-es-default-0   1/1     Running   0          45m
    kibana-kb-868f8c4778-5s562   1/1     Running   0          51m
    logstash-ls-0                1/1     Running   0          44m

    $ kubectl get daemonsets.apps -n logging

    NAME                     DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR   AGE
    filebeat-beat-filebeat   2         2         2       2            2           <none>          87m
    ```
    - logging namespace 中的 Pod 應該都要是 Running
    - logging namespace 中 daemonSet filebeat-beat-filebeat AVAILABLE 的數字要等同你的 worker node 數量   
  
    若以上都符合代表安裝完成

## 使用 Kibana 查詢日誌
1. 查詢登入密碼
    ```shell
    $ kubectl get secrets -n logging elasticsearch-es-elastic-user -o=jsonpath='{.data.elastic}' | base64 --decode; echo

    H5D2Vxi51h0ek0X10ykPR3cm
    ```
 返回值就是登入密碼

2. 將 kibana 服務轉發到本地
    ```
    kubectl port-forward services/kibana-kb-http 5601    
    ```

3. 開啟瀏覽器，連至 `https://localhost:5601`
    ![kibana-login-page](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-28-上午12.26.17.2kte4ww982w0.webp)
    - 預設帳號： elastic
    - 預設密碼： 使用第一步查到的帳密
  
1. 點選 login
    ![kibana-index-page](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-28-上午12.28.21.79ef2dvjhzo0.webp)

1. 開啟左側選單，選擇 Analytics/Discover   
    ![discover](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-28-上午12.30.48.tsvp412x328.webp)
1. 選擇 logs-* 的 data-view
    > 此 demo 配置，日誌預設會儲存在 `logs-generic-default` 的 data stream，所以 logs-* 能查到日誌
    ![select logs-* data-view](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-28-上午12.35.28.6mbrxwvyf4c0.webp)
1. 右側表單就會顯示 Kubernetes 上收集到的 Logs 

## 測試-產生新日誌
- 選項1 - 部署新服務觀察啟動日誌
    1. 部署 nginx pod
    ```
    kubectl run nginx --image nginx 
    ```

    2. 點選 `Refresh` 按鈕，查詢日誌
    ![nginx-start-log](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-28-上午1.03.34.2x27ryyresq0.webp)
- 選項2 - 使用 Day25 的服務，輸出自定義日誌
  1. 將 <pod-name> 改為你的 Pod 名稱，執行以下指令後，該服務
    ```
    kubectl exec -it -n ithome <pod-name>  -- curl -X POST -H "Content-Type: application/json" -v -d '{"message": "Hello from Kubectl"}' http://localhost:8080/log-message
    ```
    
    2. 點選 `Refresh` 按鈕，查詢日誌
    ![customize-log](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-28-上午1.06.42.1bki3g1ypb9c.webp)

# 總結
今天透過 `ELK` 與 `filebeat`，實現了集中式的日誌管理，讓維護者管理日誌更加容易，開發者想查詢日誌脈絡時，也能在 Kibana 查詢所有服務都日誌，能好理解日誌上下文，進而了解服務運作與排除問題。

明天我們會來介紹 Observability 的第二支柱 `Metrics`。

[Observability]: https://www.redhat.com/zh/topics/devops/what-is-observability

[elastic operatior]: https://www.elastic.co/guide/en/cloud-on-k8s/current/k8s-deploy-eck.html