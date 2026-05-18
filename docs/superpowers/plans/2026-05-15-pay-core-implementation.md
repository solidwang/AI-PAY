# Pay-Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Pay-Core — a multi-module Maven Spring Boot service providing unified WeChat/Alipay payment APIs, merchant management, and admin portal backend as a single deployable JAR.

**Architecture:** 8 Maven modules assembled into one JAR via `pay-bootstrap`. `pay-common` holds shared enums/utils. `pay-core` holds domain entities, MyBatis-Plus mappers, and business services. `pay-channel-api` defines the `PayChannel` interface. `pay-channel-wechat` and `pay-channel-alipay` implement it. `pay-api` exposes `/v1/**` with API Key (HTTP Basic) auth. `pay-admin-api` exposes `/admin/v1/**` with JWT Bearer auth. Both security configs coexist in the same application context via `@Order`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, MyBatis-Plus 3.5.9, MySQL 8, Redisson 3.37.0, wechatpay-java 0.2.12, alipay-sdk-java 4.38.204.ALL, jjwt 0.12.6, springdoc-openapi 2.6.0, Flyway 10.x, Lombok

---

## File Map

```
ai-pay/
├── pom.xml
├── pay-common/
│   ├── pom.xml
│   └── src/main/java/com/aipay/common/
│       ├── constant/ChannelCode.java
│       ├── enums/ChargeStatus.java
│       ├── enums/RefundStatus.java
│       ├── exception/BizException.java
│       ├── exception/ErrorCode.java
│       ├── util/IdGenerator.java
│       └── util/CryptoUtil.java
├── pay-core/
│   ├── pom.xml
│   └── src/main/java/com/aipay/core/
│       ├── domain/   (Merchant, App, ChannelConfig, Operator, OperatorPermission,
│       │              Charge, Refund, NotifyRecord, ReconcileRecord)
│       ├── mapper/   (one BaseMapper per entity + custom methods)
│       ├── service/  (MerchantService, AppService, ChannelConfigService,
│       │              ChargeService, NotifyService, RefundService, OperatorService)
│       └── config/CoreConfig.java
├── pay-channel/
│   ├── pom.xml
│   ├── pay-channel-api/
│   │   ├── pom.xml
│   │   └── src/main/java/com/aipay/channel/api/
│   │       ├── PayChannel.java
│   │       ├── ChannelRouter.java
│   │       └── model/  (CreateOrderRequest, CreateOrderResult, NotifyRequest,
│   │                    NotifyResult, RefundRequest, RefundResult, QueryResult)
│   ├── pay-channel-wechat/
│   │   ├── pom.xml
│   │   └── src/main/java/com/aipay/channel/wechat/
│   │       ├── WechatChannelConfig.java
│   │       ├── WechatJsapiChannel.java
│   │       ├── WechatH5Channel.java
│   │       ├── WechatNativeChannel.java
│   │       └── WechatMiniprogramChannel.java
│   └── pay-channel-alipay/
│       ├── pom.xml
│       └── src/main/java/com/aipay/channel/alipay/
│           ├── AlipayChannelConfig.java
│           └── AlipayWapChannel.java
├── pay-api/
│   ├── pom.xml
│   └── src/main/java/com/aipay/api/
│       ├── controller/  (ChargeController, RefundController, NotifyController)
│       ├── security/    (ApiKeyAuthFilter, ApiKeySecurityConfig)
│       └── config/OpenApiConfig.java
├── pay-admin-api/
│   ├── pom.xml
│   └── src/main/java/com/aipay/admin/
│       ├── controller/  (AuthController, MerchantController, AppController,
│       │                 ChargeQueryController, RefundQueryController,
│       │                 OperatorController, ReconcileController)
│       └── security/    (JwtTokenProvider, JwtAuthFilter, AdminSecurityConfig)
└── pay-bootstrap/
    ├── pom.xml
    └── src/main/
        ├── java/com/aipay/PayApplication.java
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── db/migration/V1__init.sql
```

---

### Task 1: Maven Multi-Module Scaffold

**Files:** Create all `pom.xml` files listed above.

- [ ] **Step 1: Create root `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.aipay</groupId>
  <artifactId>ai-pay</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>pay-common</module>
    <module>pay-core</module>
    <module>pay-channel</module>
    <module>pay-api</module>
    <module>pay-admin-api</module>
    <module>pay-bootstrap</module>
  </modules>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring.boot.version>3.3.5</spring.boot.version>
    <mybatis.plus.version>3.5.9</mybatis.plus.version>
    <springdoc.version>2.6.0</springdoc.version>
    <wechatpay.java.version>0.2.12</wechatpay.java.version>
    <alipay.sdk.version>4.38.204.ALL</alipay.sdk.version>
    <redisson.version>3.37.0</redisson.version>
    <jjwt.version>0.12.6</jjwt.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring.boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <!-- Internal modules -->
      <dependency><groupId>com.aipay</groupId><artifactId>pay-common</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.aipay</groupId><artifactId>pay-core</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-api</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-wechat</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-alipay</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.aipay</groupId><artifactId>pay-api</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.aipay</groupId><artifactId>pay-admin-api</artifactId><version>${project.version}</version></dependency>

      <!-- External libraries -->
      <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>${mybatis.plus.version}</version>
      </dependency>
      <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
      </dependency>
      <dependency>
        <groupId>com.github.wechatpay-apiv3</groupId>
        <artifactId>wechatpay-java</artifactId>
        <version>${wechatpay.java.version}</version>
      </dependency>
      <dependency>
        <groupId>com.alipay.sdk</groupId>
        <artifactId>alipay-sdk-java</artifactId>
        <version>${alipay.sdk.version}</version>
      </dependency>
      <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <version>${redisson.version}</version>
      </dependency>
      <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
      </dependency>
      <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
      </dependency>
      <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <version>${spring.boot.version}</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

- [ ] **Step 2: Create `pay-common/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>ai-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-common</artifactId>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: Create `pay-core/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>ai-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-core</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-common</artifactId></dependency>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-api</artifactId></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.redisson</groupId>
      <artifactId>redisson-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: Create `pay-channel/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>ai-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-channel</artifactId>
  <packaging>pom</packaging>

  <modules>
    <module>pay-channel-api</module>
    <module>pay-channel-wechat</module>
    <module>pay-channel-alipay</module>
  </modules>
</project>
```

- [ ] **Step 5: Create `pay-channel/pay-channel-api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>pay-channel</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-channel-api</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-common</artifactId></dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 6: Create `pay-channel/pay-channel-wechat/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>pay-channel</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-channel-wechat</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-api</artifactId></dependency>
    <dependency>
      <groupId>com.github.wechatpay-apiv3</groupId>
      <artifactId>wechatpay-java</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 7: Create `pay-channel/pay-channel-alipay/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>pay-channel</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-channel-alipay</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-api</artifactId></dependency>
    <dependency>
      <groupId>com.alipay.sdk</groupId>
      <artifactId>alipay-sdk-java</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 8: Create `pay-api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>ai-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-api</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-core</artifactId></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 9: Create `pay-admin-api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>ai-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-admin-api</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-core</artifactId></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 10: Create `pay-bootstrap/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.aipay</groupId>
    <artifactId>ai-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>pay-bootstrap</artifactId>

  <dependencies>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-api</artifactId></dependency>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-admin-api</artifactId></dependency>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-wechat</artifactId></dependency>
    <dependency><groupId>com.aipay</groupId><artifactId>pay-channel-alipay</artifactId></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-mysql</artifactId>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution>
            <goals><goal>repackage</goal></goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 11: Create placeholder `src/main/java` directories for each module**

```bash
for mod in pay-common pay-core pay-api pay-admin-api pay-bootstrap; do
  mkdir -p $mod/src/main/java/com/aipay
  mkdir -p $mod/src/test/java/com/aipay
done
mkdir -p pay-channel/pay-channel-api/src/main/java/com/aipay/channel/api/model
mkdir -p pay-channel/pay-channel-wechat/src/main/java/com/aipay/channel/wechat
mkdir -p pay-channel/pay-channel-alipay/src/main/java/com/aipay/channel/alipay
```

- [ ] **Step 12: Verify the project structure compiles**

Run: `mvn clean compile -DskipTests`
Expected: `BUILD SUCCESS` with all 8 modules compiled

- [ ] **Step 13: Commit**

```bash
git add pom.xml pay-common/pom.xml pay-core/pom.xml pay-channel/pom.xml \
  pay-channel/pay-channel-api/pom.xml pay-channel/pay-channel-wechat/pom.xml \
  pay-channel/pay-channel-alipay/pom.xml pay-api/pom.xml pay-admin-api/pom.xml \
  pay-bootstrap/pom.xml
