# Day-22-CI/CD pipeline 建置-GitLab agent

# 前言
我們昨天透過 AWS/EKS 建置了雲端的 Kubernetes 環境，接下來幾天會來介紹如何使用 GitLab CI 來建置 CI/CD pipeline，會用最簡單的 push 模式來實現 [GitOps]。
> push 優點就是容易實現，但同步性較低，故 GitOps 主流的實踐方式仍是 pull 模式，會需要介紹額外組件，如 Argo CD, Flux CD，後面有篇幅在介紹。

# 架構
![architecture](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230913/架構圖.5holmyq61hg0.webp)

途中有兩個主要 GitLab Repo 分別負責
- `Application Repo`：儲存應用程序原始碼，當原始碼調整要發布時，需透過此 Repo 觸發 CI pipeline，進行編譯跟 build image。

- `Manifest Repo`：儲存所有 Kubernetes 組件的 manifest file(yaml)。要異動 Kubernetes 時，需調整此 Repo 中的檔案後，觸發該 CD pipeline 透過 `kubectl` deploy 至 Kubernetes。

> "Application Repo 與 Manifest Repo 並不是專有名詞，只是筆者習慣的稱呼"

# 實作大綱
接下來會分為幾個階段來完成這套流程
- 安裝 GitLab agent
- 配置 `Manifest Repo` 與 `gitlab-ci.yml`
- 配置 `Application Repo` 與 `gitlab-ci.yml`

# 安裝 GitLab agent

## 什麼是 GitLab agent?
[GitLab agent] 是運行在 Kubernetes cluster 中的服務，他可以用來執行 CI/CD 等任務自動化任務。   
使用 GitLab agent 的好處是不需要將 Kubernetes 的連線資訊與憑證(如: kubeconfig)儲存在外部系統，就能對 Kubernetes 進行部署或操作，比一般的 [GitLab runner] 更安全，降低 kubeconfig 洩漏的風險。
> kubeconfig 洩漏可能導致攻擊者能直接操作你的 Kubernetes cluster

