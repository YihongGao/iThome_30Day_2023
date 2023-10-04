# day-30-Quarkus 介紹

# 前言
昨天介紹了 水平自動伸縮 (Horizontal Pod Autoscaler)，能因應 metrics (資源使用率 或 流量) 自動擴展或縮減 Pod 的數量，來保持系統強健與節省成本。
當系統需要進行自動擴展時，我們通常希望越快完成擴展越好，所以服務的啟動時間也是一個關鍵。    

今天要介紹的 `Quarkus` 這個 Java 框架，搭配 `GraalVM` 能有效的將服務的啟動時間降低至**毫秒級**，讓 HPA 觸發擴展後，極快的啟動應用程序，讓 Pod 準備好接受流量。

# GraalVM
[GraalVM] 是一個高性能的虛擬機 (VM)，支援多種程式語言，包括 Java，並且能夠將 Java 應用程式編譯成獨立的二進位檔（Native Image）。這些二進位檔較小，啟動速度最多快 100 倍，無需預熱即可提供最佳性能，並且在運行時消耗的記憶體和 CPU 較少(跟傳統 JVM 比較)。

簡單來說，GraalVM 可以作為虛擬機（VM）來運行應用程式，同時也具有將 Java 代碼編譯成本機二進位檔的功能，並且主流的 Java framework 的支持 (如 Spring boot、Quarkus)，讓 Java 也有機會跟 Golang 等高效能語言相比較。

## GraalVM 的編譯方式
GraalVM 提供了 JIT(just-in-time) 與 AOT(ahead-of-time) 兩種編譯方式