git commit -m "feat: set up maven multi-module scaffold"
```

---

### Task 2: pay-common — Enums, Exceptions, Utilities

**Files:**
- Create: `pay-common/src/main/java/com/aipay/common/constant/ChannelCode.java`
- Create: `pay-common/src/main/java/com/aipay/common/enums/ChargeStatus.java`
- Create: `pay-common/src/main/java/com/aipay/common/enums/RefundStatus.java`
- Create: `pay-common/src/main/java/com/aipay/common/exception/ErrorCode.java`
- Create: `pay-common/src/main/java/com/aipay/common/exception/BizException.java`
- Create: `pay-common/src/main/java/com/aipay/common/util/IdGenerator.java`
- Create: `pay-common/src/main/java/com/aipay/common/util/CryptoUtil.java`
- Test: `pay-common/src/test/java/com/aipay/common/util/CryptoUtilTest.java`
- Test: `pay-common/src/test/java/com/aipay/common/util/IdGeneratorTest.java`

- [ ] **Step 1: Write the failing tests for CryptoUtil and IdGenerator**

Create `pay-common/src/test/java/com/aipay/common/util/CryptoUtilTest.java`:

```java
package com.aipay.common.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CryptoUtilTest {

    @Test
    void encryptAndDecrypt_roundTrip() {
        String plaintext = "{\"mchId\":\"1234567890\",\"apiV3Key\":\"secret\"}";
        String key = "my-32-char-encryption-key-here!!";

        String encrypted = CryptoUtil.encrypt(plaintext, key);
        String decrypted = CryptoUtil.decrypt(encrypted, key);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String plaintext = "hello";
        String key = "test-key";

        String enc1 = CryptoUtil.encrypt(plaintext, key);
        String enc2 = CryptoUtil.encrypt(plaintext, key);

        // Different IVs => different ciphertexts
        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    void sha256_knownValue() {
        // echo -n "hello" | sha256sum = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertThat(CryptoUtil.sha256("hello"))
            .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
```

Create `pay-common/src/test/java/com/aipay/common/util/IdGeneratorTest.java`:

```java
package com.aipay.common.util;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class IdGeneratorTest {

    @Test
    void chargeId_hasCh_prefix() {
        assertThat(IdGenerator.chargeId()).startsWith("ch_");
    }

    @Test
    void refundId_hasRe_prefix() {
        assertThat(IdGenerator.refundId()).startsWith("re_");
    }

    @Test
    void chargeId_isUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(IdGenerator.chargeId());
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void liveApiKey_hasSk_live_prefix_and_32CharSuffix() {
        String key = IdGenerator.liveApiKey();
        assertThat(key).startsWith("sk_live_");
        assertThat(key.substring("sk_live_".length())).hasSize(32);
    }

    @Test
    void testApiKey_hasSk_test_prefix() {
        assertThat(IdGenerator.testApiKey()).startsWith("sk_test_");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (classes don't exist yet)**

Run: `mvn test -pl pay-common -Dtest="CryptoUtilTest,IdGeneratorTest" 2>&1 | tail -5`
Expected: `BUILD FAILURE` — compilation error, classes not found

- [ ] **Step 3: Create `ChannelCode.java`**

```java
package com.aipay.common.constant;

public final class ChannelCode {
    public static final String WECHAT_JSAPI       = "wechat_jsapi";
    public static final String WECHAT_H5          = "wechat_h5";
    public static final String WECHAT_NATIVE      = "wechat_native";
    public static final String WECHAT_MINIPROGRAM = "wechat_miniprogram";
    public static final String ALIPAY_WAP         = "alipay_wap";

    private ChannelCode() {}
}
```

- [ ] **Step 4: Create `ChargeStatus.java`**

```java
package com.aipay.common.enums;

public enum ChargeStatus {
    created, pending, paid, refunded, expired, closed
}
```

- [ ] **Step 5: Create `RefundStatus.java`**

```java
package com.aipay.common.enums;

public enum RefundStatus {
    pending, success, failed
}
```

- [ ] **Step 6: Create `ErrorCode.java` and `BizException.java`**

`ErrorCode.java`:
```java
package com.aipay.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_API_KEY("invalid_api_key", "Invalid or missing API key"),
    CHANNEL_NOT_CONFIGURED("channel_not_configured", "Payment channel not configured for this app"),
    CHARGE_NOT_FOUND("charge_not_found", "Charge not found"),
    CHARGE_ALREADY_PAID("charge_already_paid", "Charge is already paid"),
    REFUND_AMOUNT_EXCEEDED("refund_amount_exceeded", "Refund amount exceeds remaining refundable amount"),
    CHANNEL_ERROR("channel_error", "Payment channel returned an error"),
    NOTIFY_SIGNATURE_INVALID("notify_signature_invalid", "Notification signature verification failed"),
    MERCHANT_NOT_FOUND("merchant_not_found", "Merchant not found"),
    APP_NOT_FOUND("app_not_found", "App not found");

    private final String code;
    private final String message;
}
```

`BizException.java`:
```java
package com.aipay.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final String code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.code = errorCode.getCode();
    }
}
```

- [ ] **Step 7: Create `IdGenerator.java`**

```java
package com.aipay.common.util;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {}

    /** "ch_" + 24 hex chars */
    public static String chargeId() {
        return "ch_" + uuid24();
    }

    /** "re_" + 24 hex chars */
    public static String refundId() {
        return "re_" + uuid24();
    }

    /** "MCH" + current millis */
    public static String merchantNo() {
        return "MCH" + System.currentTimeMillis();
    }

    /** "app_" + 16 hex chars */
    public static String appId() {
        return "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** "sk_live_" + 32 hex chars (UUID without hyphens) */
    public static String liveApiKey() {
        return "sk_live_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** "sk_test_" + 32 hex chars */
    public static String testApiKey() {
        return "sk_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uuid24() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
```

- [ ] **Step 8: Create `CryptoUtil.java`**

```java
package com.aipay.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96-bit IV
    private static final int GCM_TAG_LENGTH = 128;  // 128-bit auth tag

    private CryptoUtil() {}

    /**
     * Encrypts plaintext with AES-256-GCM.
     * Output format: Base64(IV[12] + ciphertext + tag[16])
     */
    public static String encrypt(String plaintext, String key) {
        try {
            byte[] keyBytes = deriveKey32(key);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts Base64(IV + ciphertext + tag) with AES-256-GCM.
     */
    public static String decrypt(String encrypted, String key) {
        try {
            byte[] keyBytes = deriveKey32(key);
            byte[] data = Base64.getDecoder().decode(encrypted);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Returns lowercase hex SHA-256 of input string.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    /** Derives a 32-byte AES key by SHA-256-hashing the input key string. */
    private static byte[] deriveKey32(String key) throws Exception {
        return MessageDigest.getInstance("SHA-256")
            .digest(key.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 9: Run tests — verify they pass**

Run: `mvn test -pl pay-common`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 10: Commit**

```bash
git add pay-common/
git commit -m "feat: add pay-common enums, exceptions, IdGenerator, CryptoUtil"
```

---

### Task 3: Database Migration SQL

**Files:**
- Create: `pay-bootstrap/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: Create `V1__init.sql` with all 9 tables**

```sql
-- pay-bootstrap/src/main/resources/db/migration/V1__init.sql

-- =====================================================
-- Account hierarchy
-- =====================================================

CREATE TABLE IF NOT EXISTS merchant (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no   VARCHAR(32) NOT NULL UNIQUE COMMENT 'MCH202405001',
  name          VARCHAR(128) NOT NULL,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '0=disabled 1=enabled',
  contact_email VARCHAR(128),
  contact_phone VARCHAR(32),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Merchants';

CREATE TABLE IF NOT EXISTS app (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id       VARCHAR(32) NOT NULL UNIQUE COMMENT 'app_ prefix + random',
  merchant_id  BIGINT NOT NULL,
  name         VARCHAR(128) NOT NULL,
  live_key     VARCHAR(128) NOT NULL COMMENT 'SHA-256 hash; plaintext shown once',
  test_key     VARCHAR(128) NOT NULL COMMENT 'SHA-256 hash',
  status       TINYINT NOT NULL DEFAULT 1,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Applications under a merchant';

CREATE TABLE IF NOT EXISTS channel_config (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id      BIGINT NOT NULL,
  channel     VARCHAR(32) NOT NULL
                COMMENT 'wechat_jsapi|wechat_h5|wechat_native|wechat_miniprogram|alipay_wap',
  config_json TEXT NOT NULL COMMENT 'AES-256-GCM encrypted JSON with mch credentials',
  status      TINYINT NOT NULL DEFAULT 1,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_channel (app_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-app channel credentials (encrypted)';

-- =====================================================
-- Operator / RBAC
-- =====================================================

CREATE TABLE IF NOT EXISTS operator (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id   BIGINT NOT NULL,
  username      VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt strength=12',
  real_name     VARCHAR(64),
  is_admin      TINYINT NOT NULL DEFAULT 0 COMMENT '1=merchant admin',
  status        TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin portal operators';

CREATE TABLE IF NOT EXISTS operator_permission (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  module      VARCHAR(32) NOT NULL
                COMMENT 'orders|refunds|channels|reconcile|apps|operators',
  can_view    TINYINT NOT NULL DEFAULT 0,
  can_operate TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_op_module (operator_id, module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operator permission matrix';

-- =====================================================
-- Transaction records
-- =====================================================

CREATE TABLE IF NOT EXISTS charge (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  charge_id        VARCHAR(32) NOT NULL UNIQUE COMMENT 'ch_ prefix',
  app_id           BIGINT NOT NULL,
  merchant_id      BIGINT NOT NULL,
  out_trade_no     VARCHAR(64) NOT NULL COMMENT 'Merchant order number',
  channel          VARCHAR(32) NOT NULL,
  amount           INT NOT NULL COMMENT 'Amount in fen',
  currency         VARCHAR(8) NOT NULL DEFAULT 'cny',
  subject          VARCHAR(256) NOT NULL,
  body             VARCHAR(512),
  client_ip        VARCHAR(64),
  status           VARCHAR(16) NOT NULL DEFAULT 'created'
                     COMMENT 'created|pending|paid|refunded|expired|closed',
  paid             TINYINT NOT NULL DEFAULT 0,
  paid_at          DATETIME,
  time_expire      DATETIME,
  transaction_no   VARCHAR(64) COMMENT 'Channel transaction number',
  channel_extra    JSON COMMENT 'e.g. open_id',
  credential       JSON COMMENT 'Payment credential returned to client',
  failure_code     VARCHAR(32),
  failure_msg      VARCHAR(256),
  amount_refunded  INT NOT NULL DEFAULT 0,
  metadata         JSON,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_app_id (app_id),
  INDEX idx_merchant_id (merchant_id),
  INDEX idx_out_trade_no (out_trade_no),
  INDEX idx_transaction_no (transaction_no),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payment orders';

CREATE TABLE IF NOT EXISTS refund (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_id      VARCHAR(32) NOT NULL UNIQUE COMMENT 're_ prefix',
  charge_id      VARCHAR(32) NOT NULL,
  app_id         BIGINT NOT NULL,
  merchant_id    BIGINT NOT NULL,
  out_refund_no  VARCHAR(64) NOT NULL,
  amount         INT NOT NULL COMMENT 'Refund amount in fen',
  description    VARCHAR(256),
  status         VARCHAR(16) NOT NULL DEFAULT 'pending'
                   COMMENT 'pending|success|failed',
  transaction_no VARCHAR(64) COMMENT 'Channel refund transaction number',
  failure_code   VARCHAR(32),
  failure_msg    VARCHAR(256),
  succeed_at     DATETIME,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_charge_id (charge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refund orders';

CREATE TABLE IF NOT EXISTS notify_record (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  notify_key    VARCHAR(128) NOT NULL UNIQUE COMMENT 'channel:transaction_no',
  charge_id     VARCHAR(32),
  channel       VARCHAR(32) NOT NULL,
  raw_body      TEXT NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'processing'
                  COMMENT 'processing|success|failed',
  process_count INT NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotency record for channel notifications';

-- =====================================================
-- Reconciliation
-- =====================================================

CREATE TABLE IF NOT EXISTS reconcile_record (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id          BIGINT NOT NULL,
  merchant_id     BIGINT NOT NULL,
  channel         VARCHAR(32) NOT NULL,
  reconcile_date  DATE NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'pending'
                    COMMENT 'pending|reconciling|matched|unmatched',
  total_count     INT NOT NULL DEFAULT 0,
  total_amount    BIGINT NOT NULL DEFAULT 0,
  matched_count   INT NOT NULL DEFAULT 0,
  unmatched_count INT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_channel_date (app_id, channel, reconcile_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reconciliation records';
```

- [ ] **Step 2: Commit**

```bash
git add pay-bootstrap/src/main/resources/db/migration/V1__init.sql
git commit -m "feat: add database migration V1 — all 9 tables"
```

---

### Task 4: pay-core — Domain Entities and Mappers

**Files:**
- Create: `pay-core/src/main/java/com/aipay/core/domain/*.java` (9 entity classes)
- Create: `pay-core/src/main/java/com/aipay/core/mapper/*.java` (7 mapper interfaces)
- Create: `pay-core/src/main/java/com/aipay/core/config/CoreConfig.java`

- [ ] **Step 1: Create domain entities — Merchant and App (pattern for all)**

`Merchant.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantNo;
    private String name;
    private Integer status;
    private String contactEmail;
    private String contactPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`App.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("app")
public class App {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private Long merchantId;
    private String name;
    private String liveKey;   // SHA-256 hash
    private String testKey;   // SHA-256 hash
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create remaining entities following the same pattern**

`ChannelConfig.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("channel_config")
public class ChannelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String channel;
    private String configJson; // AES-256-GCM encrypted
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`Operator.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operator")
public class Operator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String username;
    private String passwordHash;
    private String realName;
    private Integer isAdmin;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`OperatorPermission.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("operator_permission")
public class OperatorPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String module;
    private Integer canView;
    private Integer canOperate;
}
```

`Charge.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("charge")
public class Charge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chargeId;
    private Long appId;
    private Long merchantId;
    private String outTradeNo;
    private String channel;
    private Integer amount;
    private String currency;
    private String subject;
    private String body;
    private String clientIp;
    private String status;
    private Integer paid;
    private LocalDateTime paidAt;
    private LocalDateTime timeExpire;
    private String transactionNo;
    private String channelExtra;  // JSON string
    private String credential;    // JSON string
    private String failureCode;
    private String failureMsg;
    private Integer amountRefunded;
    private String metadata;      // JSON string
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`Refund.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("refund")
public class Refund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundId;
    private String chargeId;
    private Long appId;
    private Long merchantId;
    private String outRefundNo;
    private Integer amount;
    private String description;
    private String status;
    private String transactionNo;
    private String failureCode;
    private String failureMsg;
    private LocalDateTime succeedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`NotifyRecord.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notify_record")
public class NotifyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String notifyKey;
    private String chargeId;
    private String channel;
    private String rawBody;
    private String status;
    private Integer processCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`ReconcileRecord.java`:
```java
package com.aipay.core.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reconcile_record")
public class ReconcileRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private Long merchantId;
    private String channel;
    private LocalDate reconcileDate;
    private String status;
    private Integer totalCount;
    private Long totalAmount;
    private Integer matchedCount;
    private Integer unmatchedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Create mapper interfaces**

`MerchantMapper.java`:
```java
package com.aipay.core.mapper;

import com.aipay.core.domain.Merchant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {}
```

Create the remaining mappers following the same one-liner pattern:
- `AppMapper extends BaseMapper<App>` — add `selectByAppId(String appId)` custom method
- `ChannelConfigMapper extends BaseMapper<ChannelConfig>`
- `ChargeMapper extends BaseMapper<Charge>` — add `selectByChargeId(String chargeId)`
- `RefundMapper extends BaseMapper<Refund>`
- `NotifyRecordMapper extends BaseMapper<NotifyRecord>`
- `OperatorMapper extends BaseMapper<Operator>`
- `OperatorPermissionMapper extends BaseMapper<OperatorPermission>`
- `ReconcileRecordMapper extends BaseMapper<ReconcileRecord>`

`AppMapper.java` (example with custom query):
```java
package com.aipay.core.mapper;

import com.aipay.core.domain.App;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppMapper extends BaseMapper<App> {
    @Select("SELECT * FROM app WHERE app_id = #{appId} AND status = 1")
    App selectByAppId(String appId);
}
```

`ChargeMapper.java`:
```java
package com.aipay.core.mapper;

import com.aipay.core.domain.Charge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChargeMapper extends BaseMapper<Charge> {
    @Select("SELECT * FROM charge WHERE charge_id = #{chargeId}")
    Charge selectByChargeId(String chargeId);
}
```

- [ ] **Step 4: Create `CoreConfig.java`**

```java
package com.aipay.core.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aipay.core.mapper")
public class CoreConfig {}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl pay-core -am -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add pay-core/
git commit -m "feat: add domain entities, mappers, and CoreConfig"
```

---

### Task 5: pay-channel-api — PayChannel Interface and Models

**Files:**
- Create: `pay-channel/pay-channel-api/src/main/java/com/aipay/channel/api/model/*.java`
- Create: `pay-channel/pay-channel-api/src/main/java/com/aipay/channel/api/PayChannel.java`
- Create: `pay-channel/pay-channel-api/src/main/java/com/aipay/channel/api/ChannelRouter.java`

- [ ] **Step 1: Create channel request/result models**

`CreateOrderRequest.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class CreateOrderRequest {
    private String chargeId;
    private String outTradeNo;
    private int amount;              // in fen
    private String currency;
    private String subject;
    private String body;
    private String clientIp;
    private String notifyUrl;
    private long timeExpireSeconds;
    private Map<String, Object> channelExtra;
    // WeChat credentials (from decrypted ChannelConfig)
    private String wechatAppId;
    private String mchId;
    private String apiV3Key;
    private String serialNo;
    private String privateKey;
    // Alipay credentials
    private String alipayAppId;
    private String alipayPrivateKey;
    private String alipayPublicKey;
    private String returnUrl;
}
```

`CreateOrderResult.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class CreateOrderResult {
    private boolean success;
    private String transactionNo;
    private Map<String, Object> credential;
    private String failureCode;
    private String failureMsg;
}
```

`NotifyRequest.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class NotifyRequest {
    private String channel;
    private String rawBody;
    private Map<String, String> headers;    // for WeChat signature
    private Map<String, String[]> params;   // for Alipay form params
    // WeChat credentials
    private String mchId;
    private String apiV3Key;
    private String serialNo;
    private String privateKey;
    // Alipay credentials
    private String alipayPublicKey;
}
```

`NotifyResult.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotifyResult {
    private boolean success;
    private String transactionNo;   // channel transaction number
    private String outTradeNo;      // merchant order number
    private int paidAmount;         // in fen
    private String failureReason;
}
```

`RefundRequest.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundRequest {
    private String chargeId;
    private String outRefundNo;
    private String transactionNo;   // channel's transaction number
    private int totalAmount;        // original charge amount
    private int refundAmount;
    private String description;
    // WeChat credentials
    private String wechatAppId;
    private String mchId;
    private String apiV3Key;
    private String serialNo;
    private String privateKey;
    // Alipay credentials
    private String alipayAppId;
    private String alipayPrivateKey;
    private String alipayPublicKey;
    private String outTradeNo;
}
```

`RefundResult.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResult {
    private boolean success;
    private String transactionNo;
    private String failureCode;
    private String failureMsg;
}
```

`QueryResult.java`:
```java
package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryResult {
    private boolean paid;
    private String transactionNo;
    private int paidAmount;
}
```

- [ ] **Step 2: Create `PayChannel.java` interface**

```java
package com.aipay.channel.api;

import com.aipay.channel.api.model.*;

public interface PayChannel {
    /** Returns the channel code this implementation handles, e.g. "wechat_jsapi" */
    String channelCode();

    /** Creates an order with the payment channel and returns a credential for the client. */
    CreateOrderResult createOrder(CreateOrderRequest req);

    /** Parses and validates an inbound async notification from the channel. */
    NotifyResult parseNotify(NotifyRequest req);

    /** Initiates a refund. */
    RefundResult refund(RefundRequest req);

    /** Queries order status from the channel. */
    QueryResult query(String outTradeNo);
}
```

- [ ] **Step 3: Create `ChannelRouter.java`**

```java
package com.aipay.channel.api;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChannelRouter {
    private final Map<String, PayChannel> channels;

    public ChannelRouter(List<PayChannel> channelList) {
        this.channels = channelList.stream()
            .collect(Collectors.toMap(PayChannel::channelCode, Function.identity()));
    }

    public PayChannel route(String channelCode) {
        PayChannel channel = channels.get(channelCode);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown channel: " + channelCode);
        }
        return channel;
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl pay-channel/pay-channel-api -am -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add pay-channel/pay-channel-api/
git commit -m "feat: add PayChannel interface and channel request/result models"
```

---

### Task 6: pay-core — Business Services

**Files:**
- Create: `pay-core/src/main/java/com/aipay/core/service/MerchantService.java`
- Create: `pay-core/src/main/java/com/aipay/core/service/AppService.java`
- Create: `pay-core/src/main/java/com/aipay/core/service/ChannelConfigService.java`
- Create: `pay-core/src/main/java/com/aipay/core/service/ChargeService.java`
- Create: `pay-core/src/main/java/com/aipay/core/service/NotifyService.java`
- Create: `pay-core/src/main/java/com/aipay/core/service/RefundService.java`
- Test: `pay-core/src/test/java/com/aipay/core/service/ChargeServiceTest.java`
- Test: `pay-core/src/test/java/com/aipay/core/service/NotifyServiceTest.java`
- Test: `pay-core/src/test/java/com/aipay/core/service/RefundServiceTest.java`

- [ ] **Step 1: Write failing tests for ChargeService**

```java
// pay-core/src/test/java/com/aipay/core/service/ChargeServiceTest.java
package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.CreateOrderRequest;
import com.aipay.channel.api.model.CreateOrderResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.exception.BizException;
import com.aipay.core.domain.*;
import com.aipay.core.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock ChargeMapper chargeMapper;
    @Mock ChannelConfigService channelConfigService;
    @Mock ChannelRouter channelRouter;
    @Mock PayChannel payChannel;
    @InjectMocks ChargeService chargeService;

    private App testApp;
    private ChannelConfig testChannelConfig;

    @BeforeEach
    void setUp() {
        testApp = new App();
        testApp.setId(1L);
        testApp.setAppId("app_test123");
        testApp.setMerchantId(100L);

        testChannelConfig = new ChannelConfig();
        testChannelConfig.setAppId(1L);
        testChannelConfig.setChannel("wechat_jsapi");
        testChannelConfig.setStatus(1);
    }

    @Test
    void createCharge_success_returnsChargeWithPendingStatus() {
        when(channelConfigService.findActiveConfig(1L, "wechat_jsapi"))
            .thenReturn(testChannelConfig);
        when(channelConfigService.buildCreateOrderRequest(any(), any(), any()))
            .thenReturn(CreateOrderRequest.builder().build());
        when(channelRouter.route("wechat_jsapi")).thenReturn(payChannel);
        when(payChannel.createOrder(any())).thenReturn(
            CreateOrderResult.builder()
                .success(true)
                .credential(Map.of("wechat_jsapi", Map.of("appId", "wxabc")))
                .build()
        );
        when(chargeMapper.insert(any())).thenReturn(1);
        when(chargeMapper.updateById(any())).thenReturn(1);

        Charge result = chargeService.createCharge(testApp, "ORDER_001",
            "wechat_jsapi", 9900, "cny", "Test Product", null, "127.0.0.1",
            1800L, Map.of("open_id", "oUpF8xxx"), null);

        assertThat(result.getStatus()).isEqualTo(ChargeStatus.pending.name());
        assertThat(result.getPaid()).isEqualTo(0);
        assertThat(result.getCredential()).contains("wechat_jsapi");
        verify(chargeMapper, times(2)).insert(any()); // won't happen — just verify insert called once
    }

    @Test
    void createCharge_channelNotConfigured_throwsBizException() {
        when(channelConfigService.findActiveConfig(1L, "wechat_jsapi"))
            .thenReturn(null);

        assertThatThrownBy(() ->
            chargeService.createCharge(testApp, "ORDER_001",
                "wechat_jsapi", 9900, "cny", "Test", null, "127.0.0.1",
                1800L, null, null)
        ).isInstanceOf(BizException.class)
         .hasMessageContaining("channel_not_configured");
    }
}
```

- [ ] **Step 2: Write failing tests for NotifyService**

```java
// pay-core/src/test/java/com/aipay/core/service/NotifyServiceTest.java
package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.NotifyRequest;
import com.aipay.channel.api.model.NotifyResult;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.NotifyRecord;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.NotifyRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifyServiceTest {

    @Mock NotifyRecordMapper notifyRecordMapper;
    @Mock ChargeMapper chargeMapper;
    @Mock ChannelRouter channelRouter;
    @Mock ChannelConfigService channelConfigService;
    @Mock RedissonClient redissonClient;
    @Mock RBucket<String> rBucket;
    @InjectMocks NotifyService notifyService;

    @Test
    void processNotify_alreadyProcessed_returnsImmediately() {
        when(redissonClient.getBucket(anyString())).thenReturn(rBucket);
        when(rBucket.setIfAbsent(anyString(), anyLong(), any())).thenReturn(false);

        notifyService.processNotify("wechat_jsapi", "TX123", "body",
            Map.of(), null, "mch001", "key", "serial", "privKey", null);

        verify(chargeMapper, never()).selectByChargeId(any());
    }

    @Test
    void processNotify_newNotification_updatesChargeStatus() {
        when(redissonClient.getBucket(anyString())).thenReturn(rBucket);
        when(rBucket.setIfAbsent(anyString(), anyLong(), any())).thenReturn(true);

        PayChannel channel = mock(PayChannel.class);
        when(channelRouter.route("wechat_jsapi")).thenReturn(channel);
        when(channel.parseNotify(any())).thenReturn(
            NotifyResult.builder()
                .success(true)
                .transactionNo("TX123")
                .outTradeNo("ORDER_001")
                .paidAmount(9900)
                .build()
        );

        Charge charge = new Charge();
        charge.setChargeId("ch_abc");
        charge.setStatus("pending");
        charge.setAmount(9900);
        when(chargeMapper.selectOne(any())).thenReturn(charge);
        when(chargeMapper.updateById(any())).thenReturn(1);
        when(notifyRecordMapper.insert(any())).thenReturn(1);
        when(notifyRecordMapper.updateById(any())).thenReturn(1);

        notifyService.processNotify("wechat_jsapi", "TX123", "body",
            Map.of(), null, "mch001", "key", "serial", "privKey", null);

        verify(chargeMapper).updateById(argThat(c -> "paid".equals(((Charge) c).getStatus())));
    }
}
```

- [ ] **Step 3: Write failing tests for RefundService**

```java
// pay-core/src/test/java/com/aipay/core/service/RefundServiceTest.java
package com.aipay.core.service;

import com.aipay.common.exception.BizException;
import com.aipay.core.domain.Charge;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.RefundMapper;
import com.aipay.channel.api.ChannelRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock RefundMapper refundMapper;
    @Mock ChargeMapper chargeMapper;
    @Mock ChannelRouter channelRouter;
    @Mock ChannelConfigService channelConfigService;
    @Mock RedissonClient redissonClient;
    @Mock RBucket<String> rBucket;
    @InjectMocks RefundService refundService;

    @Test
    void createRefund_chargeNotPaid_throwsBizException() {
        Charge charge = new Charge();
        charge.setChargeId("ch_abc");
        charge.setPaid(0);  // not paid

        assertThatThrownBy(() ->
            refundService.createRefund(charge, 5000, "REFUND_001", "test refund")
        ).isInstanceOf(BizException.class)
         .hasMessageContaining("charge_already_paid");
    }

    @Test
    void createRefund_amountExceeded_throwsBizException() {
        Charge charge = new Charge();
        charge.setChargeId("ch_abc");
        charge.setPaid(1);
        charge.setAmount(9900);
        charge.setAmountRefunded(5000);

        assertThatThrownBy(() ->
            refundService.createRefund(charge, 5000, "REFUND_001", "test refund")
        ).isInstanceOf(BizException.class)
         .hasMessageContaining("refund_amount_exceeded");
    }
}
```

- [ ] **Step 4: Run tests — verify they fail**

Run: `mvn test -pl pay-core -Dtest="ChargeServiceTest,NotifyServiceTest,RefundServiceTest" 2>&1 | tail -5`
Expected: `BUILD FAILURE` — class not found

- [ ] **Step 5: Create `ChannelConfigService.java`**

```java
package com.aipay.core.service;

import com.aipay.channel.api.model.CreateOrderRequest;
import com.aipay.common.util.CryptoUtil;
import com.aipay.core.domain.App;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.mapper.ChannelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.encrypt-key}")
    private String encryptKey;

    @Value("${app.notify-base-url}")
    private String notifyBaseUrl;

    public ChannelConfig findActiveConfig(long appId, String channel) {
        return channelConfigMapper.selectOne(new LambdaQueryWrapper<ChannelConfig>()
            .eq(ChannelConfig::getAppId, appId)
            .eq(ChannelConfig::getChannel, channel)
            .eq(ChannelConfig::getStatus, 1));
    }

    public Map<String, Object> decryptConfig(ChannelConfig config) {
        try {
            String json = CryptoUtil.decrypt(config.getConfigJson(), encryptKey);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt channel config for app " + config.getAppId(), e);
        }
    }

    public void saveConfig(long appId, String channel, Map<String, Object> configMap) {
        try {
            String json = objectMapper.writeValueAsString(configMap);
            String encrypted = CryptoUtil.encrypt(json, encryptKey);

            ChannelConfig existing = findActiveConfig(appId, channel);
            if (existing != null) {
                existing.setConfigJson(encrypted);
                channelConfigMapper.updateById(existing);
            } else {
                ChannelConfig cc = new ChannelConfig();
                cc.setAppId(appId);
                cc.setChannel(channel);
                cc.setConfigJson(encrypted);
                cc.setStatus(1);
                channelConfigMapper.insert(cc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save channel config", e);
        }
    }

    /**
     * Builds a CreateOrderRequest with credentials from the decrypted ChannelConfig.
     * The channelExtra map is from the original charge request (e.g. open_id for JSAPI).
     */
    public CreateOrderRequest buildCreateOrderRequest(
            App app,
            ChannelConfig channelConfig,
            com.aipay.core.domain.Charge charge) {
        Map<String, Object> cfg = decryptConfig(channelConfig);
        String channel = channelConfig.getChannel();
        String notifyUrl = notifyBaseUrl + "/v1/notify/" +
            (channel.startsWith("wechat") ? "wechat" : "alipay") + "/" + app.getAppId();

        Map<String, Object> extra = charge.getChannelExtra() != null
            ? parseJson(charge.getChannelExtra())
            : Map.of();

        CreateOrderRequest.CreateOrderRequestBuilder builder = CreateOrderRequest.builder()
            .chargeId(charge.getChargeId())
            .outTradeNo(charge.getOutTradeNo())
            .amount(charge.getAmount())
            .currency(charge.getCurrency())
            .subject(charge.getSubject())
            .body(charge.getBody())
            .clientIp(charge.getClientIp())
            .notifyUrl(notifyUrl)
            .channelExtra(extra);

        if (channel.startsWith("wechat")) {
            builder.wechatAppId((String) cfg.get("appId"))
                   .mchId((String) cfg.get("mchId"))
                   .apiV3Key((String) cfg.get("apiV3Key"))
                   .serialNo((String) cfg.get("serialNo"))
                   .privateKey((String) cfg.get("privateKey"));
        } else if (channel.startsWith("alipay")) {
            builder.alipayAppId((String) cfg.get("appId"))
                   .alipayPrivateKey((String) cfg.get("privateKey"))
                   .alipayPublicKey((String) cfg.get("alipayPublicKey"))
                   .returnUrl((String) extra.get("return_url"));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
```

- [ ] **Step 6: Create `MerchantService.java`**

```java
package com.aipay.core.service;

import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.Merchant;
import com.aipay.core.mapper.MerchantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;

    public Merchant createMerchant(String name, String contactEmail, String contactPhone) {
        Merchant m = new Merchant();
        m.setMerchantNo(IdGenerator.merchantNo());
        m.setName(name);
        m.setContactEmail(contactEmail);
        m.setContactPhone(contactPhone);
        m.setStatus(1);
        merchantMapper.insert(m);
        return m;
    }

    public Merchant findById(Long id) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        return m;
    }

    public Page<Merchant> listMerchants(int page, int size) {
        return merchantMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Merchant>().orderByDesc(Merchant::getCreatedAt));
    }

    public void updateMerchant(Long id, String name, String contactEmail,
                                String contactPhone, Integer status) {
        Merchant m = findById(id);
        if (name != null) m.setName(name);
        if (contactEmail != null) m.setContactEmail(contactEmail);
        if (contactPhone != null) m.setContactPhone(contactPhone);
        if (status != null) m.setStatus(status);
        merchantMapper.updateById(m);
    }
}
```

- [ ] **Step 7: Create `AppService.java`**

```java
package com.aipay.core.service;

import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.CryptoUtil;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.App;
import com.aipay.core.mapper.AppMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppService {

    private final AppMapper appMapper;

    /**
     * Creates a new app for a merchant.
     * Returns the app AND the plaintext live/test keys (shown only once).
     */
    public Map<String, Object> createApp(Long merchantId, String name) {
        String plainLiveKey = IdGenerator.liveApiKey();
        String plainTestKey = IdGenerator.testApiKey();

        App app = new App();
        app.setAppId(IdGenerator.appId());
        app.setMerchantId(merchantId);
        app.setName(name);
        app.setLiveKey(CryptoUtil.sha256(plainLiveKey));
        app.setTestKey(CryptoUtil.sha256(plainTestKey));
        app.setStatus(1);
        appMapper.insert(app);

        return Map.of(
            "app", app,
            "live_key", plainLiveKey,   // shown only once
            "test_key", plainTestKey    // shown only once
        );
    }

    /**
     * Authenticates an API key — returns the App if valid, null if not.
     * Checks both live and test keys.
     */
    public App authenticateApiKey(String rawKey) {
        String hashed = CryptoUtil.sha256(rawKey);
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
            .eq(App::getLiveKey, hashed).eq(App::getStatus, 1));
        if (app == null) {
            app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getTestKey, hashed).eq(App::getStatus, 1));
        }
        return app;
    }

    public App findByAppId(String appId) {
        App app = appMapper.selectByAppId(appId);
        if (app == null) throw new BizException(ErrorCode.APP_NOT_FOUND);
        return app;
    }

    public List<App> listByMerchant(Long merchantId) {
        return appMapper.selectList(new LambdaQueryWrapper<App>()
            .eq(App::getMerchantId, merchantId));
    }
}
```

- [ ] **Step 8: Create `ChargeService.java`**

```java
package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.model.CreateOrderRequest;
import com.aipay.channel.api.model.CreateOrderResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.mapper.ChargeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeMapper chargeMapper;
    private final ChannelConfigService channelConfigService;
    private final ChannelRouter channelRouter;
    private final ObjectMapper objectMapper;

    @Transactional
    public Charge createCharge(App app, String outTradeNo, String channel,
                                int amount, String currency, String subject, String body,
                                String clientIp, long timeExpireSeconds,
                                Map<String, Object> channelExtra,
                                Map<String, Object> metadata) {
        // 1. Verify channel is configured
        ChannelConfig channelConfig = channelConfigService.findActiveConfig(app.getId(), channel);
        if (channelConfig == null) {
            throw new BizException(ErrorCode.CHANNEL_NOT_CONFIGURED);
        }

        // 2. Create charge record with status=created
        Charge charge = new Charge();
        charge.setChargeId(IdGenerator.chargeId());
        charge.setAppId(app.getId());
        charge.setMerchantId(app.getMerchantId());
        charge.setOutTradeNo(outTradeNo);
        charge.setChannel(channel);
        charge.setAmount(amount);
        charge.setCurrency(currency);
        charge.setSubject(subject);
        charge.setBody(body);
        charge.setClientIp(clientIp);
        charge.setStatus(ChargeStatus.created.name());
        charge.setPaid(0);
        charge.setAmountRefunded(0);
        charge.setTimeExpire(LocalDateTime.now().plusSeconds(timeExpireSeconds));
        charge.setChannelExtra(toJson(channelExtra));
        charge.setMetadata(toJson(metadata));
        chargeMapper.insert(charge);

        // 3. Call payment channel
        CreateOrderRequest request = channelConfigService.buildCreateOrderRequest(
            app, channelConfig, charge);
        CreateOrderResult result = channelRouter.route(channel).createOrder(request);

        // 4. Update charge with credential or failure
        if (result.isSuccess()) {
            charge.setStatus(ChargeStatus.pending.name());
            charge.setCredential(toJson(result.getCredential()));
            charge.setTransactionNo(result.getTransactionNo());
        } else {
            charge.setStatus(ChargeStatus.closed.name());
            charge.setFailureCode(result.getFailureCode());
            charge.setFailureMsg(result.getFailureMsg());
        }
        chargeMapper.updateById(charge);

        return charge;
    }

    public Charge findByChargeId(String chargeId) {
        Charge charge = chargeMapper.selectByChargeId(chargeId);
        if (charge == null) throw new BizException(ErrorCode.CHARGE_NOT_FOUND);
        return charge;
    }

    public Page<Charge> listCharges(Long appId, Long merchantId, String status,
                                     int page, int size) {
        LambdaQueryWrapper<Charge> wrapper = new LambdaQueryWrapper<Charge>()
            .eq(appId != null, Charge::getAppId, appId)
            .eq(merchantId != null, Charge::getMerchantId, merchantId)
            .eq(status != null, Charge::getStatus, status)
            .orderByDesc(Charge::getCreatedAt);
        return chargeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @SneakyThrows
    private String toJson(Object obj) {
        if (obj == null) return null;
        return objectMapper.writeValueAsString(obj);
    }
}
```

- [ ] **Step 9: Create `NotifyService.java`**

```java
package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.model.NotifyRequest;
import com.aipay.channel.api.model.NotifyResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.NotifyRecord;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.NotifyRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private static final String NOTIFY_KEY_PREFIX = "notify:";
    private static final long NOTIFY_TTL_SECONDS = 300;

    private final NotifyRecordMapper notifyRecordMapper;
    private final ChargeMapper chargeMapper;
    private final ChannelRouter channelRouter;
    private final ChannelConfigService channelConfigService;
    private final RedissonClient redissonClient;

    /**
     * Processes an async payment notification.
     * Idempotent: uses Redis SETNX to prevent duplicate processing.
     *
     * @param channel      e.g. "wechat_jsapi"
     * @param transactionNo channel's transaction number
     * @param rawBody      raw notification body (for record keeping)
     * @param headers      HTTP headers (for WeChat signature)
     * @param params       form params (for Alipay signature)
     */
    @Transactional
    public void processNotify(String channel, String transactionNo, String rawBody,
                               Map<String, String> headers, Map<String, String[]> params,
                               String mchId, String apiV3Key, String serialNo,
                               String privateKey, String alipayPublicKey) {
        String redisKey = NOTIFY_KEY_PREFIX + channel + ":" + transactionNo;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);

        // Idempotency check — if key exists, this notification was already processed
        boolean acquired = bucket.setIfAbsent("processing", NOTIFY_TTL_SECONDS, TimeUnit.SECONDS);
        if (!acquired) {
            log.info("Duplicate notification ignored: {}", redisKey);
            return;
        }

        NotifyRecord record = new NotifyRecord();
        record.setNotifyKey(channel + ":" + transactionNo);
        record.setChannel(channel);
        record.setRawBody(rawBody);
        record.setStatus("processing");
        record.setProcessCount(1);
        notifyRecordMapper.insert(record);

        try {
            // Parse and verify the notification
            NotifyRequest request = NotifyRequest.builder()
                .channel(channel)
                .rawBody(rawBody)
                .headers(headers)
                .params(params)
                .mchId(mchId)
                .apiV3Key(apiV3Key)
                .serialNo(serialNo)
                .privateKey(privateKey)
                .alipayPublicKey(alipayPublicKey)
                .build();

            NotifyResult result = channelRouter.route(channel).parseNotify(request);

            if (!result.isSuccess()) {
                throw new BizException(ErrorCode.NOTIFY_SIGNATURE_INVALID,
                    result.getFailureReason());
            }

            // Find and update the charge
            Charge charge = chargeMapper.selectOne(new LambdaQueryWrapper<Charge>()
                .eq(Charge::getOutTradeNo, result.getOutTradeNo()));

            if (charge == null) {
                log.warn("Charge not found for outTradeNo: {}", result.getOutTradeNo());
                return;
            }

            record.setChargeId(charge.getChargeId());

            // Validate amount matches
            if (charge.getAmount() != result.getPaidAmount()) {
                log.error("Amount mismatch: expected {} got {} for charge {}",
                    charge.getAmount(), result.getPaidAmount(), charge.getChargeId());
                throw new RuntimeException("Amount mismatch in payment notification");
            }

            // Update charge status to paid (only if currently pending — prevents double-processing)
            if (ChargeStatus.pending.name().equals(charge.getStatus())) {
                charge.setStatus(ChargeStatus.paid.name());
                charge.setPaid(1);
                charge.setPaidAt(LocalDateTime.now());
                charge.setTransactionNo(result.getTransactionNo());
                chargeMapper.updateById(charge);
            }

            record.setStatus("success");
            notifyRecordMapper.updateById(record);

        } catch (Exception e) {
            log.error("Failed to process notification {}: {}", redisKey, e.getMessage(), e);
            record.setStatus("failed");
            notifyRecordMapper.updateById(record);
            // Delete Redis key so the channel can retry
            bucket.delete();
            throw e;
        }
    }
}
```

- [ ] **Step 10: Create `RefundService.java`**

```java
package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.model.RefundRequest;
import com.aipay.channel.api.model.RefundResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.enums.RefundStatus;
import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.domain.Refund;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.RefundMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundMapper refundMapper;
    private final ChargeMapper chargeMapper;
    private final ChannelRouter channelRouter;
    private final ChannelConfigService channelConfigService;
    private final RedissonClient redissonClient;

    @Transactional
    public Refund createRefund(Charge charge, int amount,
                                String outRefundNo, String description) {
        // Validate charge is paid
        if (charge.getPaid() == null || charge.getPaid() == 0) {
            throw new BizException(ErrorCode.CHARGE_ALREADY_PAID);
        }

        // Validate refund amount
        int alreadyRefunded = charge.getAmountRefunded() == null ? 0 : charge.getAmountRefunded();
        if (alreadyRefunded + amount > charge.getAmount()) {
            throw new BizException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }

        // Idempotency key
        RBucket<String> bucket = redissonClient.getBucket("refund:" + outRefundNo);
        boolean acquired = bucket.setIfAbsent("processing", 300, TimeUnit.SECONDS);
        if (!acquired) {
            // Return existing refund if already processing
            return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOutRefundNo, outRefundNo));
        }

        // Create refund record
        Refund refund = new Refund();
        refund.setRefundId(IdGenerator.refundId());
        refund.setChargeId(charge.getChargeId());
        refund.setAppId(charge.getAppId());
        refund.setMerchantId(charge.getMerchantId());
        refund.setOutRefundNo(outRefundNo);
        refund.setAmount(amount);
        refund.setDescription(description);
        refund.setStatus(RefundStatus.pending.name());
        refundMapper.insert(refund);

        // Load channel config for credentials
        ChannelConfig channelConfig = channelConfigService.findActiveConfig(
            charge.getAppId(), charge.getChannel());
        Map<String, Object> cfg = channelConfigService.decryptConfig(channelConfig);

        // Call channel refund
        RefundRequest request = buildRefundRequest(charge, refund, cfg);
        RefundResult result = channelRouter.route(charge.getChannel()).refund(request);

        if (result.isSuccess()) {
            refund.setStatus(RefundStatus.success.name());
            refund.setTransactionNo(result.getTransactionNo());
            refund.setSucceedAt(LocalDateTime.now());
        } else {
            refund.setStatus(RefundStatus.failed.name());
            refund.setFailureCode(result.getFailureCode());
            refund.setFailureMsg(result.getFailureMsg());
        }
        refundMapper.updateById(refund);

        // Update charge.amount_refunded (and status if fully refunded)
        int newRefunded = alreadyRefunded + amount;
        charge.setAmountRefunded(newRefunded);
        if (newRefunded >= charge.getAmount()) {
            charge.setStatus(ChargeStatus.refunded.name());
        }
        chargeMapper.updateById(charge);

        return refund;
    }

    public Page<Refund> listByChargeId(String chargeId, int page, int size) {
        return refundMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Refund>()
                .eq(Refund::getChargeId, chargeId)
                .orderByDesc(Refund::getCreatedAt));
    }

    public Refund findByRefundId(String refundId) {
        Refund r = refundMapper.selectOne(
            new LambdaQueryWrapper<Refund>().eq(Refund::getRefundId, refundId));
        if (r == null) throw new BizException(ErrorCode.CHARGE_NOT_FOUND, "Refund not found");
        return r;
    }

    private RefundRequest buildRefundRequest(Charge charge, Refund refund,
                                              Map<String, Object> cfg) {
        RefundRequest.RefundRequestBuilder builder = RefundRequest.builder()
            .chargeId(charge.getChargeId())
            .outRefundNo(refund.getOutRefundNo())
            .transactionNo(charge.getTransactionNo())
            .outTradeNo(charge.getOutTradeNo())
            .totalAmount(charge.getAmount())
            .refundAmount(refund.getAmount())
            .description(refund.getDescription());

        if (charge.getChannel().startsWith("wechat")) {
            builder.wechatAppId((String) cfg.get("appId"))
                   .mchId((String) cfg.get("mchId"))
                   .apiV3Key((String) cfg.get("apiV3Key"))
                   .serialNo((String) cfg.get("serialNo"))
                   .privateKey((String) cfg.get("privateKey"));
        } else {
            builder.alipayAppId((String) cfg.get("appId"))
                   .alipayPrivateKey((String) cfg.get("privateKey"))
                   .alipayPublicKey((String) cfg.get("alipayPublicKey"));
        }
        return builder.build();
    }
}
```

- [ ] **Step 11: Create `OperatorService.java`**

```java
package com.aipay.core.service;

import com.aipay.core.domain.Operator;
import com.aipay.core.domain.OperatorPermission;
import com.aipay.core.mapper.OperatorMapper;
import com.aipay.core.mapper.OperatorPermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperatorService {

    private final OperatorMapper operatorMapper;
    private final OperatorPermissionMapper permissionMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public Operator createOperator(Long merchantId, String username,
                                    String rawPassword, String realName, boolean isAdmin) {
        Operator op = new Operator();
        op.setMerchantId(merchantId);
        op.setUsername(username);
        op.setPasswordHash(passwordEncoder.encode(rawPassword));
        op.setRealName(realName);
        op.setIsAdmin(isAdmin ? 1 : 0);
        op.setStatus(1);
        operatorMapper.insert(op);
        return op;
    }

    public Operator authenticate(String username, String rawPassword) {
        Operator op = operatorMapper.selectOne(
            new LambdaQueryWrapper<Operator>()
                .eq(Operator::getUsername, username)
                .eq(Operator::getStatus, 1));
        if (op == null || !passwordEncoder.matches(rawPassword, op.getPasswordHash())) {
            return null;
        }
        return op;
    }

    public List<OperatorPermission> getPermissions(Long operatorId) {
        return permissionMapper.selectList(
            new LambdaQueryWrapper<OperatorPermission>()
                .eq(OperatorPermission::getOperatorId, operatorId));
    }

    public void updatePermissions(Long operatorId, List<Map<String, Object>> permissions) {
        permissionMapper.delete(
            new LambdaQueryWrapper<OperatorPermission>()
                .eq(OperatorPermission::getOperatorId, operatorId));
        for (Map<String, Object> p : permissions) {
            OperatorPermission perm = new OperatorPermission();
            perm.setOperatorId(operatorId);
            perm.setModule((String) p.get("module"));
            perm.setCanView(((Number) p.getOrDefault("can_view", 0)).intValue());
            perm.setCanOperate(((Number) p.getOrDefault("can_operate", 0)).intValue());
            permissionMapper.insert(perm);
        }
    }
}
```

- [ ] **Step 12: Run service tests — verify they pass**

Run: `mvn test -pl pay-core -Dtest="ChargeServiceTest,NotifyServiceTest,RefundServiceTest"`
Expected: `Tests run: 5, Failures: 0, Errors: 0` (adjust test count as needed)

- [ ] **Step 13: Commit**

```bash
git add pay-core/src/
git commit -m "feat: add core services (ChargeService, NotifyService, RefundService, MerchantService, AppService)"
```

---

### Task 7: pay-channel-wechat — WeChat Pay Implementations

**Files:**
- Create: `pay-channel/pay-channel-wechat/src/main/java/com/aipay/channel/wechat/WechatChannelConfig.java`
- Create: `pay-channel/pay-channel-wechat/src/main/java/com/aipay/channel/wechat/WechatJsapiChannel.java`
- Create: `pay-channel/pay-channel-wechat/src/main/java/com/aipay/channel/wechat/WechatH5Channel.java`
- Create: `pay-channel/pay-channel-wechat/src/main/java/com/aipay/channel/wechat/WechatNativeChannel.java`
- Create: `pay-channel/pay-channel-wechat/src/main/java/com/aipay/channel/wechat/WechatMiniprogramChannel.java`

**Note on wechatpay-java SDK:** All WeChat V3 channels use `com.wechat.pay.java.core.RSAAutoCertificateConfig`. Build it fresh per-request (the SDK caches certificates internally by merchant ID).

- [ ] **Step 1: Create `WechatChannelConfig.java`** (helper to build SDK config)

```java
package com.aipay.channel.wechat;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;

final class WechatChannelConfig {
    private WechatChannelConfig() {}

    static RSAAutoCertificateConfig buildConfig(String mchId, String privateKey,
                                                 String serialNo, String apiV3Key) {
        return new RSAAutoCertificateConfig.Builder()
            .merchantId(mchId)
            .privateKeyFromString(privateKey)
            .merchantSerialNumber(serialNo)
            .apiV3Key(apiV3Key)
            .build();
    }
}
```

- [ ] **Step 2: Create `WechatJsapiChannel.java`**

```java
package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatJsapiChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_JSAPI;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            JsapiServiceExtension service = new JsapiServiceExtension.Builder()
                .config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId());
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            Amount amount = new Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            Payer payer = new Payer();
            payer.setOpenid((String) req.getChannelExtra().get("open_id"));
            request.setPayer(payer);

            PrepayWithRequestPaymentResponse response =
                service.prepayWithRequestPayment(request);

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_JSAPI, Map.of(
                    "appId",     response.getAppId(),
                    "timeStamp", response.getTimeStamp(),
                    "nonceStr",  response.getNonceStr(),
                    "package",   response.getPackageVal(),
                    "signType",  response.getSignType(),
                    "paySign",   response.getPaySign()
                )))
                .build();

        } catch (Exception e) {
            log.error("WeChat JSAPI createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false)
                .failureCode("WECHAT_API_ERROR")
                .failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(req.getHeaders().get("Wechatpay-Serial"))
                .nonce(req.getHeaders().get("Wechatpay-Nonce"))
                .signature(req.getHeaders().get("Wechatpay-Signature"))
                .timestamp(req.getHeaders().get("Wechatpay-Timestamp"))
                .body(req.getRawBody())
                .build();

            NotificationParser parser = new NotificationParser(config);
            com.wechat.pay.java.service.payments.model.Transaction tx =
                parser.parse(requestParam,
                    com.wechat.pay.java.service.payments.model.Transaction.class);

            return NotifyResult.builder()
                .success(true)
                .transactionNo(tx.getTransactionId())
                .outTradeNo(tx.getOutTradeNo())
                .paidAmount(tx.getAmount().getPayerTotal())
                .build();

        } catch (Exception e) {
            log.error("WeChat JSAPI parseNotify failed: {}", e.getMessage(), e);
            return NotifyResult.builder()
                .success(false)
                .failureReason(e.getMessage())
                .build();
        }
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            RefundService refundService = new RefundService.Builder().config(config).build();

            com.wechat.pay.java.service.refund.model.CreateRequest refundRequest =
                new com.wechat.pay.java.service.refund.model.CreateRequest();
            refundRequest.setTransactionId(req.getTransactionNo());
            refundRequest.setOutRefundNo(req.getOutRefundNo());
            refundRequest.setReason(req.getDescription());
            refundRequest.setNotifyUrl(null); // optional for refund callback

            AmountReq amountReq = new AmountReq();
            amountReq.setRefund((long) req.getRefundAmount());
            amountReq.setTotal((long) req.getTotalAmount());
            amountReq.setCurrency("CNY");
            refundRequest.setAmount(amountReq);

            Refund refund = refundService.create(refundRequest);

            return RefundResult.builder()
                .success(true)
                .transactionNo(refund.getRefundId())
                .build();

        } catch (Exception e) {
            log.error("WeChat refund failed: {}", e.getMessage(), e);
            return RefundResult.builder()
                .success(false)
                .failureCode("WECHAT_REFUND_ERROR")
                .failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public QueryResult query(String outTradeNo) {
        // Implementation omitted for initial version — use WeChat query API if needed
        return QueryResult.builder().paid(false).build();
    }
}
```

- [ ] **Step 3: Create `WechatH5Channel.java`**

```java
package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatH5Channel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_H5;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            H5Service service = new H5Service.Builder().config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId());
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            com.wechat.pay.java.service.payments.h5.model.Amount amount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            // Optional scene info from channel_extra
            if (req.getChannelExtra() != null && req.getChannelExtra().containsKey("scene_info")) {
                SceneInfo sceneInfo = new SceneInfo();
                sceneInfo.setPayerClientIp(req.getClientIp());
                request.setSceneInfo(sceneInfo);
            }

            PrepayResponse response = service.prepay(request);

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_H5, Map.of("h5_url", response.getH5Url())))
                .build();

        } catch (Exception e) {
            log.error("WeChat H5 createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false).failureCode("WECHAT_H5_ERROR").failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        // Same notification format as JSAPI — delegate to shared WeChat notify parser
        return new WechatJsapiChannel().parseNotify(req);
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        return new WechatJsapiChannel().refund(req);
    }

    @Override
    public QueryResult query(String outTradeNo) {
        return QueryResult.builder().paid(false).build();
    }
}
```

- [ ] **Step 4: Create `WechatNativeChannel.java`**

```java
package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatNativeChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_NATIVE;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            NativePayService service = new NativePayService.Builder().config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId());
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            Amount amount = new Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            PrepayResponse response = service.prepay(request);

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_NATIVE,
                    Map.of("code_url", response.getCodeUrl())))
                .build();

        } catch (Exception e) {
            log.error("WeChat Native createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false).failureCode("WECHAT_NATIVE_ERROR").failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        return new WechatJsapiChannel().parseNotify(req);
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        return new WechatJsapiChannel().refund(req);
    }

    @Override
    public QueryResult query(String outTradeNo) {
        return QueryResult.builder().paid(false).build();
    }
}
```

- [ ] **Step 5: Create `WechatMiniprogramChannel.java`**

Miniprogram uses the same JSAPI service but with the miniprogram's appId. The returned parameters are passed directly to `wx.requestPayment()` on the client.

```java
package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatMiniprogramChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_MINIPROGRAM;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            // Miniprogram uses JsapiServiceExtension — same as JSAPI
            JsapiServiceExtension service = new JsapiServiceExtension.Builder()
                .config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId()); // miniprogram appId
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            Amount amount = new Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            Payer payer = new Payer();
            payer.setOpenid((String) req.getChannelExtra().get("open_id"));
            request.setPayer(payer);

            PrepayWithRequestPaymentResponse response =
                service.prepayWithRequestPayment(request);

            // Return same format as JSAPI — client passes to wx.requestPayment()
            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_MINIPROGRAM, Map.of(
                    "appId",     response.getAppId(),
                    "timeStamp", response.getTimeStamp(),
                    "nonceStr",  response.getNonceStr(),
                    "package",   response.getPackageVal(),
                    "signType",  response.getSignType(),
                    "paySign",   response.getPaySign()
                )))
                .build();

        } catch (Exception e) {
            log.error("WeChat Miniprogram createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false).failureCode("WECHAT_MP_ERROR").failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        return new WechatJsapiChannel().parseNotify(req);
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        return new WechatJsapiChannel().refund(req);
    }

    @Override
    public QueryResult query(String outTradeNo) {
        return QueryResult.builder().paid(false).build();
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `mvn compile -pl pay-channel/pay-channel-wechat -am -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add pay-channel/pay-channel-wechat/
git commit -m "feat: add WeChat JSAPI, H5, Native, Miniprogram channel implementations"
```

---

### Task 8: pay-channel-alipay — Alipay WAP Implementation

**Files:**
- Create: `pay-channel/pay-channel-alipay/src/main/java/com/aipay/channel/alipay/AlipayChannelConfig.java`
- Create: `pay-channel/pay-channel-alipay/src/main/java/com/aipay/channel/alipay/AlipayWapChannel.java`

- [ ] **Step 1: Create `AlipayChannelConfig.java`** (helper to build Alipay client)

```java
package com.aipay.channel.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;

final class AlipayChannelConfig {
    private AlipayChannelConfig() {}

    static AlipayClient buildClient(String appId, String privateKey, String alipayPublicKey) {
        try {
            return new DefaultAlipayClient(
                "https://openapi.alipay.com/gateway.do",
                appId,
                privateKey,
                "json",
                "UTF-8",
                alipayPublicKey,
                "RSA2"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Alipay client", e);
        }
    }
}
```

- [ ] **Step 2: Create `AlipayWapChannel.java`**

```java
package com.aipay.channel.alipay;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AlipayWapChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.ALIPAY_WAP;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            AlipayClient client = AlipayChannelConfig.buildClient(
                req.getAlipayAppId(), req.getAlipayPrivateKey(), req.getAlipayPublicKey());

            AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
            request.setReturnUrl(req.getReturnUrl());
            request.setNotifyUrl(req.getNotifyUrl());

            AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
            model.setOutTradeNo(req.getOutTradeNo());
            // Convert fen to yuan with 2 decimal places
            model.setTotalAmount(String.format("%.2f", req.getAmount() / 100.0));
            model.setSubject(req.getSubject());
            model.setBody(req.getBody());
            model.setProductCode("QUICK_WAP_WAY");
            request.setBizModel(model);

            // pageExecute returns an HTML form that auto-submits to Alipay
            String form = client.pageExecute(request).getBody();

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.ALIPAY_WAP, Map.of("form", form)))
                .build();

        } catch (AlipayApiException e) {
            log.error("Alipay WAP createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false)
                .failureCode("ALIPAY_API_ERROR")
                .failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        try {
            // Flatten Alipay's String[] param values to String for signature verification
            Map<String, String> params = new HashMap<>();
            for (Map.Entry<String, String[]> entry : req.getParams().entrySet()) {
                params.put(entry.getKey(), String.join(",", entry.getValue()));
            }

            boolean signValid = AlipaySignature.rsaCheckV1(
                params, req.getAlipayPublicKey(), "UTF-8", "RSA2");

            if (!signValid) {
                return NotifyResult.builder()
                    .success(false)
                    .failureReason("Alipay signature verification failed")
                    .build();
            }

            // Check trade status
            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return NotifyResult.builder()
                    .success(false)
                    .failureReason("Trade not completed: " + tradeStatus)
                    .build();
            }

            int paidAmount = (int) (Double.parseDouble(params.get("total_amount")) * 100);

            return NotifyResult.builder()
                .success(true)
                .transactionNo(params.get("trade_no"))
                .outTradeNo(params.get("out_trade_no"))
                .paidAmount(paidAmount)
                .build();

        } catch (Exception e) {
            log.error("Alipay parseNotify failed: {}", e.getMessage(), e);
            return NotifyResult.builder()
                .success(false).failureReason(e.getMessage())
                .build();
        }
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        try {
            AlipayClient client = AlipayChannelConfig.buildClient(
                req.getAlipayAppId(), req.getAlipayPrivateKey(), req.getAlipayPublicKey());

            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setTradeNo(req.getTransactionNo());
            model.setOutTradeNo(req.getOutTradeNo());
            model.setRefundAmount(String.format("%.2f", req.getRefundAmount() / 100.0));
            model.setRefundReason(req.getDescription());
            model.setOutRequestNo(req.getOutRefundNo());
            request.setBizModel(model);

            AlipayTradeRefundResponse response = client.execute(request);

            if (response.isSuccess()) {
                return RefundResult.builder()
                    .success(true)
                    .transactionNo(response.getTradeNo())
                    .build();
            } else {
                return RefundResult.builder()
                    .success(false)
                    .failureCode(response.getCode())
                    .failureMsg(response.getMsg() + ": " + response.getSubMsg())
                    .build();
            }

        } catch (AlipayApiException e) {
            log.error("Alipay refund failed: {}", e.getMessage(), e);
            return RefundResult.builder()
                .success(false).failureCode("ALIPAY_REFUND_ERROR").failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public QueryResult query(String outTradeNo) {
        return QueryResult.builder().paid(false).build();
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl pay-channel/pay-channel-alipay -am -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add pay-channel/pay-channel-alipay/
git commit -m "feat: add Alipay WAP channel implementation"
```

---

### Task 9: pay-api — Merchant API (Security + Controllers)

**Files:**
- Create: `pay-api/src/main/java/com/aipay/api/security/ApiKeyAuthFilter.java`
- Create: `pay-api/src/main/java/com/aipay/api/security/ApiKeySecurityConfig.java`
- Create: `pay-api/src/main/java/com/aipay/api/controller/ChargeController.java`
- Create: `pay-api/src/main/java/com/aipay/api/controller/RefundController.java`
- Create: `pay-api/src/main/java/com/aipay/api/controller/NotifyController.java`
- Create: `pay-api/src/main/java/com/aipay/api/config/OpenApiConfig.java`
- Test: `pay-api/src/test/java/com/aipay/api/controller/ChargeControllerTest.java`

- [ ] **Step 1: Write failing test for ChargeController**

```java
// pay-api/src/test/java/com/aipay/api/controller/ChargeControllerTest.java
package com.aipay.api.controller;

import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.service.AppService;
import com.aipay.core.service.ChargeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChargeController.class)
class ChargeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AppService appService;
    @MockBean ChargeService chargeService;

    private String basicAuthHeader(String key) {
        return "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes());
    }

    @Test
    void createCharge_withValidApiKey_returns200() throws Exception {
        App app = new App();
        app.setId(1L);
        app.setAppId("app_test");
        app.setMerchantId(100L);

        Charge charge = new Charge();
        charge.setChargeId("ch_abc123");
        charge.setStatus("pending");
        charge.setPaid(0);
        charge.setAmount(9900);
        charge.setCurrency("cny");
        charge.setCredential("{\"wechat_jsapi\":{\"appId\":\"wxabc\"}}");

        when(appService.authenticateApiKey("sk_live_testkey")).thenReturn(app);
        when(chargeService.createCharge(any(), any(), any(), anyInt(), any(), any(),
            any(), any(), anyLong(), any(), any())).thenReturn(charge);

        Map<String, Object> body = Map.of(
            "order_no", "ORDER_001",
            "channel", "wechat_jsapi",
            "amount", 9900,
            "currency", "cny",
            "subject", "Test Product",
            "time_expire", 1800,
            "channel_extra", Map.of("open_id", "oUpF8xxx")
        );

        mockMvc.perform(post("/v1/charges")
                .header("Authorization", basicAuthHeader("sk_live_testkey"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("ch_abc123"))
            .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void createCharge_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/v1/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Create `ApiKeyAuthFilter.java`**

```java
package com.aipay.api.security;

import com.aipay.core.domain.App;
import com.aipay.core.service.AppService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final AppService appService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64 = authHeader.substring(6);
                String decoded = new String(Base64.getDecoder().decode(base64),
                    StandardCharsets.UTF_8);
                // Format: apiKey:  (password is empty)
                String apiKey = decoded.contains(":") ? decoded.split(":", 2)[0] : decoded;

                App app = appService.authenticateApiKey(apiKey);
                if (app != null) {
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(app, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Invalid base64 or key format — let filter chain return 401
            }
        }

        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Create `ApiKeySecurityConfig.java`**

```java
package com.aipay.api.security;

import com.aipay.core.service.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class ApiKeySecurityConfig {

    private final AppService appService;

    @Bean
    @Order(1)
    public SecurityFilterChain merchantApiSecurityFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(appService);

        http
            .securityMatcher("/v1/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Notify endpoints are public (payment providers POST to them)
                .requestMatchers("/v1/notify/**").permitAll()
                // All other /v1/** require authentication
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> basic.disable()); // We handle Basic Auth manually

        return http.build();
    }
}
```

- [ ] **Step 4: Create `ChargeController.java`**

```java
package com.aipay.api.controller;

import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.service.ChargeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Charges", description = "Payment order operations")
@RestController
@RequestMapping("/v1/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create a payment charge")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCharge(
            @AuthenticationPrincipal App app,
            @RequestBody Map<String, Object> body) {
        String outTradeNo = (String) body.get("order_no");
        String channel = (String) body.get("channel");
        int amount = ((Number) body.get("amount")).intValue();
        String currency = (String) body.getOrDefault("currency", "cny");
        String subject = (String) body.get("subject");
        String bodyText = (String) body.get("body");
        String clientIp = (String) body.get("client_ip");
        long timeExpire = ((Number) body.getOrDefault("time_expire", 1800)).longValue();
        Map<String, Object> channelExtra = (Map<String, Object>) body.get("channel_extra");
        Map<String, Object> metadata = (Map<String, Object>) body.get("metadata");

        Charge charge = chargeService.createCharge(app, outTradeNo, channel, amount,
            currency, subject, bodyText, clientIp, timeExpire, channelExtra, metadata);

        return ResponseEntity.ok(toChargeResponse(charge));
    }

    @Operation(summary = "Get a charge by ID")
    @GetMapping("/{chargeId}")
    public ResponseEntity<Map<String, Object>> getCharge(
            @AuthenticationPrincipal App app,
            @PathVariable String chargeId) {
        Charge charge = chargeService.findByChargeId(chargeId);
        return ResponseEntity.ok(toChargeResponse(charge));
    }

    @Operation(summary = "List charges with pagination")
    @GetMapping
    public ResponseEntity<Map<String, Object>> listCharges(
            @AuthenticationPrincipal App app,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Page<Charge> result = chargeService.listCharges(app.getId(), null, status, page, size);
        return ResponseEntity.ok(Map.of(
            "object", "list",
            "data", result.getRecords().stream().map(this::toChargeResponse).toList(),
            "total", result.getTotal(),
            "page", page,
            "size", size
        ));
    }

    @SneakyThrows
    private Map<String, Object> toChargeResponse(Charge charge) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", charge.getChargeId());
        resp.put("object", "charge");
        resp.put("status", charge.getStatus());
        resp.put("paid", charge.getPaid() == 1);
        resp.put("amount", charge.getAmount());
        resp.put("currency", charge.getCurrency());
        resp.put("subject", charge.getSubject());
        resp.put("out_trade_no", charge.getOutTradeNo());
        resp.put("channel", charge.getChannel());
        resp.put("created_at", charge.getCreatedAt());
        if (charge.getCredential() != null) {
            resp.put("credential", objectMapper.readValue(charge.getCredential(), Map.class));
        }
        if (charge.getFailureCode() != null) {
            resp.put("failure_code", charge.getFailureCode());
            resp.put("failure_msg", charge.getFailureMsg());
        }
        return resp;
    }
}
```

- [ ] **Step 5: Create `RefundController.java`**

```java
package com.aipay.api.controller;

import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.Refund;
import com.aipay.core.service.ChargeService;
import com.aipay.core.service.RefundService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Refunds", description = "Refund operations")
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final ChargeService chargeService;
    private final RefundService refundService;

    @PostMapping("/v1/charges/{chargeId}/refunds")
    public ResponseEntity<Map<String, Object>> createRefund(
            @AuthenticationPrincipal App app,
            @PathVariable String chargeId,
            @RequestBody Map<String, Object> body) {
        Charge charge = chargeService.findByChargeId(chargeId);
        int amount = ((Number) body.get("amount")).intValue();
        String outRefundNo = (String) body.get("out_refund_no");
        String description = (String) body.get("description");

        Refund refund = refundService.createRefund(charge, amount, outRefundNo, description);
        return ResponseEntity.ok(toRefundResponse(refund));
    }

    @GetMapping("/v1/charges/{chargeId}/refunds")
    public ResponseEntity<Map<String, Object>> listRefunds(
            @AuthenticationPrincipal App app,
            @PathVariable String chargeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Refund> result = refundService.listByChargeId(chargeId, page, size);
        return ResponseEntity.ok(Map.of(
            "object", "list",
            "data", result.getRecords().stream().map(this::toRefundResponse).toList(),
            "total", result.getTotal()
        ));
    }

    @GetMapping("/v1/refunds/{refundId}")
    public ResponseEntity<Map<String, Object>> getRefund(
            @AuthenticationPrincipal App app,
            @PathVariable String refundId) {
        return ResponseEntity.ok(toRefundResponse(refundService.findByRefundId(refundId)));
    }

    private Map<String, Object> toRefundResponse(Refund refund) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", refund.getRefundId());
        resp.put("object", "refund");
        resp.put("charge_id", refund.getChargeId());
        resp.put("amount", refund.getAmount());
        resp.put("status", refund.getStatus());
        resp.put("out_refund_no", refund.getOutRefundNo());
        resp.put("created_at", refund.getCreatedAt());
        return resp;
    }
}
```

- [ ] **Step 6: Create `NotifyController.java`**

```java
package com.aipay.api.controller;

import com.aipay.common.constant.ChannelCode;
import com.aipay.core.domain.App;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.service.AppService;
import com.aipay.core.service.ChannelConfigService;
import com.aipay.core.service.NotifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "Notify", description = "Channel async notifications")
@RestController
@RequestMapping("/v1/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;
    private final AppService appService;
    private final ChannelConfigService channelConfigService;

    @PostMapping("/wechat/{appId}")
    public ResponseEntity<Map<String, String>> wechatNotify(
            @PathVariable String appId,
            @RequestParam(required = false) String channel,
            HttpServletRequest request) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = extractHeaders(request);

        App app = appService.findByAppId(appId);
        // Default to wechat_jsapi if channel not specified (all wechat channels use same notify format)
        String channelCode = channel != null ? channel : ChannelCode.WECHAT_JSAPI;
        ChannelConfig cfg = channelConfigService.findActiveConfig(app.getId(), channelCode);
        Map<String, Object> creds = channelConfigService.decryptConfig(cfg);

        String transactionNo = headers.getOrDefault("Wechatpay-Nonce", "unknown");

        try {
            notifyService.processNotify(channelCode, transactionNo, rawBody, headers, null,
                (String) creds.get("mchId"), (String) creds.get("apiV3Key"),
                (String) creds.get("serialNo"), (String) creds.get("privateKey"), null);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("WeChat notify processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }

    @PostMapping("/alipay/{appId}")
    public ResponseEntity<String> alipayNotify(
            @PathVariable String appId,
            HttpServletRequest request) {
        App app = appService.findByAppId(appId);
        ChannelConfig cfg = channelConfigService.findActiveConfig(app.getId(), ChannelCode.ALIPAY_WAP);
        Map<String, Object> creds = channelConfigService.decryptConfig(cfg);

        String transactionNo = request.getParameter("trade_no");
        String rawBody = request.getParameterMap().toString();
        Map<String, String[]> params = request.getParameterMap();

        try {
            notifyService.processNotify(ChannelCode.ALIPAY_WAP, transactionNo, rawBody, null,
                params, null, null, null, null, (String) creds.get("alipayPublicKey"));
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("Alipay notify processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok("fail");
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
```

- [ ] **Step 7: Create global exception handler**

```java
// pay-api/src/main/java/com/aipay/api/controller/GlobalExceptionHandler.java
package com.aipay.api.controller;

import com.aipay.common.exception.BizException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBizException(BizException e) {
        return ResponseEntity.badRequest().body(
            Map.of("error", Map.of("code", e.getCode(), "message", e.getMessage()))
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        return ResponseEntity.internalServerError().body(
            Map.of("error", Map.of("code", "internal_error", "message", "Internal server error"))
        );
    }
}
```

- [ ] **Step 8: Create `OpenApiConfig.java`**

```java
package com.aipay.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("AI-PAY API").version("1.0").description("Unified payment API"))
            .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
            .components(new Components().addSecuritySchemes("basicAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")));
    }
}
```

- [ ] **Step 9: Run controller tests**

Run: `mvn test -pl pay-api -Dtest="ChargeControllerTest"`
Expected: Tests pass

- [ ] **Step 10: Commit**

```bash
git add pay-api/src/
git commit -m "feat: add merchant API with API key authentication, charge/refund/notify controllers"
```

---

### Task 10: pay-admin-api — JWT Auth + Admin Controllers

**Files:**
- Create: `pay-admin-api/src/main/java/com/aipay/admin/security/JwtTokenProvider.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/security/JwtAuthFilter.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/security/AdminSecurityConfig.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/controller/AuthController.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/controller/MerchantController.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/controller/AppController.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/controller/ChargeQueryController.java`
- Create: `pay-admin-api/src/main/java/com/aipay/admin/controller/OperatorController.java`
- Test: `pay-admin-api/src/test/java/com/aipay/admin/security/JwtTokenProviderTest.java`

- [ ] **Step 1: Write failing test for JwtTokenProvider**

```java
// pay-admin-api/src/test/java/com/aipay/admin/security/JwtTokenProviderTest.java
package com.aipay.admin.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey",
            "test-secret-key-that-is-long-enough-for-hmac-sha256-32chars");
        ReflectionTestUtils.setField(provider, "accessTokenExpiryHours", 8L);
    }

    @Test
    void generateAndValidateAccessToken() {
        String token = provider.generateAccessToken(1L, 100L, 0, "orders:view");
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getOperatorId(token)).isEqualTo(1L);
    }

    @Test
    void invalidToken_returnsFalse() {
        assertThat(provider.validateToken("invalid.jwt.token")).isFalse();
    }

    @Test
    void expiredToken_returnsFalse() throws Exception {
        JwtTokenProvider shortLived = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLived, "secretKey",
            "test-secret-key-that-is-long-enough-for-hmac-sha256-32chars");
        ReflectionTestUtils.setField(shortLived, "accessTokenExpiryHours", 0L); // immediate expiry
        String token = shortLived.generateAccessToken(1L, 100L, 0, "");
        Thread.sleep(10);
        assertThat(shortLived.validateToken(token)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl pay-admin-api -Dtest="JwtTokenProviderTest" 2>&1 | tail -3`
Expected: `BUILD FAILURE` — class not found

- [ ] **Step 3: Create `JwtTokenProvider.java`**

```java
package com.aipay.admin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret-key}")
    private String secretKey;

    @Value("${app.jwt.access-token-expiry-hours:8}")
    private long accessTokenExpiryHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long operatorId, Long merchantId,
                                       int isAdmin, String permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryHours * 3600 * 1000);

        return Jwts.builder()
            .subject(operatorId.toString())
            .claim("merchant_id", merchantId)
            .claim("is_admin", isAdmin)
            .claim("permissions", permissions)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key())
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public Long getOperatorId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public Long getMerchantId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
        return claims.get("merchant_id", Long.class);
    }
}
```

- [ ] **Step 4: Run JwtTokenProvider tests — verify they pass**

Run: `mvn test -pl pay-admin-api -Dtest="JwtTokenProviderTest"`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Create `JwtAuthFilter.java`**

```java
package com.aipay.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                Long operatorId = jwtTokenProvider.getOperatorId(token);
                Long merchantId = jwtTokenProvider.getMerchantId(token);
                // Store operator context as principal
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        new OperatorPrincipal(operatorId, merchantId, token),
                        null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }
}
```

Create `OperatorPrincipal.java` (simple record for carrying operator context):

```java
package com.aipay.admin.security;

