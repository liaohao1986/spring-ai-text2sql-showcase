package com.example.text2sql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 多数据源配置类
 */
@Configuration
public class MultiDataSourceConfig {

    /**
     * 主数据源配置 (JPA 需要这个作为默认数据源)
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 分销数据源配置
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.ticket-distribution")
    public DataSource ticketDistributionDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 订单数据源配置
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.ticket-booking")
    public DataSource ticketBookingDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 本地数据源配置
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.text2sql-db")
    public DataSource text2sqlDbDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 主数据源 JdbcTemplate (默认数据源，JPA 使用)
     */
    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate() {
        return new JdbcTemplate(primaryDataSource());
    }

    /**
     * 分销数据源 JdbcTemplate
     */
    @Bean(name = "ticketDistributionJdbcTemplate")
    public JdbcTemplate ticketDistributionJdbcTemplate() {
        return new JdbcTemplate(ticketDistributionDataSource());
    }

    /**
     * 订单数据源 JdbcTemplate
     */
    @Bean(name = "ticketBookingJdbcTemplate")
    public JdbcTemplate ticketBookingJdbcTemplate() {
        return new JdbcTemplate(ticketBookingDataSource());
    }

    /**
     * 本地数据源 JdbcTemplate
     */
    @Bean(name = "text2sqlDbJdbcTemplate")
    public JdbcTemplate text2sqlDbJdbcTemplate() {
        return new JdbcTemplate(text2sqlDbDataSource());
    }
}