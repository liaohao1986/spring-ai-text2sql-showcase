# Spring Boot 启动错误修复说明

## 问题描述

启动应用时出现以下错误：
```
org.springframework.beans.factory.BeanCreationException: 
dataSource or dataSourceClassName or jdbcUrl is required.
```

## 问题根因

1. **JPA 配置问题**: Spring Boot JPA 自动配置需要一个默认的数据源
2. **数据源配置不匹配**: 配置文件中的前缀与代码中的 Bean 定义不一致
3. **Bean 名称不匹配**: 数据源 Bean 名称与注入时的 Qualifier 不匹配
4. **变量名称不一致**: 字段名称与使用时的变量名不一致

## 修复方案

### 1. 添加默认数据源配置

**application.yml** 中添加默认数据源：
```yaml
spring:
  datasource:
    # JPA 需要的默认数据源
    url: jdbc:mysql://120.133.0.220:4406/ticket_distribution?...
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ticket
    password: dfj2!@dVN8dVLOsd
    # 具名数据源配置
    ticket_distribution:
      # ... 分销数据源配置
    ticket_booking:
      # ... 订单数据源配置
    text2sql_db:
      # ... 本地数据源配置
```

### 2. 修复 MultiDataSourceConfig

**添加主数据源 Bean**：
```java
@Bean
@Primary
@ConfigurationProperties(prefix = "spring.datasource")
public DataSource primaryDataSource() {
    return DataSourceBuilder.create().build();
}

@Bean
@Primary
public JdbcTemplate primaryJdbcTemplate() {
    return new JdbcTemplate(primaryDataSource());
}
```

**修复配置前缀**：
```java
// 修复前缀 (kebab-case -> snake_case)
@ConfigurationProperties(prefix = "spring.datasource.ticket_distribution")  // 正确
@ConfigurationProperties(prefix = "spring.datasource.ticket_booking")        // 正确
@ConfigurationProperties(prefix = "spring.datasource.text2sql_db")      // 正确
```

### 3. 修复 DataSourceRouter 依赖注入

**正确注入具名 Bean**：
```java
@Autowired
public DataSourceRouter(
        @Qualifier("ticketDistributionJdbcTemplate") JdbcTemplate ticketDistributionJdbcTemplate,
        @Qualifier("ticketBookingJdbcTemplate") JdbcTemplate ticketBookingJdbcTemplate,
        @Qualifier("text2sqlDbJdbcTemplate") JdbcTemplate text2sqlDbJdbcTemplate) {
    // 注入正确的具名 Bean
}
```

### 4. 统一变量名称

**字段声明与使用一致**：
```java
// 声明
private final JdbcTemplate text2sqlDbJdbcTemplate;

// 使用时保持一致
case DATASOURCE_TEXT2SQL_DB -> text2sqlDbJdbcTemplate;  // 正确
```

## 关键修复点

### 配置文件修复

| 问题 | 修复前 | 修复后 |
|------|--------|--------|
| 默认数据源 | 无 | 添加 `spring.datasource` 默认配置 |
| 前缀格式 | `ticket-distribution` (kebab-case) | `ticket_distribution` (snake_case) |
| Bean 命名 | `text2sqlJdbcTemplate` | `text2sqlDbJdbcTemplate` |
| 连接池配置 | 缺少 | 添加完整的 HikariCP 配置 |

### 代码修复

| 问题 | 修复前 | 修复后 |
|------|--------|--------|
| 依赖注入 | 无 Qualifier | 添加正确的 `@Qualifier` 注解 |
| 变量使用 | `text2sqlJdbcTemplate` | `text2sqlDbJdbcTemplate` |
| Bean 定义 | 缺少主数据源 | 添加 `primaryDataSource()` 和 `primaryJdbcTemplate()` |

## 验证修复

### 1. 检查 Bean 创建
```bash
# 启动应用查看日志
mvn spring-boot:run

# 检查 Bean 注册情况
curl http://localhost:8080/actuator/beans
```

### 2. 测试数据源连接
```bash
# 测试数据源连接
curl http://localhost:8080/api/test-datasources

# 检查数据源信息
curl http://localhost:8080/api/datasource-info
```

### 3. 验证 JPA 功能
```bash
# 测试 JPA 实体
curl -X POST http://localhost:8080/api/schema
```

## 最佳实践

### 1. 数据源配置规范
- 使用 `snake_case` 格式配置前缀
- 始终提供默认数据源（JPA 需要）
- 保持配置文件和 Bean 定义的一致性

### 2. Bean 命名规范
```java
// 数据源 Bean: {业务名}DataSource
@Bean DataSource ticketDistributionDataSource()

// JdbcTemplate Bean: {业务名}JdbcTemplate  
@Bean(name = "ticketDistributionJdbcTemplate")
```

### 3. 依赖注入规范
```java
// 主数据源可直接注入
@Autowired JdbcTemplate primaryJdbcTemplate;

// 其他数据源使用 Qualifier
@Autowired @Qualifier("ticketBookingJdbcTemplate") JdbcTemplate ticketBookingJdbcTemplate;
```

### 4. 变量命名规范
- 字段声明名称要清晰表达用途
- 使用时要保持名称一致性
- 避免缩写和不明确命名

## 故障排查

### 常见错误和解决方案

1. **"dataSource is required"**
   - 检查是否有默认的 `spring.datasource` 配置
   - 确保 `@Primary` Bean 正确配置

2. **"Bean not found"**
   - 检查 Bean 名称和 Qualifier 是否匹配
   - 验证 Bean 是否正确注册

3. **"Cannot resolve variable"**
   - 检查变量名称拼写
   - 确保字段声明和使用一致

4. **"Connection failed"**
   - 检查数据库连接参数
   - 验证数据库服务状态

## 相关文件

修复涉及的文件：
- `src/main/resources/application.yml` - 数据源配置
- `src/main/java/com/example/text2sql/config/MultiDataSourceConfig.java` - 数据源 Bean 配置
- `src/main/java/com/example/text2sql/config/DataSourceRouter.java` - 数据源路由器

修复完成后，应用应该能够正常启动并提供多数据源功能。