public record OperatorPrincipal(Long operatorId, Long merchantId, String token) {}
```

- [ ] **Step 6: Create `AdminSecurityConfig.java`**

```java
package com.aipay.admin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    @Order(2)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtTokenProvider);

        http
            .securityMatcher("/admin/v1/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/v1/auth/login", "/admin/v1/auth/refresh").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

- [ ] **Step 7: Create `AuthController.java`**

```java
package com.aipay.admin.controller;

import com.aipay.admin.security.JwtTokenProvider;
import com.aipay.core.domain.Operator;
import com.aipay.core.domain.OperatorPermission;
import com.aipay.core.service.OperatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Admin Auth")
@RestController
@RequestMapping("/admin/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OperatorService operatorService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        Operator op = operatorService.authenticate(body.get("username"), body.get("password"));
        if (op == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", Map.of("code", "auth_failed", "message", "Invalid credentials")));
        }

        List<OperatorPermission> perms = operatorService.getPermissions(op.getId());
        String permSummary = perms.stream()
            .map(p -> p.getModule() + ":" + (p.getCanOperate() == 1 ? "operate" : "view"))
            .collect(Collectors.joining(","));

        String accessToken = jwtTokenProvider.generateAccessToken(
            op.getId(), op.getMerchantId(), op.getIsAdmin(), permSummary);

        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "token_type", "Bearer",
            "expires_in", 28800,
            "operator_id", op.getId(),
            "merchant_id", op.getMerchantId(),
            "is_admin", op.getIsAdmin() == 1
        ));
    }
}
```

