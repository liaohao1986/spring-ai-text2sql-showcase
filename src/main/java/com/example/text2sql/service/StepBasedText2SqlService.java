package com.example.text2sql.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.text2sql.util.SqlUtils.isSqlSafe;

/**
 * 基于步骤的 Text2SQL 服务
 * 实现5个步骤的结构化输出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepBasedText2SqlService {

    @Qualifier("mcpChatClient")
    private final ChatClient mcpChatClient;
    
    private final BusinessRuleService businessRuleService;

    /**
     * 从资源文件读取提示词内容
     */
    private String loadPromptTemplate(String filePath) {
        try {
			ClassPathResource resource = new ClassPathResource(filePath);
			return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).replaceAll("\\r\\n", "\n") // 统一换行符
					.trim();
        } catch (IOException e) {
            log.error("读取提示词文件失败: {}", filePath, e);
            throw new RuntimeException("无法加载提示词文件: " + filePath, e);
        }
    }

    private static final String SQL_PATTERN = "(SELECT.*?)(?=\\n\\n|$)";
    private static final String SQL_EXTRACTION_FAILED = "无法从内容中提取有效的SQL语句";
    private static final String SQL_UNSAFE_MSG = "生成的 SQL 包含危险操作";

    // 提示词文件路径
    private static final String STEP1_PROMPT_FILE = "prompts/step1-query-rewrite.txt";
    private static final String STEP2_PROMPT_FILE = "prompts/step2-table-selection.txt";
    private static final String STEP3_PROMPT_FILE = "prompts/step3-information-inference.txt";
    private static final String STEP4_PROMPT_FILE = "prompts/step4-sql-generation.txt";
    private static final String STEP5_PROMPT_FILE = "prompts/step5-sql-execution.txt";

    /**
     * 执行步骤的简化方法（无后处理函数）
     */
    private Text2SqlStepResult.StepResult executeStep(int stepNumber,
                                                      String promptTemplate, Map<String, Object> variables) {
        return executeStep(stepNumber, promptTemplate, variables, null);
    }

    /**
     * 执行步骤的通用方法
     */
    private Text2SqlStepResult.StepResult executeStep(int stepNumber, String promptTemplate, Map<String, Object> variables, Function<String, String> function) {
        try {
            System.out.println("执行步骤" + stepNumber);

            PromptTemplate template = new PromptTemplate(promptTemplate);
            String promptText = template.create(variables).getContents();

            String result = mcpChatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();

            if (function != null) {
                result = function.apply(result);
            }

            System.out.println(result);

            return Text2SqlStepResult.StepResult.success(result);
        } catch (Exception e) {
            log.error("步骤{}执行失败", stepNumber, e);
            return Text2SqlStepResult.StepResult.error(e.getMessage());
        }
    }

    /**
     * 处理查询请求，返回5个步骤的结果
     */
    public Text2SqlStepResult processQueryWithSteps(String userQuery) {
        log.info("开始处理步骤化 Text2SQL 查询: {}", userQuery);

        // 步骤1: 问题改写
        Text2SqlStepResult.StepResult step1 = executeStep1(userQuery);
        if (step1.isError()) {
            return Text2SqlStepResult.create(step1, null, null, null, null);
        }

        // 检查步骤1是否判断为数据库查询
        if (isNonDatabaseQuery(step1.getContent())) {
            // 将步骤1标记为失败
            Text2SqlStepResult.StepResult failedStep1 = Text2SqlStepResult.StepResult.error(
                    "非数据库查询，请输入与数据库相关的问题");
            return Text2SqlStepResult.create(failedStep1, null, null, null, null);
        }

        // 步骤2: 数据表选取
        Text2SqlStepResult.StepResult step2 = executeStep2(step1.getContent());
        if (step2.isError()) {
            return Text2SqlStepResult.create(step1, step2, null, null, null);
        }

        // 步骤3: 信息推理
        Text2SqlStepResult.StepResult step3 = executeStep3(step1.getContent(), step2.getContent());
        if (step3.isError()) {
            return Text2SqlStepResult.create(step1, step2, step3, null, null);
        }

        // 步骤4: SQL生成
        Text2SqlStepResult.StepResult step4 = executeStep4(step1.getContent(), step2.getContent(),
                step3.getContent());
        if (step4.isError()) {
            return Text2SqlStepResult.create(step1, step2, step3, step4, null);
        }

        // 步骤5: SQL执行
        Text2SqlStepResult.StepResult step5 = executeStep5(step4.getContent());

        return Text2SqlStepResult.create(step1, step2, step3, step4, step5);

    }

    /**
     * 判断步骤1的结果是否为非数据库查询
     */
    private boolean isNonDatabaseQuery(String step1Content) {
        if (step1Content == null || step1Content.trim().isEmpty()) {
            return true;
        }

        String content = step1Content.trim().toLowerCase();

        // 检查是否包含非数据库查询的提示信息
        return
                content.contains("请输入与数据库查询相关的问题") ||
                        content.contains("当前数据库中没有相关的业务表");
    }

    /**
     * 执行步骤1: 问题改写
     */
    private Text2SqlStepResult.StepResult executeStep1(String userQuery) {
        String prompt = loadPromptTemplate(STEP1_PROMPT_FILE);
        return executeStep(1, prompt, Map.of("userQuery", userQuery));
    }

    /**
     * 执行步骤2: 数据表选取
     */
    private Text2SqlStepResult.StepResult executeStep2(String rewrittenQuery) {
        String prompt = loadPromptTemplate(STEP2_PROMPT_FILE);
        return executeStep(2, prompt, Map.of("rewrittenQuery", rewrittenQuery));
    }

    /**
     * 执行步骤3: 信息推理
     */
    private Text2SqlStepResult.StepResult executeStep3(String rewrittenQuery, String selectedTables) {
        // 生成业务规则参考信息
        String businessRules = generateBusinessRules(rewrittenQuery, selectedTables);
        String prompt = loadPromptTemplate(STEP3_PROMPT_FILE);
        
        return executeStep(3, prompt,
                Map.of("rewrittenQuery", rewrittenQuery, 
                       "selectedTables", selectedTables,
                       "businessRules", businessRules));
    }
    
    /**
     * 生成业务规则参考信息
     */
    private String generateBusinessRules(String query, String selectedTables) {
        StringBuilder rules = new StringBuilder();
        
        // 时间推理 - 简化输出
        String timeLogic = businessRuleService.parseTimeExpression(query);
        if (timeLogic != null && !timeLogic.contains("无法解析")) {
            rules.append("时间范围: ").append(timeLogic.replace("时间范围: ", "")).append("; ");
        }
        
        // 业务逻辑推理 - 简化输出
        String businessLogic = businessRuleService.getBusinessLogic(query, selectedTables);
        if (!businessLogic.isEmpty()) {
            // 提取关键信息，去掉"推理:"等前缀
            String simplifiedLogic = businessLogic.replaceAll("(时间推理|状态推理|排序推理|分组推理|限制推理): ", "").trim();
            if (!simplifiedLogic.isEmpty()) {
                rules.append("业务规则: ").append(simplifiedLogic).append("; ");
            }
        }
        
        // 字段需求推理 - 简化输出
        if (selectedTables != null && !selectedTables.isEmpty()) {
            String[] tables = selectedTables.split(",");
            for (String table : tables) {
                String fieldRequirements = businessRuleService.getFieldRequirements(query, table.trim());
                if (!fieldRequirements.isEmpty()) {
                    // 提取字段名，去掉"需要"等前缀
                    String fields = fieldRequirements.replaceAll("需要", "").replaceAll("字段", "").trim();
                    if (!fields.isEmpty()) {
                        rules.append("关键字段: ").append(fields).append("; ");
                    }
                }
            }
        }
        
        // 表关联规则 - 简化输出
        if (selectedTables != null && selectedTables.contains(",")) {
            String[] tables = selectedTables.split(",");
            if (tables.length >= 2) {
                String joinRule = businessRuleService.getTableJoinRule(tables[0].trim(), tables[1].trim());
                if (!joinRule.isEmpty() && !joinRule.contains("无法确定")) {
                    rules.append("表关联: ").append(joinRule).append("; ");
                }
            }
        }
        
        // 聚合规则推理 - 简化输出
        String aggregationRule = businessRuleService.getAggregationRule(query, selectedTables);
        if (!aggregationRule.isEmpty() && !aggregationRule.contains("未指定指标")) {
            rules.append("聚合方式: ").append(aggregationRule).append("; ");
        }
        
        String result = rules.toString().trim();
        // 去掉最后的分号
        if (result.endsWith("; ")) {
            result = result.substring(0, result.length() - 2);
        }
        
        return result.isEmpty() ? "基于查询需求进行智能分析" : result;
    }

    /**
     * 执行步骤4: SQL生成
     */
    private Text2SqlStepResult.StepResult executeStep4(String rewrittenQuery, String selectedTables,
                                                       String inferenceResult) {
        String prompt = loadPromptTemplate(STEP4_PROMPT_FILE);
        Map<String, Object> variables = Map.of(
                "rewrittenQuery", rewrittenQuery,
                "selectedTables", selectedTables,
                "inferenceResult", inferenceResult
        );
        return executeStep(4, prompt, variables);
    }

    /**
     * 执行步骤5: SQL执行
     */
    private Text2SqlStepResult.StepResult executeStep5(String sqlContent) {
        // 从步骤4的内容中提取SQL语句
        String sql = extractSqlFromContent(sqlContent);
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException(SQL_EXTRACTION_FAILED);
        }

        if (!isSqlSafe(sql)) {
            throw new IllegalArgumentException(SQL_UNSAFE_MSG);
        }

        String prompt = loadPromptTemplate(STEP5_PROMPT_FILE);
        return executeStep(5, prompt, Map.of("sqlQuery", sql));
    }

    /**
     * 从内容中提取SQL语句
     */
    private String extractSqlFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("内容为空，无法提取SQL语句");
            return null;
        }

        try {
            // 查找SQL语句
            Pattern pattern = Pattern.compile(SQL_PATTERN, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                String sql = matcher.group(1).trim();
                // 清理SQL语句
                sql = sql.replaceAll("```sql\\s*", "").replaceAll("```\\s*", "");
                return sql;
            }

            log.warn("无法从内容中提取SQL语句，内容: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
            return null;
        } catch (Exception e) {
            log.error("提取SQL语句时发生错误", e);
            return null;
        }
    }
}
