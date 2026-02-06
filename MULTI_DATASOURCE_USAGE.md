# 多数据源配置使用说明

## 概述

本项目已配置支持多数据源，包括三个业务数据源：分销数据源（ticket_distribution）、订单数据源（ticket_booking）和本地数据源（text2sql_db），支持业务数据分离和负载均衡。

## 配置说明

### application.yml 配置

```yaml
spring:
  datasource:
    # 分销数据源 (主数据源，用于分销业务)
    ticket_distribution:
      url: jdbc:mysql://120.133.0.220:4406/ticket_distribution
      username: ticket
      password: dfj2!@dVN8dVLOsd
      hikari:
        pool-name: PrimaryHikariPool
        maximum-pool-size: 20
        minimum-idle: 5
    
    # 订单数据源 (用于订单和票务业务)
    ticket_booking:
      url: jdbc:mysql://120.133.0.220:4406/ticket_booking
      username: ticket
      password: dfj2!@dVN8dVLOsd
      hikari:
        pool-name: PrimaryHikariPool
        maximum-pool-size: 20
        minimum-idle: 5
    
    # 本地数据源 (用于测试和演示)
    text2sql_db:
      url: jdbc:mysql://localhost:3306/text2sql_db
      username: root
      password: 12345678
      hikari:
        pool-name: Replica1HikariPool
        maximum-pool-size: 15
        minimum-idle: 3
```

## 核心组件

### 1. MultiDataSourceConfig

多数据源配置类，负责创建和管理多个数据源的 Bean：
- `primaryDataSource()`: 主数据源
- `replica1DataSource()`: 从数据源1
- `replica2DataSource()`: 从数据源2

### 2. DataSourceRouter

数据源路由器，提供数据源选择和管理功能：
- `getPrimaryDataSource()`: 获取分销数据源（主数据源）
- `getReadDataSource()`: 获取读数据源（负载均衡）
- `getDataSourceByName(String name)`: 根据名称获取数据源
- `testAllConnections()`: 测试所有数据源连接

### 3. DatabaseTool

增强的数据库工具类，支持多数据源操作：
- 支持指定数据源进行查询
- 提供数据源信息查询
- 支持数据源连接测试

## API 使用说明

### 1. 基础查询 API

#### 获取表列表

```bash
# 获取默认数据源的表列表
GET /api/table-names

# 从指定数据源获取表列表
GET /api/table-names-with-datasource?dataSource=ticket_booking

# 从本地数据源获取表列表
GET /api/table-names-with-datasource?dataSource=text2sql_db
```

#### 获取表结构

```bash
# 获取默认数据源的数据库结构
GET /api/schema

# 从指定数据源获取数据库结构
GET /api/schema-with-datasource?dataSource=ticket_distribution

# 从订单数据源获取数据库结构
GET /api/schema-with-datasource?dataSource=ticket_booking
```

### 2. 自然语言查询 API

#### 智能查询（使用 AI）

```bash
# 在默认数据源执行自然语言查询
POST /api/query
{
  "query": "查询所有员工信息"
}

# 在指定数据源执行自然语言查询
POST /api/query-with-datasource
{
  "query": "查询所有订单信息",
  "dataSource": "ticket_booking"
}

# 在分销数据源执行查询
POST /api/query-with-datasource
{
  "query": "查询代理商佣金信息",
  "dataSource": "ticket_distribution"
}
```

#### 响应格式

```json
{
  "success": true,
  "dataSource": "replica1",
  "sql": "SELECT * FROM employee LIMIT 1000",
  "data": [
    {"id": 1, "name": "张三", "department": "技术部"},
    {"id": 2, "name": "李四", "department": "产品部"}
  ],
  "count": 2
}
```

### 3. 数据源管理 API

#### 测试数据源连接

```bash
# 测试所有数据源连接状态
GET /api/test-datasources
```

响应示例：
```json
{
  "status": "所有数据源连接正常"
}
```

#### 获取数据源信息

```bash
# 获取数据源配置和状态信息
GET /api/datasource-info
```

### 4. AI 工具查询方式

```bash
# 通过 AI 工具方式查询（推荐）
POST /api/text2sql
{
  "message": "获取所有表"
}

# 指定数据源的 AI 查询
POST /api/text2sql  
{
  "message": "从 ticket_booking 数据源获取所有表"
}

# 业务场景智能查询
POST /api/text2sql
{
  "message": "查询分销代理商的佣金明细"
}

# 获取表结构
POST /api/text2sql
{
  "message": "获取 employee 表的结构"
}

# 执行查询
POST /api/text2sql
{
  "message": "查询所有员工信息"
}

# 数据源管理
POST /api/text2sql
{
  "message": "测试所有数据源的连接状态"
}
```

### 5. API 对比说明

| API 类型 | 用途 | 优势 | 推荐场景 |
|---------|------|------|----------|
| `/api/query` | 基础自然语言查询 | 简单直接 | 简单查询需求 |
| `/api/query-with-datasource` | 指定数据源查询 | 精确控制数据源 | 需要特定数据源的场景 |
| `/api/text2sql` | AI 工具查询 | 功能最全，支持多种数据源操作 | 复杂查询和数据源管理 |

## 数据源说明

| 数据源名称 | 业务场景 | 说明 |
|-----------|---------|------|
| ticket_distribution | 分销业务 | 分销数据源（主数据源），用于代理商、佣金、分销等业务 |
| ticket_booking | 订单业务 | 订单数据源，用于订单、票务、支付等业务 |
| text2sql_db | 测试演示 | 本地数据源，用于测试和演示 |
| distribution, primary, master | 分销别名 | ticket_distribution 数据源的别名 |
| booking, order | 订单别名 | ticket_booking 数据源的别名 |
| text2sql, local, test | 本地别名 | text2sql_db 数据源的别名 |
| read | 负载均衡 | 自动选择数据源的负载均衡查询 |

## 负载均衡策略

当前实现采用简单的轮询策略：
1. 请求读操作时，系统会在三个数据源之间轮询
2. 如果指定的数据源不可用，会自动回退到主数据源
3. 写操作始终使用主数据源

## 最佳实践

1. **业务数据分离**: 根据业务场景选择合适的数据源
   - 分销业务 → 使用 `ticket_distribution` 数据源
   - 订单业务 → 使用 `ticket_booking` 数据源  
   - 测试演示 → 使用 `text2sql_db` 数据源

2. **智能数据源选择**: AI 会根据查询内容自动选择合适的数据源
3. **负载均衡**: 对于不确定的数据查询，使用 `read` 数据源进行负载均衡
4. **故障转移**: 当指定数据源不可用时，系统会自动使用分销数据源
5. **连接池配置**: 根据实际负载调整各数据源的连接池大小
6. **监控**: 定期使用 `testDataSources` 检查数据源状态

## 注意事项

1. 不同数据源可能包含不同的数据库结构和业务数据
2. 生产环境中需要确保远程数据源的网络连接稳定
3. 建议为不同的业务场景配置独立的数据源访问权限
4. 监控各数据源的连接状态和性能指标
5. 定期检查数据同步状态（如果存在主从复制）
6. 使用合适的数据源别名以提高查询效率