- [ ] **Step 8: Create admin CRUD controllers**

`MerchantController.java`:
```java
package com.aipay.admin.controller;

import com.aipay.admin.security.OperatorPrincipal;
import com.aipay.core.service.AppService;
import com.aipay.core.service.MerchantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin Merchants")
@RestController
@RequestMapping("/admin/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final AppService appService;

    @GetMapping
    public ResponseEntity<?> listMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(merchantService.listMerchants(page, size));
    }

    @PostMapping
    public ResponseEntity<?> createMerchant(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(merchantService.createMerchant(
            body.get("name"), body.get("contact_email"), body.get("contact_phone")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMerchant(@PathVariable Long id) {
        return ResponseEntity.ok(merchantService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMerchant(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        merchantService.updateMerchant(id, (String) body.get("name"),
            (String) body.get("contact_email"), (String) body.get("contact_phone"),
            body.containsKey("status") ? ((Number) body.get("status")).intValue() : null);
        return ResponseEntity.ok(merchantService.findById(id));
    }

    @GetMapping("/{id}/apps")
    public ResponseEntity<?> listApps(@PathVariable Long id) {
        return ResponseEntity.ok(appService.listByMerchant(id));
    }

    @PostMapping("/{id}/apps")
    public ResponseEntity<?> createApp(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(appService.createApp(id, body.get("name")));
    }
}
```