## 前置作業
1. 安裝 [helm]：是一個用於管理 Kubernetes 應用程序的開源工具。稍後會使用它來安裝 GitLab agent 到 EKS Cluster 中。
2. 建立個 GitLab Project 或 clone/fork 我的 [repo](https://gitlab.com/ithome2/cicd-playground) 到你自己的 GitLab 帳戶下。

## 實作
1. [建立 agent configuration file](https://docs.gitlab.com/ee/user/clusters/agent/install/index.html#create-an-agent-configuration-file)   
    在 repo 中預設分支(main or master)，建立一個檔案 `config.yaml`，檔案位置如下
    ```
    .gitlab/agents/<agent-name>/config.yaml
    ```
    `<agent-name>` 改為你想要的 agent 名稱
    比如我的 agent 名稱為 `eks-ithome-demo`，檔案位置則如下圖

    ![agent config path](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午10.34.56.2lkv3wykxxk0.webp)
 2. 授權 agent 能存取此 git project
    編輯上一步的 `config.yaml`，並填寫以下內容
    ```
    ci_access:
      groups:
        - id: <path/to/group/subgroup>
    ```
    `<path/to/group/subgroup>` 請改為你 GitLab project 的路徑。    
    例如我的 Project name 為 `cicd-playground
`，放置於 ithome2 Group，我的 `config.yaml` 內容如下
    ![agent config context](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午10.42.46.29fvpmf2v36s.webp)

3. 註冊 GitLab agent
    1. 於 GitLab project 中點選左側選單/Operate/Kubernetes clusters。
    ![operate/kubernetes cluster](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午10.45.56.504nq63iouo0.webp)
    
    2. 點選 Connect a cluster
    ![Connect a cluster](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午10.46.30.6v4qcehwq1o0.webp)
    3. 選擇 agent configuration file，並點選 Register
    ![select agent config file](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午10.46.56.1ud1ra1xfolc.webp)
    > 這裡的名稱應該會跟 第一步-"建立 agent configuration file" 時，你配置的名稱一樣。
    4. 將 `Install using Helm (recommended)` 下方的指令內容複製下來。
    ![copy Helm install script](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午10.50.53.zetvvpq81ow.webp)
4. 安裝 Gitlab agent 到 EKS Cluster，
   先確認 kubectl 現在連的是 EKS
    ```
    $ kubectl config current-context 
    luciferstut@ithome-demo.ap-northeast-1.eksctl.io

    $ kubectl get node
    NAME                                              STATUS   ROLES    AGE   VERSION
    ip-192-168-6-52.ap-northeast-1.compute.internal   Ready    <none>   13h   v1.25.12-eks-8ccc7ba
    ```

   執行先前複製的 helm 指令
   ```
   helm repo add gitlab https://charts.gitlab.io
    helm repo update
    helm upgrade --install eks-ithome-demo gitlab/gitlab-agent \
    --namespace gitlab-agent-eks-ithome-demo \
    --create-namespace \
    --set image.tag=v16.4.0 \
    --set config.token=glagent-AQ4CNs6enzZEvXNE6***************** \
    --set config.kasAddress=wss://kas.gitlab.com
   ```
    執行完成後，檢查是否安裝完成
    ```
    $ kubectl get pod -n gitlab-agent-eks-ithome-demo 
    NAME                                               READY   STATUS    RESTARTS   AGE
    eks-ithome-demo-gitlab-agent-v1-769c98c749-62xmx   1/1     Running   0          83s
    eks-ithome-demo-gitlab-agent-v1-769c98c749-bcbj2   1/1     Running   0          94s
    ```
    若能看到運行了兩個 GitLab Agent 的 Pod 在 EKS Cluster 中，就代表已經**安裝成功**了

## 測試
1. 我們在專案中根目錄建立一個 `.gitlab-ci.yml`，並填入以下內容
    ```yml
    deploy:
      image:
        name: bitnami/kubectl:latest
        entrypoint: ['']
      script:
        - kubectl config get-contexts
        - kubectl config use-context <path/to/group/subgroup>:<agent-name>
        - kubectl get pods
    ```
    `<path/to/group/subgroup>`: 改為 `config.yaml` 檔案中 `ci_access.groups.id` 的值
    `<agent-name>`: 改為 `config.yaml` 上層目錄的名稱
    ```
    .gitlab/agents/<agent-name>/config.yaml
    ```
    這個腳本內容，會嘗試查詢 Kubernetes default namespace 的 Pod。
2. commit 並 push 到預設分支(main or master)
3. 至此 GitLab repo 左側選單/Build/Pipelines
    ![UI/Build/Pipelines](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.18.58.2yztvrvx1ww0.webp)
   能在右側表單，看到一個 Pipelines 被創建跟自動執行了
   ![UI/Pipeline/task](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.20.21.52mcyabmnco0.webp)
4. 檢視運行日誌
   1. 點選 Pipeline Id(Ex: #1011741728)
    ![pipelineId](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.27.28.7blvn4gfgtk0.webp)
   2. 點選 `deploy` task
    ![deploy task](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.27.12.22sezono9f28.webp)
   3. 能看到 GitLab runner 執行了 `kubectl get pods
`，並收到 EKS Cluster 的 kubernetes API 回應 `No resources found in default namespace.`，代表能使用 GitLab CI 來部署與操作 EKS Cluster 了
    ![runner-log](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-21-下午11.27.07.12y1n5ryzp80.webp)
   

# 總結
今天，我們成功在 EKS Cluster 中安裝了 GitLab Agent，這樣我們就不必在 GitLab 系統中直接儲存 kubeconfig。現在，我們可以使用 GitLab CI 來呼叫 GitLab Agent，啟動 GitLab Runner，然後執行 pipeline script（比如 kubectl get pods）。這表示 GitLab 和 EKS Cluster 之間的通訊是順暢的。

明天，我們將開始實作 `.gitlab-ci.yml`，以實現自動部署 YAML 到 EKS Cluster。

[GitOps]: https://about.gitlab.com/topics/gitops/

[GitLab agent]: https://gitlab.com/gitlab-org/cluster-integration/gitlab-agent

[GitLab runner]: https://docs.gitlab.com/runner/



[helm]: https://helm.sh/docs/intro/install/