![GraalVM compile mode](https://media.licdn.com/dms/image/C4D12AQHPctJFroIGAA/article-cover_image-shrink_423_752/0/1631986935583?e=1701907200&v=beta&t=ZGj1dKscdDipYGg9yFMy5e3fOaZ2bZzrczkPWDrQZmo)
圖檔來自 - [Make Linux syscalls from Java with help of GraalVM](https://www.linkedin.com/pulse/make-linux-syscalls-from-java-help-graalvm-braynix-ai/)

### JIT (just-in-time)
如同傳統 JDK (Oracle JDK、OpenJDK)一樣，能將原始碼(java file) 透過 `javac` 編譯為 class file，當運行應用程序時(使用 JVM 運行這些 class file)，JVM 會解讀 class file 中的 JVM bytecode，並將其轉換為 `machine code` 來執行代碼行為，這個機制實現了 __**Java write once run anywhere**__

![JIT](https://www.cesarsotovalero.net/assets/resized/java_source_code_compilation-640x307.png)
圖檔來自 - [AOT vs. JIT Compilation in Java]

> 使用 JIT 方式編譯原始碼雖然能跨多平台，但 JVM bytecode 在運行時，需要轉換成 `machine code` 的行為，也導致了啟動時間較慢的現象(跟 C、Golang 比較)。

### AOT (ahead-of-time)
AOT 屬於一種靜態編譯，能在編譯階段(應用程序執行之前)，就將原始碼(java file) 轉換為 `machine code`，讓應用程序運行時，能直接透過 `machine code` 進行代碼行為，不需要 JVM 多執行一次轉換，達到媲美 C、Golang 這類語言啟動快速的能力。

![AOT](https://www.cesarsotovalero.net/assets/resized/native_image_creation_process-1024x304.png)
圖檔來自 - [AOT vs. JIT Compilation in Java]

> AOT 直接編譯出 `machine code`，節省了將 JVN bytecode 轉譯的時間，且會自動移除掉多餘的代碼，並限制了 reflection 能力，相對來說，有比較好的安全性與較穩定的資源使用率。    
> 但因為直接編譯成了 `machine code`，導致編譯出的成品，失去跨平台能力，需要針對運行平台來編譯。


## Quarkus Framework
Quarkus 框架是一個針對 Kubernetes、Serverless、微服務和雲原生應用程式開發而設計的現代 Java 框架。它的目標是提供低記憶體使用、快速啟動和優化運行時效能的 Java 開發體驗，特別適用於雲原生環境。

- 快速啟動：Quarkus 能夠在極短的時間內啟動應用程式，這對於需要高效能的微服務和Serverless應用程式非常有利。

- 低記憶體使用：Quarkus 設計用於節省記憶體，並最小化應用程式的記憶體占用，這對於容器化應用程式和 Kubernetes 部署非常有用。

- 高效能：Quarkus 支援即時編譯（JIT）和預先編譯（AOT）的選項，以最大程度地優化應用程式的效能。

- 支援多種程式語言：除了 Java，Quarkus 還支援其他程式語言，如 Kotlin、Scala 和 Groovy。

- 原生 GraalVM 整合：Quarkus 預先編譯支援，可以使用 GraalVM 創建本機映像，從而提供更快的啟動時間和更低的記憶體使用。

- 多元的擴充套件：支援 Spring boot、Hibernate、JDBC、MySQL client、Kafka client、Redis client..等套件。

## 為什麼要用 Quarkus
除了上述的特性非常適合與 Kubernetes 的 HPA 互相協作外，筆者認為 Quarkus 豐富的擴充也是非常好用的，因為許多套件或框架都大量使用 reflection 的功能，這在搭配 GraalVM 時會造成阻礙。
> 例: 使用 AOT 時，因為靜態編譯無法感知到 reflection 要建構的 Class，導致 native-image 會缺少該 class，導致 ClassNotFound Exception。

Quarkus extension interface 提供於編譯階段能處理這類收集 metadata 的作業，更容易搭配 GraalVM 一起使用。
> 詳細介紹能參考 [Quarkus 官方文檔](https://quarkus.io/guides/writing-extensions)

> **補充:** 現在 Spring boot 3 也支援 AOT 編譯 build native-image，有興趣的讀者也能嘗試看看，參考 [spring 官方](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)。

## 實作

### 前置步驟
1. 安裝 GraalVM，參考 [GraalVM 官方手冊/install GraalVM](https://www.graalvm.org/22.0/docs/getting-started/#install-graalvm)
2. 配置 `GRAALVM_HOME` 與 `JAVA_HOME` 環境變數，參考 [Quarkus 官方手冊](https://quarkus.io/guides/building-native-image#configuring-graalvm)
3. 安裝 `Native Image` 工具，參考 [GraalVM 官方手冊/Native Image](https://www.graalvm.org/22.0/reference-manual/native-image/)
4. 檢查環境變數是否設定完成 與 java 版本是為 GraalVM
   ```shell
   $ echo $GRAALVM_HOME
   # show you GRAALVM path

   $ echo $JAVA_HOME
   # show you GRAALVM path

   $ java -version

   openjdk version "17.0.8" 2023-07-18
   OpenJDK Runtime Environment GraalVM CE 17.0.8+7.1 (build 17.0.8+7-jvmci-23.0-b15)
   OpenJDK 64-Bit Server VM GraalVM CE 17.0.8+7.1 (build 17.0.8+7-jvmci-23.0-b15, mixed mode, sharing)
   ```

### 建立一個使用 Quarkus 的應用程序
我們來嘗試撰寫一個使用 Quarkus 的應用程序
1. 開啟瀏覽器，並輸入 `https://code.quarkus.io/`，連到官方提供的 initializer
![Quarkus-initializer](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231004/截圖-2023-10-04-下午4.52.22.7is8y2k00co0.webp)
2. 選擇你想要使用的 extension，此範例使用以下 extension
   1. Filters 輸入 `yaml`，並勾選 `YAML Configureation`
   2. Filters 輸入 `spring`，勾選 `Quarkus Extension for Spring Web API`
3. 點選 `Generate you application` 下載初始化的 project
4. 使用 IDE 開啟該 Project，能看到已經提供了範例程式碼
   ```java
   package org.acme;

   import org.springframework.web.bind.annotation.GetMapping;
   import org.springframework.web.bind.annotation.RequestMapping;
   import org.springframework.web.bind.annotation.RestController;

   @RestController
   @RequestMapping("/greeting")
   public class GreetingController {

      @GetMapping
      public String hello() {
         return "Hello Spring";
      }
   }
   ```
5. 啟動應用程序
   1. 選項一： 使用 IDEA 搭配 quarkus plugin    
      例： 使用 IntelliJ IDEA 並安裝 [Quarkus run config plugin](https://plugins.jetbrains.com/plugin/14242-quarkus-run-configs)即可快速的啟動應用程序，使用方式參考 [IntelliJ 文檔](https://www.jetbrains.com/help/idea/quarkus.html#run-app)
   2. 選項二： 用 [quarkus cli](https://quarkus.io/guides/cli-tooling) 運行應用程序
      ```shell
      quarkus dev
      ```
      > 筆者目前使用 IntelliJ IDEA、VS Code 開發 Quarkus 應用程序體感都蠻順的，該有的 debug 功能都有，讀者能挑自己習慣的使用。不過早期使用 Eclipse 時，常出現一些奇怪的問題，不確定是否已經穩定。

   啟動 Log
   ```log
   Listening for transport dt_socket at address: 5005
   __  ____  __  _____   ___  __ ____  ______ 
   --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
   -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
   --\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
   2023-10-04 19:40:17,011 INFO  [io.quarkus] (Quarkus Main Thread) code-with-quarkus 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.4.1) started in 1.237s. Listening on: http://localhost:8080

   2023-10-04 19:40:17,015 INFO  [io.quarkus] (Quarkus Main Thread) Profile dev activated. Live Coding activated.
   2023-10-04 19:40:17,015 INFO  [io.quarkus] (Quarkus Main Thread) Installed features: [cdi, config-yaml, resteasy-reactive, resteasy-reactive-jackson, smallrye-context-propagation, spring-web, vertx]
   ```
   能看到應用程序已經啟動，啟動花費的時間為 `1.237s.`
6. 測試 API 
   ```shell
   $ curl localhost:8080/greeting
   Hello Spring
   ```


### 編譯成品
   - 使用 JIT 建構 [Uber-jar(Fat-jar)](https://blog.payara.fish/what-is-a-java-uber-jar)
      1. 使用建構指令，編譯並產生 native-image
         - maven  
            ```shell
            ./mvnw clean package -Dquarkus.package.type=uber-jar   
            ```
         - gradle
            ```
            ./gradlew clean build -Dquarkus.package.type=uber-jar 
            ``` 
       2. 檢視成品，成品會放在 target 或 build 目錄下，並有 `runner.jar` 後綴
            ```shell
            # maven 建構的成品會在 target目錄，若使用 gradle 會在 build 目錄
            ls ./target/*-runner

            ./target/code-with-quarkus-1.0.0-SNAPSHOT-runner.jar
            ```
      3. 運行 uber-jar
         ```
         java -jar ./target/code-with-quarkus-1.0.0-SNAPSHOT-runner.jar
         ```
         啟動 Log
         ```log
         __  ____  __  _____   ___  __ ____  ______ 
         --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
         -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
         --\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
         2023-10-04 20:55:48,473 INFO  [io.quarkus] (main) code-with-quarkus 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.4.1) started in 0.413s. Listening on: http://0.0.0.0:8080
         2023-10-04 20:55:48,475 INFO  [io.quarkus] (main) Profile prod activated. 
         2023-10-04 20:55:48,475 INFO  [io.quarkus] (main) Installed features: [cdi, config-yaml, resteasy-reactive, resteasy-reactive-jackson, smallrye-context-propagation, spring-web, vertx]
         ```
         啟動時間為 `0.413s`         
   - 使用 AOT 建構 build-image
      1. 使用建構指令，編譯並產生 native-image
         - maven
            ```shell
            ./mvnw install -Dnative
            ```
         - gradle
            ```shell
            ./gradlew build -Dquarkus.package.type=native
            ```
         建構時間可能需要數十秒到數分鐘，當建構完畢時，成品會出現在 target 目錄
      2. 檢視成品，target 或 build 目錄下應有個 `runner` 後綴的 native-image 可執行檔
         ```shell
         # maven 建構的成品會在 target目錄，若使用 gradle 會在 build 目錄
         ls ./target/*-runner

         ./target/code-with-quarkus-1.0.0-SNAPSHOT-runner
         ```
         > 檔案名稱中的 `code-with-quarkus-1.0.0-SNAPSHOT` 是依據 Project 的 [GAV (Group, Artifact, Version coordinate)](https://stackoverflow.com/questions/71835160/what-is-the-meaning-for-gav-in-maven-context) 命名
      3. 使用 native-image 運行應用程序
         ```shell
         ./target/code-with-quarkus-1.0.0-SNAPSHOT-runner 
         ```
         啟動 Log
         ```log
         --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
         -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
         --\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
         2023-10-04 20:16:07,122 INFO  [io.quarkus] (main) code-with-quarkus 1.0.0-SNAPSHOT native (powered by Quarkus 3.4.1) started in 0.075s. Listening on: http://0.0.0.0:8080
         2023-10-04 20:16:07,123 INFO  [io.quarkus] (main) Profile prod activated. 
         2023-10-04 20:16:07,123 INFO  [io.quarkus] (main) Installed features: [cdi, config-yaml, resteasy-reactive, resteasy-reactive-jackson, smallrye-context-propagation, spring-web, vertx]
         ```
         能看到啟動日誌顯示啟動時間為 `0.075s`，大幅的減少啟動時花費的時間。
      

### 延伸-引入更多 Quarkus extension
1. 開啟瀏覽器，進入 Quarkus 官方網站 `https://quarkus.io/`
2. 點選右上選項 `EXTENSIONS / BROWSE EXTENSIONS`，進入 extensions menu
![BROWSE EXTENSIONS](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231004/截圖-2023-10-04-下午9.47.26.3y5s570blcy0.webp)
3. 從左邊過濾器能快速搜尋想要的 extension，如使用 Spring boot、MySQL、Redis、Kafka 等關鍵字。    
   假設我想找 Spring DI 來使用習慣的 `@Autowired`，輸入 Spring 關鍵字，能在選項中看到 `Quarkus Extension for Spring DI API`
4. 點擊想要的 Extension 選項，能看到該 extension 介紹與安裝指令
   ![extension-detail-page](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20231004/截圖-2023-10-04-下午9.58.03.3lkui6qonic0.webp)
5. 於專案目錄，執行安裝指令
   ```shell
   # 已 maven 為例，其他指令能在 extension 頁面上看到。
   ./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-spring-di"
   
   [INFO] Scanning for projects...
   [INFO] 
   [INFO] ---------------------< org.acme:code-with-quarkus >---------------------
   [INFO] Building code-with-quarkus 1.0.0-SNAPSHOT
   [INFO]   from pom.xml
   [INFO] --------------------------------[ jar ]---------------------------------
   [INFO] 
   [INFO] --- quarkus:3.4.1:add-extension (default-cli) @ code-with-quarkus ---
   [INFO] Looking for the newly published extensions in registry.quarkus.io
   [INFO] [SUCCESS] ✅  Extension io.quarkus:quarkus-spring-di has been installed
   [INFO] ------------------------------------------------------------------------
   [INFO] BUILD SUCCESS
   [INFO] ------------------------------------------------------------------------
   [INFO] Total time:  12.883 s
   [INFO] Finished at: 2023-10-04T21:59:27+08:00
   [INFO] ------------------------------------------------------------------------
   ```
6. 開啟 pom.xml 或 gradle.build 中已經包含 extension 的依賴項目
   ```xml
   <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-spring-di</artifactId>
    </dependency>   
   ```
7. 測試依賴項目是否可用
   - 新增一個 UserService.java
      ```java
      package org.acme;

      import org.springframework.stereotype.Service;

      @Service
      public class UserService {

         public String getUserName(){
            return "ithome-demo";
         }
      }
      ```
   - 更改 GreetingController.java 內容為
      ```java
      package org.acme;

      import org.springframework.beans.factory.annotation.Autowired;
      import org.springframework.web.bind.annotation.GetMapping;
      import org.springframework.web.bind.annotation.RequestMapping;
      import org.springframework.web.bind.annotation.RestController;

      @RestController
      @RequestMapping("/greeting")
      public class GreetingController {

         // 使用 DI 注入 UserService
         @Autowired
         private UserService userService;

         @GetMapping
         public String hello() {
            // 使用 UserService 取得 user 名稱來組成 response body
            return "Hello " + this.userService.getUserName();
         }
      }
      ```
   7. 再次 build native-image
      ```shell
      ./mvnw install -Dnative -DskipTests
      ```
      > 加上 -DskipTests 跳過 JUnit 測試
   8. 執行 native-image 啟動應用程序 並 呼叫 API
      ```shell
      # 啟動應用程序
      ./target/code-with-quarkus-1.0.0-SNAPSHOT-runner

      # 開另一個 terminal 呼叫 API
      curl localhost:8080/greeting
      Hello ithome-demo
      ```
      能看到返回值包含來自 UserService 提供的字串，代表 Spring boot DI extension 運作正常。

# 總結
今天介紹了 Quarkus 這個為了 Kubernetes、Serverless 等雲原生生態設計的 Java framework，將 Java 應用程序編譯為 native-image 將具有以下優點

- 快速啟動：跟 HPA 等 scale 組件合作良好，能最快速的進行縮放
- 低內存消耗：通常 native-image 使用的內存量較少，能有效的降低成本
- 穩定的效能：因不需於 runtime 轉譯為 `machine code`，通常效能較穩定
- 較小的檔案大小：native-image 中只保留必要的代碼，會捨棄冗余的套件或原始碼，故成品的檔案大小通常較小
- 更好的安全性：因為冗余的套件或原始碼會被移除，並且抑制了 `reflection` 的應用，這降低了應用程序被攻擊的位面

但 native-image 同時也具有以下缺點
- 編譯時間較長：因為需要進行靜態編譯，編譯時需要較多的 CPU 與 memory，並且大多編譯花費的時間是分鐘級的(通常 JIT 編譯是秒級)。
   > 建議本地開發透過 JIT 編譯與測試，部署上測試/正式環境時，才使用 AOT。
- 部分套件不支援 或 不穩定：許多套件都依賴了 `reflection` 功能來實現，但在 AOT 編譯無法感知哪些是被要的類別，可能會導致套件的行為使用 JIT 與 AOT 編譯後的行為不一致。
   > 大部分 Quarkus extension 都能正常使用，筆者目前只遇過 Camel extension 有此問題，但查這種 bug 通常很花時間😭

以上就是 iThome 鐵人賽最後一篇分享，希望對讀者能有一些收穫，若發現文章中有任何錯誤的訊息也請不吝告知，謝謝。

另外有一些主題沒分享到，先留幾個方向給有需要的讀者
- yaml 管理：單純使用原始的 yaml，在管理多環境(開發/測試/正式環境)時，要複製很多份很像的 yaml，只有少部分內容不同(如 URL，log level)，很不好管理這些 yaml，能研究 [kustomize](https://kustomize.io/) 或 [helm chart](https://helm.sh/)
- GitOps 實現：本系列為了簡化案例使用 push 模式的 GitOps，但 push 模式需要較高團隊自制力與較高部署頻率才能確保 manifest repo 與 實際 cluster 一致。
   透過 pull 模式的會更穩定保持兩端一致性，能參考 [ArgoCD](https://argoproj.github.io/cd/) 與 [Flux CD](https://fluxcd.io/)
- 更好的部署策略：能參考 [ArgoRollout](https://argoproj.github.io/rollouts/)，提供了藍綠部署與金絲雀部署等高級方案


# 參考
- [GraalVM]
- [AOT vs. JIT Compilation in Java]
- [Maximizing Performance with GraalVM]
  


[AOT vs. JIT Compilation in Java]: https://www.cesarsotovalero.net/blog/aot-vs-jit-compilation-in-java.html

[GraalVM]: https://www.graalvm.org/

[Maximizing Performance with GraalVM]: https://www.infoq.com/presentations/graalvm-performance/