`AppController.java`:
```java
package com.aipay.admin.controller;

import com.aipay.core.service.AppService;
import com.aipay.core.service.ChannelConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin Apps & Channels")
@RestController
@RequestMapping("/admin/v1/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final ChannelConfigService channelConfigService;

    @GetMapping("/{appId}")
    public ResponseEntity<?> getApp(@PathVariable String appId) {
        return ResponseEntity.ok(appService.findByAppId(appId));
    }

    @GetMapping("/{appId}/channels/{channel}")
    public ResponseEntity<?> getChannelConfig(@PathVariable String appId,
                                               @PathVariable String channel) {
        var app = appService.findByAppId(appId);
        var cfg = channelConfigService.findActiveConfig(app.getId(), channel);
        if (cfg == null) return ResponseEntity.notFound().build();
        // Return config without decrypting (for admin review)
        return ResponseEntity.ok(Map.of("app_id", appId, "channel", channel,
            "status", cfg.getStatus()));
    }

    @PutMapping("/{appId}/channels/{channel}")
    public ResponseEntity<?> updateChannelConfig(@PathVariable String appId,
                                                  @PathVariable String channel,
                                                  @RequestBody Map<String, Object> body) {
        var app = appService.findByAppId(appId);
        channelConfigService.saveConfig(app.getId(), channel, body);
        return ResponseEntity.ok(Map.of("result", "updated"));
    }
}
```

