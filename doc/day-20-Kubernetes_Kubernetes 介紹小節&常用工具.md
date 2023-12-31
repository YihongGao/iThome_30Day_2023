# Day-20-Kubernetes_知識回顧 與 常用工具分享

# 回顧
在我們進入到 AWS EKS 與 CI/CD 之前，讓我們迅速回顧一下我們已經學到的 Kubernetes 核心組件。這些組件是 Kubernetes 運行的基礎，理解它們將有助於我們更好地掌握整個系統的運作。

- Pod：Pod 是最小的可部署單位，它可以包含一個或多個容器。Pod 共享相同的網絡命名空間和存儲卷，用於確保容器之間的通信和數據共享。
  - Resource mangement: 控制最低與最多能使用的計算資源(CPU/Memory)，保障應用程序穩定
  - QoS (Quality of Service): 透過服務分級，降低關鍵服務被驅逐的機會
  - Liveness, Readiness：偵測應用程序可用性, 以利快速反應
  - 優雅關閉 (graceful shutdown): 進行應用程序更新時，能跟平滑的切換版本，降低對用戶的影響
- Deployment：Deployment 提供了高級的控制和管理功能，並能夠自動處理應用程序的升級、回滾、自我修復等操作，降低我們管理 Pod 的複雜度 與 提供更高的可用性。
  - Rolling update：服務更新時，逐一替換 Pod 實例，保障服務可用性
  - 快速回滾：服務更新失敗時，能快速回滾至歷史版本
  - 自我修復：當 Pod 故障 或 節點崩潰時，會嘗試自我修復，已滿足期望狀態。
> Deployment 是非常好的 Pod 管理組件，通常建議用 Deployment 管理 Pod，才能享有 Deployment 的功能，不需要直接建立 Pod。

- Service：Service 定義了一個穩定的網絡端點，用於暴露一個或多個 Pod。它可以是 ClusterIP、NodePort 或 LoadBalancer 類型，根據你的需求進行配置。
  - DNS name： 提供穩定的網絡端點供調用
  - 負載均衡：將流量分配給多個 Pod，提高可用性

- Ingress：Ingress 允許你管理入站網絡流量，通常用於路由 HTTP 和 HTTPS 流量到不同的 Service。它是一個強大的 API 對象，支持多種路由規則和 SSL/TLS 終止。

- ConfigMap 和 Secrets：ConfigMap 用於存儲配置數據，Secrets 則用於存儲敏感數據。它們可以在應用程式中以環境變數或卷的形式使用。
  - 配置檔與應用程序解耦：提高應用程序容器可移植性，也降低機敏資訊洩漏風險
  - [sealed-secrets]：透過 [sealed-secrets] 來保護 Secrets yaml，讓 Secrets yaml 也能透過 GitOps 管理。

- Namespace：用於劃分 Kubernetes 集群，允許多個團隊或應用程序共享同一個集群而不相互干擾。

- ResourceQuota：ResourceQuota 用於限制 Namespace 內資源的使用，確保資源分配的合理性和可控性。

- LimitRange：LimitRange 定義容器的資源限制，確保容器不會無限制地消耗資源。

我們介紹了以上 Software engineer 最常接觸到的 Kubernetes 組件，希望能讓讀者對 **應用程序如何運行在 Kubernetes** 與 **Kubernetes 怎麼處理將網路流量傳給應用程序** 有個基礎認識。

