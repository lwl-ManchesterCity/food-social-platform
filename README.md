Food Social Platform
一个基于 Spring Boot + Spring Cloud 的餐饮社交微服务项目，聚焦餐饮场景下的社交互动、积分体系与高并发秒杀能力。
适合作为 Java 后端微服务、Redis 多场景应用、网关统一鉴权、高并发场景设计 的学习与面试项目。
项目亮点
基于 Gateway + OAuth2 实现统一网关入口与鉴权
基于 Eureka 实现服务注册与发现
基于 Redis 支撑签到、排行榜、附近的人、秒杀库存等高频场景
基于 Lua + Redisson 处理秒杀中的库存扣减与并发控制
采用多模块 Maven 父子工程结构，便于服务拆分与统一依赖管理
技术栈
Java 8
Spring Boot 2.3.5.RELEASE
Spring Cloud Hoxton.SR8
Spring Cloud Gateway
Spring Security OAuth2
Eureka
MyBatis
MySQL
Redis
Redisson
Maven
Lua
项目结构
food-social-contact-parent
├── commons            # 公共模块：通用实体、常量、工具类、统一返回体
├── db                 # 数据脚本、压测/JMeter 相关文件
├── ms-registry        # 注册中心
├── ms-gateway         # 网关服务，统一入口、路由转发、白名单与 token 校验
├── ms-oauth2-server   # 认证授权中心，负责登录、令牌签发、令牌校验
├── ms-diners          # 食客/用户服务
├── ms-restaurants     # 餐厅服务
├── ms-follow          # 关注关系服务
├── ms-feeds           # 动态/内容流服务
├── ms-points          # 积分、签到、排行榜服务
└── ms-seckill         # 秒杀服务
核心功能
1. 用户与社交
用户注册、登录、个人信息管理
关注/被关注关系维护
社交动态发布与信息流展示
2. 餐饮业务
餐厅信息查询
餐饮场景相关业务扩展
3. Redis 多场景应用
Bitmap：用户签到
Sorted Set：积分排行榜
GEO：附近的人
Hash / String：缓存业务数据与秒杀库存
4. 高并发秒杀
使用 Lua 脚本保证库存扣减原子性
使用 Redisson 控制并发访问
实现秒杀场景下的防超卖与并发保护
系统架构
客户端请求统一进入 ms-gateway，网关根据路由规则将请求转发到不同业务服务。
ms-registry：维护服务注册与发现
ms-gateway：统一入口，负责鉴权与路由
ms-oauth2-server：生成并校验 token
各业务服务：按领域职责拆分
Redis + MySQL：分别承担高频访问支撑与业务数据持久化
鉴权流程简述
用户登录请求先进入 Gateway
登录相关接口命中白名单后放行到 OAuth2 服务
认证成功后生成 token，并存入 Redis
后续业务请求再次进入 Gateway
网关校验请求是否在白名单中
非白名单请求需携带 token
网关调用认证服务校验 token
校验通过后转发至具体业务服务
秒杀设计简述
秒杀服务重点解决高并发下的超卖与重复下单问题：
请求进入秒杀服务后先进行基础校验
基于 Redis Lua 脚本原子扣减库存
基于 Redisson 控制同一用户的并发抢购行为
结合业务校验限制重复下单
本地启动说明
1. 环境准备
JDK 1.8
Maven 3.6+
MySQL 5.7+/8.x
Redis
2. 初始化
导入 db 目录下的 SQL 脚本
根据本地环境修改各服务 application.yml 中的数据库、Redis 与端口配置
3. 推荐启动顺序
ms-registry
ms-oauth2-server
ms-gateway
其他业务服务：
ms-diners、ms-restaurants、ms-follow、ms-feeds、ms-points、ms-seckill
4. 启动方式
在项目根目录或对应模块目录执行：
mvn clean install
然后分别启动各个 Spring Boot 服务。
学习与面试可展开点
微服务拆分与父子模块管理
Gateway 统一鉴权设计
OAuth2 + Redis TokenStore 登录体系
Redis 在签到、排行榜、GEO、秒杀中的应用
Lua 脚本与分布式锁在高并发场景中的使用
后续可继续演进的方向：
RabbitMQ 异步削峰、链路监控、缓存优化、服务限流熔断
仓库说明
当前仓库主要用于项目学习、复习和面试整理。如果你也在准备 Java 后端面试，欢迎参考这个项目的模块划分、鉴权链路和高并发设计思路。