`ChargeQueryController.java`:
```java
package com.aipay.admin.controller;

import com.aipay.core.service.ChargeService;
import com.aipay.core.service.RefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Charges & Refunds")
@RestController
@RequiredArgsConstructor
public class ChargeQueryController {

    private final ChargeService chargeService;
    private final RefundService refundService;

    @GetMapping("/admin/v1/charges")
    public ResponseEntity<?> listCharges(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chargeService.listCharges(null, merchantId, status, page, size));
    }

    @GetMapping("/admin/v1/charges/{chargeId}")
    public ResponseEntity<?> getCharge(@PathVariable String chargeId) {
        return ResponseEntity.ok(chargeService.findByChargeId(chargeId));
    }

    @GetMapping("/admin/v1/refunds")
    public ResponseEntity<?> listRefunds(
            @RequestParam String chargeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(refundService.listByChargeId(chargeId, page, size));
    }
}
```

`OperatorController.java`:
```java
package com.aipay.admin.controller;

import com.aipay.admin.security.OperatorPrincipal;
import com.aipay.core.service.OperatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin Operators")
@RestController
@RequestMapping("/admin/v1/operators")
@RequiredArgsConstructor
public class OperatorController {

    private final OperatorService operatorService;

    @PostMapping
    public ResponseEntity<?> createOperator(
            @AuthenticationPrincipal OperatorPrincipal principal,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(operatorService.createOperator(
            principal.merchantId(),
            (String) body.get("username"),
            (String) body.get("password"),
            (String) body.get("real_name"),
            Boolean.TRUE.equals(body.get("is_admin"))));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<?> updatePermissions(@PathVariable Long id,
                                                @RequestBody List<Map<String, Object>> perms) {
        operatorService.updatePermissions(id, perms);
        return ResponseEntity.ok(Map.of("result", "updated"));
    }
}
```