![Resource Map](https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2F1rMrj%2FbtqZvNBjz77%2FEcTAhCHZHbPPeVgltsPEpK%2Fimg.png)
圖檔來自 [Kubernetes Resource Map (Resource 기능 영역별)](https://clarkshim.tistory.com/155)
Kubernetes 中還有許多組件沒介紹到，礙於篇幅有限，就留給各位自行去探索了，能從上圖快速找到對應的組件名稱。
- IAM 區塊：帳號與權限相關
- Storage：儲存、持久化相關
- Pod Generator：
  - CronJob/Job：定時或單次任務
  - StatefulSet：管理有狀態的 Pod，如 Database
  - DaemonSet：每個節點要運行一個 Pod，如日誌收集
  

# 常用工具分享
主要介紹兩個部分
- **Kubernetes Dashboard**：一個 Web GUI，提供能從圖形化的網頁介面上管理 Kubernetes。
- **kubectl plugins**：管理或開發 kubernetes 時，勢必不斷的使用 `kubectl`，所以社群開發了許多 **kubectl plugins** 來提高我們操作的效率。

### [Kubernetes Dashboard]
#### **Install**
到[官網/release清單](https://github.com/kubernetes/dashboard/releases)中找到符合你 Kubernetes 版本的配置檔，並將 Manifest(yaml) apply 到 kubernetes。

```
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.6.1/aio/deploy/recommended.yaml
```
> 此版本支持 Kubernetes 1.21 ~ 1.24

#### **建立 Service Account 並取得 token 用於登入**
透過 `kubectl apply` 以下三個 yaml，來建立 ServiceAccount 與綁定角色權限
```
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
```
```
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: kubernetes-dashboard
```
```
apiVersion: v1
kind: Secret
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
  annotations:
    kubernetes.io/service-account.name: "admin-user"   
type: kubernetes.io/service-account-token  
```

取得登入 token
```
kubectl get secret admin-user -n kubernetes-dashboard -o jsonpath={".data.token"} | base64 -d
```
會返回一串登入用的 token，先 **複製** 該 token
```
eyJhbGciOiJSUzI1NiIsImtpZCI6IjZoM3htY3JzbGx6MU9sdy15SVczUC0wNHM2cHVDT05GSWJHRjV3QnBQdkEifQ.eyJpc3MiOiJrdWJCCm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrdWJlcm5ldGVzLWRhc2hib2FyZCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJhZG1pbiX1c2VyIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImFkbWluLXVzZXIiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZXY2UtYWNjb3VudC51aWQiOiI0ZGYxYzQyMC00ZTZhLTQ2NWEtOGJiYi02MmIzNTBjOTI0YTMiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a3ViZXJuZXRlcy1kYXNOYm9hcmQ6YWRtAW4tdXNlciJ9.VBM30N88j_xfhZqwUzYsRwS8SX1RgpGgBAgRjllSGUoa3Uaw7sd-9aRVPg2gR_r4nAovzfC46nz1i3S1GyiFfTrWf4j86Tkp-SAm-9llHFU5C-1lOcuHylexqe590XLyGGa8kd4tEPvUl9Q8aHyCRZYaTV7uMhDsV7FPJp3Pmo8ZUr_TcNw_zx-pIoYQ5WQV9okMDDDdwKTXSlb0K_1PlXdagbSxsE2THREBN5ZXMMSA-8Ps9t_yLR6KUwmGGqf3NOMhHXti8nBtDR03Mqy7zpkE3JUGAsCZCLRIQXPdiRAgxDWoJ5e-VKvx5FWN8ZqTpamf4L-xNymaRWs0LySE3A
```
> token 內容每個人都不同，你需要複製自己的

#### **運行 Proxy**
透過 `kubectl proxy` 將 dashboard pod，曝露在 [http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/#/login](http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/#/login)，讓我們能訪問
```
kubectl proxy 
```
> 也能自行透過 Service 或 Ingress 等方式曝露

#### **開啟 Dashboard**
開啟瀏覽器，連至 [http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/#/login](http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/#/login)
能看到以下畫面
![dashboard-login](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-20-上午1.55.22.5py26tegyo40.webp)

將稍早複製的 token 貼到 `輸入 token` 的欄位，並點選登入
![dashboard-index](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-20-上午1.57.27.7imc9hrsrf40.webp)
登入之後，就能看到 Dashboard 的首頁，能看到有 Cluster 中 Workload 運行狀態的圖表、左側有各種組件能檢視。

## kubectl plugins
今天就來介紹幾個筆者實務上常用的 **kubectl plugins** 與 **Kubernetes Dashboard**

### 1. [Krew] - kubectl plugins 管理工具
[Krew] 是個 plugins 管理工具，讓我們輕鬆安裝和管理 kubectl plugins。

#### **Install**
MacOS/Linux
1. Make sure that git is installed.
2. Run this command to download and install krew:
```
(
  set -x; cd "$(mktemp -d)" &&
  OS="$(uname | tr '[:upper:]' '[:lower:]')" &&
  ARCH="$(uname -m | sed -e 's/x86_64/amd64/' -e 's/\(arm\)\(64\)\?.*/\1\2/' -e 's/aarch64$/arm64/')" &&
  KREW="krew-${OS}_${ARCH}" &&
  curl -fsSLO "https://github.com/kubernetes-sigs/krew/releases/latest/download/${KREW}.tar.gz" &&
  tar zxvf "${KREW}.tar.gz" &&
  ./"${KREW}" install krew
)
```
3. Add the $HOME/.krew/bin directory to your PATH environment variable. To do this, update your .bashrc or .zshrc file and append the following line:
```
export PATH="${KREW_ROOT:-$HOME/.krew}/bin:$PATH"
```
4. restart your shell
5. Run kubectl krew to check the installation.
```
kubectl krew version
```
  
> 其他作業系統，能參考[安裝手冊](https://krew.sigs.k8s.io/docs/user-guide/setup/install/)

後續安裝 plugin 都會透過 [Krew] 進行安裝.

### 2. [kubectx & kubens]
[kubectx & kubens] 在多個 Kubernetes cluster 或 Namespace 切換時特別有用，直覺簡短的指令，減少發生錯誤的機會。

#### **Install**
```
kubectl krew install ctx
kubectl krew install ns
```

#### **Demo**
`kubectx` 用來快速切換操作的 Cluster (contexts)，不需要用冗長的 `kubectl config use-context` 指令
![kubectx-show-case](https://github.com/ahmetb/kubectx/raw/master/img/kubectx-demo.gif)

`kubens` 用來切換 Namespace，不需要用冗長的 `kubectl config use-context` 指令 或 每次都要加上 `--namespace` 參數
![kubens-show-case](https://github.com/ahmetb/kubectx/raw/master/img/kubens-demo.gif)

### 3. [kubectl tree]
能快速查到 Kubernetes 組件之間的關聯性，管理或學習時都很好用

#### **Install**
```
kubectl krew install tree
kubectl tree --help
```
#### **Demo**
![kubectl tree show-case](https://github.com/ahmetb/kubectl-tree/raw/master/assets/example-1.png)

### 4. [kubectl explore]
[kubectl explore] 能快速查閱各個組件的 yaml 屬性，對於筆者這種腦容量有限的人很實用，只要記得功能，透過 `kubectl explore` 就能找到正確屬性名稱

#### **Install**
```
kubectl krew install explore
kubectl explore --help
```

# 總結
今天回顧了許多核心組件，已經能建構自己的服務並提供給外部存取，接下來幾天會開始在使用 AWS EKS 建置 kubernetes 環境，並進行 CI/CD 與 監控等介紹。

#### **Demo**
![kubectl explore](https://github.com/keisku/kubectl-explore/raw/main/demo.gif)

[Kubernetes Dashboard]: https://github.com/kubernetes/dashboard

[Krew]: https://krew.sigs.k8s.io/

[kubectx & kubens]: https://github.com/ahmetb/kubectx

[sealed-secrets]: https://github.com/bitnami-labs/sealed-secrets

[kubectl tree]: https://github.com/ahmetb/kubectl-tree

[kubectl explore]: https://github.com/keisku/kubectl-explore