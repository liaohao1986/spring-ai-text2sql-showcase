package com.example.text2sql.config;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据源路由器 用于管理和选择不同的数据源
 */
@Slf4j
@Component
public class DataSourceRouter {

	// 数据源名称常量
	public static final String DATASOURCE_TICKET_DISTRIBUTION = "ticket-distribution";
	public static final String DATASOURCE_TICKET_BOOKING = "ticket-booking";
	public static final String DATASOURCE_TEXT2SQL_DB = "text2sql-db";
	public static final String DATASOURCE_READ = "read";

	// 数据源别名常量
	public static final String ALIAS_DISTRIBUTION = "distribution";
	public static final String ALIAS_PRIMARY = "primary";
	public static final String ALIAS_MASTER = "master";
	public static final String ALIAS_BOOKING = "booking";
	public static final String ALIAS_ORDER = "order";
	public static final String ALIAS_TEXT2SQL = "text2sql";
	public static final String ALIAS_LOCAL = "local";
	public static final String ALIAS_TEST = "test";
	public static final String ALIAS_REPLICA = "replica";
	public static final String ALIAS_ANY = "any";

	private final JdbcTemplate ticketDistributionJdbcTemplate;
	private final JdbcTemplate ticketBookingJdbcTemplate;
	private final JdbcTemplate text2sqlDbJdbcTemplate;

	// ThreadLocal 用于存储当前线程的数据源名称
	private static final ThreadLocal<String> DATASOURCE_CONTEXT = new ThreadLocal<>();

	@Autowired
	public DataSourceRouter(@Qualifier("ticketDistributionJdbcTemplate") JdbcTemplate ticketDistributionJdbcTemplate,
			@Qualifier("ticketBookingJdbcTemplate") JdbcTemplate ticketBookingJdbcTemplate, @Qualifier("text2sqlDbJdbcTemplate") JdbcTemplate text2sqlDbJdbcTemplate) {
		this.ticketDistributionJdbcTemplate = ticketDistributionJdbcTemplate;
		this.ticketBookingJdbcTemplate = ticketBookingJdbcTemplate;
		this.text2sqlDbJdbcTemplate = text2sqlDbJdbcTemplate;
	}

	/**
	 * 设置当前线程的数据源名称
	 */
	public static void setDataSource(String dataSourceName) {
		dataSourceName = StringUtils.defaultIfBlank(dataSourceName, DATASOURCE_TEXT2SQL_DB);
		log.info("查询数据源：{}", dataSourceName);
		DATASOURCE_CONTEXT.set(dataSourceName);
	}

	/**
	 * 获取当前线程的数据源名称
	 */
	public static String getDataSource() {
		return DATASOURCE_CONTEXT.get();
	}

	/**
	 * 清除当前线程的数据源名称
	 */
	public static void clearDataSource() {
		DATASOURCE_CONTEXT.remove();
	}

	/**
	 * 获取分销数据源 (主数据源，用于写操作)
	 */
	public JdbcTemplate getPrimaryDataSource() {
		return ticketDistributionJdbcTemplate;
	}

	/**
	 * 获取读数据源 (用于读操作，简单轮询)
	 */
	public JdbcTemplate getReadDataSource() {
		// 简单的轮询策略，实际项目中可以使用更复杂的负载均衡策略
		int hash = (int) (System.currentTimeMillis() % 3);
		return switch (hash) {
		case 0 -> ticketDistributionJdbcTemplate;
		case 1 -> ticketBookingJdbcTemplate;
		default -> text2sqlDbJdbcTemplate;
		};
	}

	/**
	 * 获取当前线程的数据源 (从ThreadLocal获取)
	 */
	public JdbcTemplate getCurrentDataSource() {
		String dataSourceName = DATASOURCE_CONTEXT.get();
		log.info("dataSourceName：{}", dataSourceName);
		if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
			// 如果没有设置数据源，默认使用分销数据源
			return ticketDistributionJdbcTemplate;
		}
		return getDataSourceByNameInternal(dataSourceName);
	}

	/**
	 * 根据数据源名称获取对应的 JdbcTemplate (内部方法)
	 */
	public JdbcTemplate getDataSourceByName(String dataSourceName) {
		return getDataSourceByNameInternal(dataSourceName);
	}

	/**
	 * 根据数据源名称获取对应的 JdbcTemplate (实现方法)
	 */
	private JdbcTemplate getDataSourceByNameInternal(String dataSourceName) {
		if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
			log.warn("数据源名称为空，使用分销数据源");
			return ticketDistributionJdbcTemplate;
		}

		String normalizedDataSourceName = dataSourceName.toLowerCase();

		return switch (normalizedDataSourceName) {
		// 分销数据源及其别名
		case DATASOURCE_TICKET_DISTRIBUTION -> ticketDistributionJdbcTemplate;

		// 订单数据源及其别名
		case DATASOURCE_TICKET_BOOKING -> ticketBookingJdbcTemplate;

		// 本地数据源及其别名
		case DATASOURCE_TEXT2SQL_DB -> text2sqlDbJdbcTemplate;

		default -> {
			log.warn("未知的数据库源名称: {}, 使用分销数据源", dataSourceName);
			yield ticketDistributionJdbcTemplate;
		}
		};
	}

	/**
	 * 测试所有数据源的连接状态
	 */
	public boolean testAllConnections() {
		boolean allConnected = true;

		try {
			ticketDistributionJdbcTemplate.queryForObject("SELECT 1", Integer.class);
			log.info("分销数据源连接正常");
		} catch (Exception e) {
			log.error("分销数据源连接失败", e);
			allConnected = false;
		}

		try {
			ticketBookingJdbcTemplate.queryForObject("SELECT 1", Integer.class);
			log.info("订单数据源连接正常");
		} catch (Exception e) {
			log.error("订单数据源连接失败", e);
			allConnected = false;
		}

		try {
			text2sqlDbJdbcTemplate.queryForObject("SELECT 1", Integer.class);
			log.info("本地数据源连接正常");
		} catch (Exception e) {
			log.error("本地数据源连接失败", e);
			allConnected = false;
		}

		return allConnected;
	}

	/**
	 * 在指定数据源上下文中执行操作
	 */
	public static <T> T executeWithDataSource(String dataSourceName, DataSourceCallback<T> callback) {
		String originalDataSource = DATASOURCE_CONTEXT.get();
		try {
			DATASOURCE_CONTEXT.set(dataSourceName);
			return callback.execute();
		} finally {
			if (originalDataSource != null) {
				DATASOURCE_CONTEXT.set(originalDataSource);
			} else {
				DATASOURCE_CONTEXT.remove();
			}
		}
	}

	/**
	 * 数据源操作回调接口
	 */
	@FunctionalInterface
	public interface DataSourceCallback<T> {
		T execute();
	}

	/**
	 * 创建数据源上下文，用于 try-with-resources 风格的使用
	 */
	public static DataSourceContext withDataSource(String dataSourceName) {
		return new DataSourceContext(dataSourceName);
	}

	/**
	 * 数据源上下文类，支持 try-with-resources
	 */
	public static class DataSourceContext implements AutoCloseable {
		private final String originalDataSource;

		public DataSourceContext(String dataSourceName) {
			this.originalDataSource = DATASOURCE_CONTEXT.get();
			DATASOURCE_CONTEXT.set(dataSourceName);
		}

		@Override
		public void close() {
			if (originalDataSource != null) {
				DATASOURCE_CONTEXT.set(originalDataSource);
			} else {
				DATASOURCE_CONTEXT.remove();
			}
		}
	}
}