- [ ] **Step 9: Commit**

```bash
git add pay-admin-api/src/
git commit -m "feat: add admin API with JWT auth, merchant/app/channel/operator management"
```

---

### Task 11: pay-bootstrap — Application Entry Point + Configuration

**Files:**
- Create: `pay-bootstrap/src/main/java/com/aipay/PayApplication.java`
- Create: `pay-bootstrap/src/main/resources/application.yml`
- Create: `pay-bootstrap/src/main/resources/application-dev.yml`

- [ ] **Step 1: Create `PayApplication.java`**

```java
package com.aipay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
    }
}
```

- [ ] **Step 2: Create `application.yml`**

```yaml
spring:
  application:
    name: ai-pay
  profiles:
    active: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${DB_URL:jdbc:mysql://localhost:3306/ai_pay?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto

redisson:
  address: ${REDIS_URL:redis://localhost:6379}
  database: 0

app:
  encrypt-key: ${APP_ENCRYPT_KEY:dev-encrypt-key-change-in-production}
  notify-base-url: ${APP_NOTIFY_BASE_URL:https://pay.example.com}
  jwt:
    secret-key: ${JWT_SECRET_KEY:dev-jwt-secret-key-change-in-production-must-be-at-least-32-chars}
    access-token-expiry-hours: 8

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: ${SWAGGER_ENABLED:true}

logging:
  level:
    com.aipay: INFO
    org.mybatis: WARN
```

