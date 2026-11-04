# ---------- 第一階段：用 Maven 打包成 jar ----------
# 多階段建置（multi-stage build）：打包用的 Maven、JDK 只留在這一階段，最後的映像檔只有 JRE + jar，比較小
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先只複製 pom.xml 下載套件：pom 沒改時 Docker 會重用這一層的快取，不用每次重新下載
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# application.properties 有密碼，沒有放進 Git；跟 CI 一樣改用範本檔，正式環境的值由 application-prod.properties + 環境變數提供
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties
# 測試已經在 GitHub Actions 的 CI 跑過，這裡跳過，部署比較快
RUN mvn -B -q package -DskipTests

# ---------- 第二階段：只放執行需要的東西 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 不用 root 執行：萬一程式被攻破，攻擊者拿到的權限也比較小
RUN useradd --system --create-home ezstock
USER ezstock

COPY --from=build /app/target/EZStockApi-*.jar app.jar

# Render 免費方案只有 512MB 記憶體，容器記憶體不等於 heap，要留空間給 metaspace、執行緒、code cache：
# - MaxRAMPercentage=60：heap 用容器記憶體的 60%（約 300MB）
# - UseSerialGC：單執行緒 GC，額外開銷最小，小 heap 很適合
# - Xss512k：每條執行緒的 stack 從 1MB 減半
# - TieredStopAtLevel=1：JIT 只做簡單編譯，省記憶體、啟動快
# - user.timezone：容器預設是 UTC，log 時間會差 8 小時；排程和「今天」本來就固定用台北時區，這裡讓 log 也一致
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -Duser.timezone=Asia/Taipei"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
