# ThreadLocal 数据源路由使用说明

## 概述

数据源路由现在使用 ThreadLocal 机制来管理当前线程的数据源上下文，这使得数据源切换更加透明和线程安全。

## 核心特性

### 1. ThreadLocal 数据源上下文

每个线程维护独立的数据源上下文，确保多线程环境下数据源选择的正确性。

### 2. 自动上下文管理

通过 `DataSourceRouter.executeWithDataSource()` 方法自动管理数据源上下文的设置和清理。

### 3. 便捷的工具方法

提供静态方法和 try-with-resources 风格的上下文管理。

## 使用方式

### 1. 基本用法（推荐）

```java
// 使用 executeWithDataSource 方法
String result = DataSourceRouter.executeWithDataSource("ticket_booking", () -> {
    // 在这个 lambda 中，所有数据库操作都会自动使用 ticket_booking 数据源
    return databaseTool.getTableNames();
});
```

### 2. Try-with-resources 风格

```java
// 使用 withDataSource 创建上下文
try (DataSourceRouter.DataSourceContext context = DataSourceRouter.withDataSource("ticket_distribution")) {
    // 在 try 块中，所有操作都使用 ticket_distribution 数据源
    List<Map<String, Object>> results = databaseTool.executeQuery("SELECT * FROM agents");
    // 自动清理数据源上下文
}
```

### 3. 手动管理（不推荐）

```java
// 手动设置数据源
DataSourceRouter.setDataSource("text2sql_db");
try {
    // 执行数据库操作
    String schema = databaseTool.getDatabaseSchema();
} finally {
    // 清理数据源上下文
    DataSourceRouter.clearDataSource();
}
```

## API 方法说明

### DataSourceRouter 静态方法

#### `DataSourceRouter.setDataSource(String dataSourceName)`
设置当前线程的数据源名称。

#### `DataSourceRouter.getDataSource()`
获取当前线程的数据源名称。

#### `DataSourceRouter.clearDataSource()`
清除当前线程的数据源名称。

#### `DataSourceRouter.executeWithDataSource(String dataSourceName, DataSourceCallback<T> callback)`
在指定数据源上下文中执行操作，自动管理上下文设置和清理。

#### `DataSourceRouter.withDataSource(String dataSourceName)`
创建数据源上下文对象，支持 try-with-resources。

### DatabaseTool 更新

所有支持多数据源的方法现在都使用 ThreadLocal 机制：

- `getTableNames(dataSourceName)`
- `getTableSchemaFromDataSource(tableName, dataSourceName)`
- `getDatabaseSchemaFromDataSource(dataSourceName)`
- `getTableColumns(tableName, dataSourceName)`
- `executeQueryOnDataSource(sql, dataSourceName)`

## 数据源名称映射

| 业务场景 | 数据源名称 | 支持的别名 |
|---------|-----------|-----------|
| 分销业务 | `ticket_distribution` | `distribution`, `primary`, `master` |
| 订单业务 | `ticket_booking` | `booking`, `order` |
| 测试演示 | `text2sql_db` | `text2sql`, `local`, `test` |
| 负载均衡 | `read` | `replica`, `any` |

## 优势

### 1. 线程安全
每个线程有独立的数据源上下文，避免并发问题。

### 2. 透明使用
在上下文中，数据库操作无需显式传递数据源参数。

### 3. 自动清理
使用 executeWithDataSource 或 try-with-resources 确保上下文自动清理。

### 4. 灵活性
支持多种使用方式，适应不同的编程风格。

## 示例场景

### 场景1：订单查询服务

```java
@Service
public class OrderService {
    
    public List<Order> getOrdersByDate(String date) {
        return DataSourceRouter.executeWithDataSource("ticket_booking", () -> {
            String sql = "SELECT * FROM orders WHERE order_date = ?";
            return jdbcTemplate.query(sql, new Object[]{date}, orderRowMapper);
        });
    }
}
```

### 场景2：分销佣金统计

```java
@Service
public class CommissionService {
    
    public CommissionReport generateReport(String agentId) {
        try (DataSourceRouter.DataSourceContext context = 
             DataSourceRouter.withDataSource("ticket_distribution")) {
            
            // 所有数据库操作都使用分销数据源
            List<Commission> commissions = getCommissions(agentId);
            Agent agent = getAgentInfo(agentId);
            return new CommissionReport(agent, commissions);
        }
    }
}
```

### 场景3：AI 工具中的使用

```java
// AI 工具调用示例
@Tool(name = "getBookingData", description = "获取订单数据")
public String getBookingData(@ToolParam(description = "查询条件") String condition) {
    return DataSourceRouter.executeWithDataSource("ticket_booking", () -> {
        String sql = "SELECT * FROM bookings WHERE " + condition;
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        return formatResults(results);
    });
}
```

## 注意事项

### 1. 内存管理
ThreadLocal 会为每个线程存储数据，在线程池环境中要注意清理上下文。

### 2. 异常处理
确保在异常情况下也能正确清理数据源上下文。

### 3. 嵌套调用
支持嵌套的 executeWithDataSource 调用，内层调用会覆盖外层设置。

### 4. 默认行为
如果没有设置数据源上下文，默认使用分销数据源（ticket_distribution）。

## 最佳实践

1. **优先使用 executeWithDataSource**：自动管理上下文，最安全。
2. **复杂场景使用 try-with-resources**：更直观的资源管理。
3. **避免手动管理**：除非有特殊需求，不要手动 set/clear。
4. **合理使用别名**：使用业务语义强的别名提高代码可读性。
5. **注意线程池环境**：确保任务完成时清理上下文。