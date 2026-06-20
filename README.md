# Food Social Platform

一个基于 **Spring Boot + Spring Cloud** 的餐饮社交微服务项目，聚焦餐饮场景下的用户社交、积分体系、附近的人、签到排行、统一网关鉴权与高并发秒杀能力。

本项目适合作为 Java 后端微服务、Redis 多场景应用、网关统一鉴权、高并发业务设计的学习与面试项目。

## 技术栈

* Java 8
* Spring Boot 2.3.5.RELEASE
* Spring Cloud Hoxton.SR8
* Spring Cloud Gateway
* Spring Security OAuth2
* Eureka
* MyBatis
* MySQL
* Redis
* Redisson
* Lua
* Maven
* JMeter

## 项目结构

```text
food-social-contact-parent
├── commons                 # 公共模块：通用实体、常量、工具类、统一返回体
├── db                      # 数据库脚本、压测/JMeter 相关文件
├── ms-registry             # 注册中心，负责服务注册与发现
├── ms-gateway              # 网关服务，统一入口、路由转发、白名单与 Token 校验
├── ms-oauth2-server        # 认证授权中心，负责登录、令牌签发、令牌校验
├── ms-diners               # 食客/用户服务
├── ms-restaurants          # 餐厅服务
├── ms-follow               # 关注关系服务
├── ms-feeds                # 动态/内容流服务
├── ms-points               # 积分、签到、排行榜服务
└── ms-seckill              # 秒杀服务，负责优惠券秒杀、库存扣减与并发控制
```

## 核心功能

### 1. 用户与社交

* 用户注册、登录与个人信息管理
* 关注 / 取关关系维护
* 社交动态发布与信息流展示

### 2. 餐饮业务

* 餐厅信息查询
* 餐饮场景相关业务扩展
* 基于 Redis GEO 实现附近的人相关能力

### 3. Redis 多场景应用

* Bitmap：用户签到
* Sorted Set：积分排行榜
* GEO：附近的人
* Hash / String：缓存业务数据与秒杀库存

### 4. 高并发秒杀

* 使用 Lua 脚本保证库存扣减原子性
* 使用 Redisson 控制并发访问
* 实现秒杀场景下的防超卖与并发保护
* 处理重复下单、库存不足等常见问题

### 5. 网关与认证

* 客户端请求统一进入 `ms-gateway`
* 网关根据路由规则转发到不同业务服务
* 白名单接口直接放行
* 非白名单接口需要携带 Token
* 网关调用认证服务校验 Token
* Token 校验通过后再转发到具体业务服务

## 系统架构说明

整体采用多模块 Maven 父子工程结构，将公共能力、网关、认证中心、业务服务和秒杀服务进行拆分，便于服务独立开发、维护和扩展。

请求链路示例：

```text
客户端请求
    ↓
ms-gateway 网关
    ↓
白名单判断 / Token 校验
    ↓
ms-oauth2-server 认证服务
    ↓
具体业务服务
    ↓
MySQL / Redis
```

## 秒杀流程简述

```text
用户发起秒杀请求
    ↓
网关统一转发
    ↓
秒杀服务进行基础参数校验
    ↓
Redis Lua 脚本原子扣减库存
    ↓
Redisson 控制并发访问
    ↓
创建秒杀订单
    ↓
返回秒杀结果
```

## 项目亮点

* 基于 Spring Cloud Gateway 实现统一网关入口、路由转发与鉴权控制
* 基于 OAuth2 实现登录认证、Token 签发与 Token 校验
* 基于 Redis 实现签到、排行榜、附近的人、缓存与秒杀库存等多场景能力
* 基于 Lua 脚本保证秒杀库存扣减的原子性
* 基于 Redisson 处理高并发场景下的并发访问控制
* 采用多模块 Maven 工程结构，便于公共依赖管理与服务拆分
* 包含数据库脚本与 JMeter 压测相关文件，便于本地测试与性能验证

## 本地启动说明

### 1. 准备环境

需要提前安装并启动：

* JDK 8
* Maven
* MySQL
* Redis

### 2. 初始化数据库

执行 `db` 目录下的 SQL 脚本，初始化项目所需数据表和测试数据。

### 3. 修改配置文件

各服务的配置文件位于：

```text
各模块/src/main/resources/application.yml
```

请根据本地环境修改：

* MySQL 地址
* MySQL 用户名与密码
* Redis 地址
* 服务端口
* 认证相关配置

### 4. 启动服务

建议按以下顺序启动：

```text
1. ms-registry
2. ms-oauth2-server
3. ms-gateway
4. ms-diners
5. ms-restaurants
6. ms-follow
7. ms-feeds
8. ms-points
9. ms-seckill
```

## 后续优化方向

* 接入 RabbitMQ，将下单、积分、通知等非核心链路异步化
* 增加接口压测报告，补充 QPS、响应时间、错误率等指标
* 优化秒杀链路，增加异步下单与削峰能力
* 增加统一日志追踪与异常监控
* 补充 Docker Compose，一键启动 MySQL、Redis 与各微服务
* 完善接口文档与核心流程图

## 项目说明

本项目主要用于 Java 后端微服务学习、Redis 场景实践、高并发业务设计与面试展示。项目中部分配置已使用占位符处理，实际运行时需要根据本地环境自行修改。
