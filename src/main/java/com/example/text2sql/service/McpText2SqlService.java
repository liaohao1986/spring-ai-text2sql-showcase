package com.example.text2sql.service;

import static com.example.text2sql.util.SqlUtils.cleanSql;
import static com.example.text2sql.util.SqlUtils.isSqlSafe;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.example.text2sql.service.tool.DatabaseTool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpText2SqlService implements Text2SqlService {

    @Qualifier("mcpChatClient")
    private final ChatClient mcpChatClient;

    private final DatabaseTool databaseTool;

    /**
     * 将自然语言转换为 SQL 并执行查询
     *
     * @param userQuery 用户自然语言查询
     * @return 查询结果
     */
    @Override
    public Text2SqlResult processQuery(String userQuery) {
        return processQueryWithTableNames(userQuery, null);
    }

    /**
     * 将自然语言转换为 SQL 并执行查询，支持指定表名
     *
     * @param userQuery  用户自然语言查询
     * @param tableNames 指定的数据库表名
     * @return 查询结果
     */
    @Override
    public Text2SqlResult processQueryWithTableNames(String userQuery, String tableNames) {
        try {
            // 1. 验证输入
            if (userQuery == null || userQuery.trim().isEmpty()) {
                return Text2SqlResult.error("查询不能为空");
            }

            if (tableNames != null && !tableNames.trim().isEmpty()) {
                log.info("开始处理 MCP Text2SQL 查询: {}", userQuery);
                log.info("指定表名: {}", tableNames);
            } else {
                log.info("开始处理 MCP Text2SQL 查询: {}", userQuery);
            }

            // 2. 使用 MCP 工具生成 SQL
            String sql = generateSqlWithMcpTools(userQuery, tableNames);

            if (sql == null || sql.trim().isEmpty()) {
                return Text2SqlResult.error("无法生成有效的 SQL 查询");
            }

            // 3. 验证 SQL 安全性
            if (!isSqlSafe(sql)) {
                //return Text2SqlResult.error("生成的 SQL 不安全，包含危险操作");
            }

            // 4. 执行查询
            List<Map<String, Object>> results = executeQuery(sql);

            log.info("MCP Text2SQL 查询完成，返回 {} 条记录", results.size());

            return Text2SqlResult.success(sql, results);

        } catch (Exception e) {
            log.error("MCP Text2SQL 处理失败", e);
            return Text2SqlResult.error("处理查询时发生错误: " + e.getMessage());
        }
    }

    /**
     * 从文件加载提示词模板
     */
    private String loadPromptTemplate(String fileName) throws Exception {
        ClassPathResource resource = new ClassPathResource("prompts/" + fileName);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * 使用 MCP 工具生成 SQL
     */
    private String generateSqlWithMcpTools(String userQuery, String tableNames) {
        try {
            String promptTemplateStr;
            PromptTemplate promptTemplate;
            Prompt prompt;

            if (tableNames != null && !tableNames.trim().isEmpty()) {
                promptTemplateStr = loadPromptTemplate("sql-generation-with-tables-prompt.txt");
                promptTemplate = new PromptTemplate(promptTemplateStr);
                prompt = promptTemplate.create(Map.of("userQuery", userQuery, "tableNames", tableNames));
                log.info("tableNames: {}", tableNames);
            } else {
                promptTemplateStr = loadPromptTemplate("sql-generation-prompt.txt");
                promptTemplate = new PromptTemplate(promptTemplateStr);
                prompt = promptTemplate.create(Map.of("userQuery", userQuery));
            }

            ChatResponse response = mcpChatClient.prompt(prompt).call().chatResponse();
            String sql = response.getResult().getOutput().getText();

            // 清理 SQL 语句，移除可能的解释文本
            sql = cleanSql(sql);

            log.info("MCP 工具生成的 SQL: {}", sql);
            return sql;

        } catch (Exception e) {
            log.error("使用 MCP 工具生成 SQL 失败", e);
            return null;
        }
    }

    /**
     * 直接调用 DatabaseTool 执行查询，避免 LLM JSON 解析问题
     */
    private List<Map<String, Object>> executeQuery(String sql) {
        log.info("执行 SQL 查询: {}", sql);
        
        // 直接调用 DatabaseTool 执行查询
        List<Map<String, Object>> result = databaseTool.executeQuery(sql);

        log.info("查询执行完成，返回 {} 条记录", result.size());
        return result;
    }

}