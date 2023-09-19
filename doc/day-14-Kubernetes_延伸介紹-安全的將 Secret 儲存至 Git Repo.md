# Day-14-Kubernetes_延伸介紹-安全的將 Secret 儲存至 Git Repo

# 前言
昨天介紹透過 Secret 來儲存機敏資訊，與 Secret 中是透過 Bese64 編碼儲存資訊的特性，所以需要透過 RBAC 等方式保護 Secret 只能被特定成員/Pod存取，來保護資敏資訊的安全。

在實施 [GitOps](https://www.cncf.io/blog/2021/09/28/gitops-101-whats-it-all-about/) 時，需要將所有 Kubernetes resource 的 yaml(Deployment、Service、Secret..等等)，透過 Git 來管理/部署/追蹤，但將明文 Secret yaml 上傳至 Git Repo，能存取該 Repo 的使用者也能輕易還原經過 Bese64 編碼的機敏資訊。

今天介紹的 [sealed-secrets](https://github.com/bitnami-labs/sealed-secrets) 這個輕量的解決方案，讓你能將 Secret 安全的保存再 Git Repo 中，即使是 public Git Repo 也不會洩漏 Secret 中的機敏資訊。

# 什麼是 GitOps
簡單來說，GitOps是基礎設施即代碼（IaC）的一種實踐方式。它的核心概念是將Kubernetes的配置文件（YAML）存儲在Git存儲庫中，並使用Git來管理所有對Kubernetes的更改。通過提交Git的更改（git commit），自動化程序確保Kubernetes上運行的資源與Git中的配置文件保持一致。这樣做的好處是，它實現了一種可控、可追蹤和自動化的基礎設施管理方法。

![GitOps-image](https://www.cncf.io/wp-content/uploads/2022/07/workshop02_gitops-operating-model.png)
圖檔來至 [CNCF/gitops-101-whats-it-all-about](https://www.cncf.io/blog/2021/09/28/gitops-101-whats-it-all-about/)

## 實施 GitOps 帶來的好處：
1. 一致性和可重現性：GitOps將整個Kubernetes應用程序的狀態存儲在版本控制系統中（例如Git存儲庫），使您能夠輕松地跟踪應用程序的變化和歷史。這確保了您的環境一致性並提供可重現的部署過程。

2. 自動化：GitOps通過自動同步Git存儲庫中的配置和實際環境來實現自動化。當您提交更改時，GitOps工具將自動應用這些更改，從而減少了人為錯誤，提高了可靠性。

3. 安全性：GitOps可以提高安全性，因為它限制了對實際環境的直接更改。所有更改都必須通過Git存儲庫進行審核和批准，從而減少了潛在的風險。

4. 故障恢復和回滾：如果應用程序遇到問題，您可以輕松地回滾到之前的版本，因為所有配置更改都在Git存儲庫中有記錄。這可以加快故障恢復過程。

5. 團隊協作：GitOps促進了跨多個團隊成員的協作。開發人員、運營人員和安全團隊可以共享和討論配置更改，並在Git存儲庫中進行協作，以確保應用程序在不同環境中正確運行。

# 什麼是 Sealed Secrets
Sealed Secrets 是一個開源的 Kubernetes 工具，用於安全地管理和部署Secrets 中的數據，例如密碼、API金鑰、憑證等，它使用非對稱加密的方法，允許這些敏感資料被安全地存儲在 Git Repo 中。

## 主要組件：
- Sealed Secrets Controller：這是 Sealed Secrets 的核心組件，運行於 Kubernetes 叢集中。它的主要工作是處理  （Custom Resource Definition）。它能夠將加密的 Sealed Secret CRD 解密，轉換成應用程式可使用的原始 Secret 數據。

    > CRD（Custom Resource Definition） 是 Kubernetes 中用於自定義資源類型的機制，它允許 Kubernetes 擴展現有的API，以支援自定義資源。 

- kubeseal CLI：kubeseal 是一個命令行工具，用於加密敏感資料，並生成 Sealed Secret CRD。通常情況下，開發人員使用 kubeseal 來將原始敏感資料加密為 Sealed Secret CRD，然後將其存儲在 Git 存儲庫中。

## 實現原理
![Sealed Secrets](https://docs.bitnami.com/tutorials/_next/static/images/life-of-a-fa8059ab3ad28631b8bb8e39a048e056.png.webp)
圖檔來至 [bitnami/Sealed Secrets: Protecting your passwords before they reach Kubernetes](https://docs.bitnami.com/tutorials/sealed-secrets)

基於`非對稱加密`的原理 與 Controller pattern 於 Kubernetes 中自動對其解密

- 生成密鑰對：在 Sealed Secrets 會自動生成一組 key pair(public/private key)。public key 是用於加密資料的，而 private key 則用於解密。
- 使用 kubeseal CLI 加密機敏資料：開發人員透過 kubeseal CLI 使用 public key 加密數據並透過 Sealed Secret CRD，該 CRD 包含了加密後的機敏資料與用於解密的 metadata。
  
- 存儲 Sealed Secret 在 Git Repo 中：生成的 Sealed Secret CRD 能存儲在版本控制的 Git Repo 中，就像一個普通的 Kubernetes 資源一樣。這個步驟確保了機敏資料的版本控制和可追蹤性。
    > 因為數據欄位被加密，要取得機敏資料需要透過 private key 才能解密(private key 儲存於 Kubernetes 中)。
    
- Sealed Secrets Controller 解密資料：當需要使用機敏資料時，Sealed Secrets Controller 監聽並檢測到新的 Sealed Secret CRD。然後，它使用 private key 來解密 Sealed Secret 中的機敏資料，將其轉換為原始的數據。
    
## 安裝
1. 安裝 Sealed Secrets Controller 
```
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.23.1/controller.yaml -n kube-system
```
> 最新的安裝方式與版本能參閱[官方文檔](https://github.com/bitnami-labs/sealed-secrets#installation)
2. 安裝 kubeseal CLI
```
brew install kubeseal
```
> 其他安裝方式參考[官方文檔]([kubeseal](https://github.com/bitnami-labs/sealed-secrets#kubeseal))

## 使用方式
1. 先產生我們要加密的 Secret
```
kubectl create secret generic sealed-secret-example --dry-run=client -o yaml --from-literal=APP.PASSWORD="my password" > sealed-secret-example.yaml
```
sealed-secret-example.yaml 內容
```
apiVersion: v1
data:
  APP.PASSWORD: bXkgcGFzc3dvcmQ=
kind: Secret
metadata:
  creationTimestamp: null
  name: sealed-secret-example
```
> 注意：sealed-secret-example.yaml 中的數據仍是 Base64 編碼的，不可分享或上傳至 Git repo。

2. 使用 kubeseal 將 sealed-secret-example.yaml 數據加密並產生 Sealed Secret CRD 的 yaml (sealed-secret-CRD.yaml)
```
kubeseal -o yaml < sealed-secret-example.yaml > sealed-secret-CRD.yaml
```

sealed-secret-CRD.yaml 內容
```
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  creationTimestamp: null
  name: sealed-secret-example
  namespace: default
spec:
  encryptedData:
    APP.PASSWORD: AgBIu59r1VgU65OaPjlfIGbL2bbudg3WIe9iPAwd2KPWF9xNyMjMs6cUzwhb8acyEETBYuOyv9hu4S07aiQM8JQ8UWpwFYXmVBbdWctgCbPqBamqjJBefJ/RYEodb2pzLpkKAhW0NYCpvlNeYBxcDsZjGtR+3wLfpoS34BX3XoI9ZIJ6umpgSb9hFFUEuVPzDsJUZoZ+tKH4C+GSO5P7g0hjTsboJTH56eW8c5rqP8qfKaeipfljVOpiVhxbdTiaqz61CduXY85T3noiANU3mXNzUFbaGB7PDejQw3i0dcWg2AdbIezV9DOPgUjKeREFy5L3KLYGUd0CygjjRPBLjsahko2UzGkZrI2U0kxXBk4IOFJVHyd5hUxTxYZebXhNQaqetY/j+Xkg/LS0+f9bK2AFd4b9japSc2nBeOJOCzVpmnL+rskr9sljNOGbbN2bhQIuLtpzJ3Xz4qqZLNd0W4GyLM4520j5yTovIVaES6DuxImgOrewdGrf7/ZW+zvIkmndIOqe/oNwELgL8gsIJNrPVblh/r8OolH8LpBadQoePdH/pB3YoTHdWwWVbzXFxTRreFtpBdJL89gEia6p8W9PhvgfjwjWuaJqA10bvuN+XYsZjzZzEEYQQGtHn3f71hQ3IctpB/YCFfsdu2PyJsQdJiJ+kfAwIKd1xADdup+OOOhsyM5vr0l5x+xGKWviRBtsVf+XWgiAmEejRQ==
  template:
    metadata:
      creationTimestamp: null
      name: sealed-secret-example
      namespace: default
```
> sealed-secret-CRD.yaml 中的數據是加密過後的，只要 Kubernetes 中的 private key 沒有外洩，此 yaml 能安全的上傳至 Git Repo。

3. 將 sealed-secret-CRD.yaml 上傳至 Kubernetes
```
kubectl apply -f sealed-secret-CRD.yaml
```
上傳 Sealed Secret CRD 後，Sealed Secrets Controller 會自動解密並生成標準 Kubernetes Secrets
```
# 查 Sealed Secret CRD
kubectl get sealedsecrets.bitnami.com sealed-secret-example

# 查 Kubernetes Secrets
kubectl get secret sealed-secret-example
```

驗證 secret/sealed-secret-example 中數據是否是 `my password`
```
# 解密 Secret 中 data.APP.PASSWORD
kubectl get secret sealed-secret-example -o jsonpath="{.data['APP\.PASSWORD']}" | base64 --decode
```

![decode-secret](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230913/截圖-2023-09-13-上午8.03.42.2doeghu7zlus.webp)

# 總結
今天簡單介紹了實施 GitOps 的好處 與 如何用 [sealed-secrets](https://github.com/bitnami-labs/sealed-secrets) 加密Secret manifast，使其可被上傳至 Git Repo，不必擔心資料外洩。