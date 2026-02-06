# 数据源常量使用说明

## 概述

`DataSourceRouter` 类中定义了完整的数据源常量，用于避免硬编码字符串，提高代码的可维护性和类型安全性。

## 常量定义

### 数据源名称常量

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `DATASOURCE_TICKET_DISTRIBUTION` | `"ticket_distribution"` | 分销数据源（主数据源） |
| `DATASOURCE_TICKET_BOOKING` | `"ticket_booking"` | 订单数据源 |
| `DATASOURCE_TEXT2SQL_DB` | `"text2sql_db"` | 本地数据源 |
| `DATASOURCE_READ` | `"read"` | 负载均衡数据源 |

### 数据源别名常量

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `ALIAS_DISTRIBUTION` | `"distribution"` | 分销数据源别名 |
| `ALIAS_PRIMARY` | `"primary"` | 主数据源别名 |
| `ALIAS_MASTER` | `"master"` | 主数据源别名 |
| `ALIAS_BOOKING` | `"booking"` | 订单数据源别名 |
| `ALIAS_ORDER` | `"order"` | 订单数据源别名 |
| `ALIAS_TEXT2SQL` | `"text2sql"` | 本地数据源别名 |
| `ALIAS_LOCAL` | `"local"` | 本地数据源别名 |
| `ALIAS_TEST` | `"test"` | 本地数据源别名 |
| `ALIAS_REPLICA` | `"replica"` | 负载均衡别名 |
| `ALIAS_ANY` | `"any"` | 任意数据源别名 |

## 使用方式

### 1. 直接引用常量

```java
// 设置数据源上下文
DataSourceRouter.setDataSource(DataSourceRouter.DATASOURCE_TICKET_BOOKING);

// 使用 executeWithDataSource
DataSourceRouter.executeWithDataSource(DataSourceRouter.DATASOURCE_READ, () -> {
    // 在负载均衡数据源中执行操作
    return databaseTool.getTableNames();
});
```

### 2. 在 DatabaseTool 中的使用

```java
// 默认使用负载均衡数据源
public List<String> getTableNames() {
    return getTableNames(DataSourceRouter.DATASOURCE_READ);
}

// 在指定数据源中获取表结构
public String getTableSchema(String tableName) {
    return getDatabaseSchema(tableName, DataSourceRouter.DATASOURCE_READ);
}
```

### 3. 在控制器中的使用

```java
@PostMapping("/api/query-with-datasource")
public ResponseEntity<Map<String, Object>> processQueryWithDataSource(
        @RequestParam String dataSource) {
    
    // 验证数据源名称
    if (!isValidDataSource(dataSource)) {
        return ResponseEntity.badRequest().body(Map.of("error", "无效的数据源名称"));
    }
    
    // 使用常量进行数据源切换
    String normalizedDataSource = normalizeDataSource(dataSource);
    Text2SqlResult result = mcpText2SqlService.processQueryWithDataSource(
        userQuery, normalizedDataSource);
    
    return ResponseEntity.ok(createResponse(result));
}
```

## 优势

### 1. 类型安全
- IDE 可以提供代码补全和编译时检查
- 减少拼写错误

### 2. 易于维护
- 统一管理数据源名称
- 修改数据源名称只需更新常量定义

### 3. 代码可读性
- 常量名具有明确的语义
- 提高代码的可理解性

### 4. 重构友好
- IDE 支持安全的重构操作
- 影响分析更加准确

## 最佳实践

### 1. 优先使用常量
```java
// 推荐
DataSourceRouter.setDataSource(DataSourceRouter.DATASOURCE_TICKET_BOOKING);

// 不推荐
DataSourceRouter.setDataSource("ticket_booking");
```

### 2. 在配置文件中使用
```yaml
# application.yml
app:
  datasources:
    default: ${DATASOURCE_TICKET_BOOKING}  # 可以通过环境变量覆盖
```

### 3. 枚举化（可选）
如果需要更强的类型安全，可以考虑创建枚举：

```java
public enum DataSourceType {
    TICKET_DISTRIBUTION(DataSourceRouter.DATASOURCE_TICKET_DISTRIBUTION),
    TICKET_BOOKING(DataSourceRouter.DATASOURCE_TICKET_BOOKING),
    TEXT2SQL_DB(DataSourceRouter.DATASOURCE_TEXT2SQL_DB),
    READ(DataSourceRouter.DATASOURCE_READ);
    
    private final String value;
    
    DataSourceType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
```

### 4. 工具方法
```java
public class DataSourceUtils {
    public static boolean isValidDataSource(String dataSource) {
        return dataSource != null && List.of(
            DataSourceRouter.DATASOURCE_TICKET_DISTRIBUTION,
            DataSourceRouter.DATASOURCE_TICKET_BOOKING,
            DataSourceRouter.DATASOURCE_TEXT2SQL_DB,
            DataSourceRouter.DATASOURCE_READ
        ).contains(dataSource);
    }
    
    public static String normalizeDataSource(String input) {
        if (input == null) return DataSourceRouter.DATASOURCE_READ;
        
        return switch (input.toLowerCase()) {
            case "booking", "order" -> DataSourceRouter.DATASOURCE_TICKET_BOOKING;
            case "distribution", "primary" -> DataSourceRouter.DATASOURCE_TICKET_DISTRIBUTION;
            case "text2sql", "local", "test" -> DataSourceRouter.DATASOURCE_TEXT2SQL_DB;
            default -> input;
        };
    }
}
```

## 迁移指南

### 从硬编码迁移到常量

1. **搜索硬编码字符串**：
   ```bash
   grep -r "ticket_distribution\|ticket_booking\|text2sql_db\|read" src/
   ```

2. **替换为常量**：
   ```java
   // 之前
   String ds = "ticket_booking";
   
   // 之后
   String ds = DataSourceRouter.DATASOURCE_TICKET_BOOKING;
   ```

3. **验证功能**：
   - 运行单元测试
   - 验证数据源切换功能
   - 检查日志输出

## 注意事项

1. **常量更新**：如果需要修改数据源名称，只需更新常量定义
2. **向后兼容**：别名常量确保向后兼容性
3. **文档同步**：更新相关文档和注释
4. **测试覆盖**：确保所有常量都有对应的测试用例

## 扩展建议

1. **配置外部化**：考虑将数据源配置外部化到配置文件
2. **动态数据源**：支持运行时动态添加数据源
3. **监控集成**：为每个数据源添加监控指标
4. **故障转移**：实现更智能的故障转移策略