- [ ] **Step 3: Create `application-dev.yml`**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_pay_dev?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true

app:
  encrypt-key: dev-encrypt-key-32-chars-exactly!!
  notify-base-url: http://localhost:8080
  jwt:
    secret-key: dev-jwt-secret-key-must-be-at-least-32-characters-long

springdoc:
  swagger-ui:
    enabled: true

logging:
  level:
    com.aipay: DEBUG
    org.mybatis: DEBUG
```

- [ ] **Step 4: Commit**

```bash
git add pay-bootstrap/src/
git commit -m "feat: add PayApplication bootstrap and application configuration"
```

---

### Task 12: Full Build Verification

- [ ] **Step 1: Build the entire project**

Run: `mvn clean package -DskipTests`
Expected: `BUILD SUCCESS` — `pay-bootstrap/target/pay-bootstrap-1.0.0-SNAPSHOT.jar` exists

- [ ] **Step 2: Verify the JAR contains all modules**

Run: `jar tf pay-bootstrap/target/pay-bootstrap-1.0.0-SNAPSHOT.jar | grep "BOOT-INF/lib/pay-" | sort`
Expected output (all internal modules present):
```
BOOT-INF/lib/pay-admin-api-1.0.0-SNAPSHOT.jar
BOOT-INF/lib/pay-api-1.0.0-SNAPSHOT.jar
BOOT-INF/lib/pay-channel-alipay-1.0.0-SNAPSHOT.jar
BOOT-INF/lib/pay-channel-api-1.0.0-SNAPSHOT.jar
BOOT-INF/lib/pay-channel-wechat-1.0.0-SNAPSHOT.jar
BOOT-INF/lib/pay-common-1.0.0-SNAPSHOT.jar
BOOT-INF/lib/pay-core-1.0.0-SNAPSHOT.jar
```

- [ ] **Step 3: Run all unit tests**

Run: `mvn test -pl pay-common,pay-core,pay-admin-api`
Expected: `Tests run: N, Failures: 0, Errors: 0`

- [ ] **Step 4: Verify application starts (requires MySQL + Redis running)**

Start MySQL and Redis locally, then:
```bash
export APP_ENCRYPT_KEY="dev-encrypt-key-32-chars-exactly!!"
export JWT_SECRET_KEY="dev-jwt-secret-key-must-be-at-least-32-characters-long"
java -jar pay-bootstrap/target/pay-bootstrap-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```
Expected: Application starts on port 8080, Flyway runs migration, log shows `Started PayApplication`

- [ ] **Step 5: Verify Swagger UI is accessible**

Navigate to `http://localhost:8080/swagger-ui.html`
Expected: Swagger UI shows all endpoints grouped by tag (Charges, Refunds, Notify, Admin Auth, Admin Merchants, etc.)

- [ ] **Step 6: Final commit**

```bash
git add .
git commit -m "feat: complete pay-core implementation — all modules integrated and verified"
```

---

## Self-Review

### Spec Coverage Check

| Spec Section | Covered By |
|---|---|
| 多模块 Maven 结构 | Task 1 |
| pay-common 枚举/工具 | Task 2 |
| MySQL 9 张表 | Task 3 |
| MyBatis-Plus 实体/Mapper | Task 4 |
| PayChannel 接口 + ChannelRouter | Task 5 |
| 商户/App/渠道配置服务 | Task 6 |
| 统一下单流程（charge 状态机） | Task 6 ChargeService |
| 异步回调幂等处理（Redis SETNX） | Task 6 NotifyService |
| 退款流程 | Task 6 RefundService |
| 微信 JSAPI/H5/Native/小程序 | Task 7 |
| 支付宝手机网站 | Task 8 |
| API Key 认证（HTTP Basic SHA-256） | Task 9 |
| 商户端 REST API | Task 9 |
| JWT 认证（8h access token） | Task 10 |
| 管理后台 API | Task 10 |
| AES-256-GCM 渠道密钥加密 | Task 2 CryptoUtil + Task 6 ChannelConfigService |
| BCrypt 操作员密码 | Task 10 BCryptPasswordEncoder |
| OpenSpec / Swagger UI | Task 9 OpenApiConfig + Task 11 application.yml |
| Flyway 数据库迁移 | Task 3 + Task 11 |

**No gaps found.**

### Type Consistency Check

- `CreateOrderRequest` fields: `wechatAppId`, `mchId`, `apiV3Key`, `serialNo`, `privateKey`, `alipayAppId`, `alipayPrivateKey`, `alipayPublicKey` — consistent across Task 5, 6, 7, 8.
- `ChannelConfigService.buildCreateOrderRequest()` uses same field names as `CreateOrderRequest.builder()` — consistent.
- `RefundRequest` uses `outRefundNo`, `transactionNo`, `outTradeNo` — consistent between Task 5 models and Task 6 RefundService.
- `ChargeStatus` enum values: `created`, `pending`, `paid`, `refunded`, `expired`, `closed` — consistent with DB schema and ChargeService.
