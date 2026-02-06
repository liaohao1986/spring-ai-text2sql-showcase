package com.example.text2sql.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.text2sql.advisor.MyLoggerAdvisor;
import com.example.text2sql.service.tool.DatabaseTool;

/**
 * ChatClient配置类
 *
 * @author liaoh
 * @date 2026/02/06 11:35
 */
@Configuration
public class ChatClientConfig {
	/**
	 * 配置 ChatClient Bean
	 */
	@Bean
	public ChatClient chatClient(OpenAiChatModel chatModel) {
		return ChatClient.builder(chatModel).defaultAdvisors(new SimpleLoggerAdvisor()).build();
	}

	@Bean("mcpChatClient")
	public ChatClient mcpChatClient(ChatClient.Builder chatClientBuilder, DatabaseTool databaseTool) {
		//return chatClientBuilder.defaultAdvisors(new MyLoggerAdvisor(), new ReReadingAdvisor()).defaultTools(databaseTool).build();
		return chatClientBuilder.defaultAdvisors(new MyLoggerAdvisor()).defaultTools(databaseTool).build();
	}
}
