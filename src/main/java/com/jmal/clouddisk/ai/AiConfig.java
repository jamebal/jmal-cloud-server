package com.jmal.clouddisk.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI主配置类，支持多种LLM提供商切换
 *
 * @author jmal
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    private final AiProperties aiProperties;

    @Bean
    @Primary
    public ChatModel chatModel() {
        String provider = aiProperties.getProvider();
        log.info("Initializing AI ChatModel with provider: {}", provider);

        if ("ollama".equalsIgnoreCase(provider)) {
            return createOllamaChatModel();
        } else {
            return createOpenAiChatModel();
        }
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        String provider = aiProperties.getProvider();
        log.info("Initializing AI EmbeddingModel with provider: {}", provider);

        if ("ollama".equalsIgnoreCase(provider)) {
            return createOllamaEmbeddingModel();
        } else {
            return createOpenAiEmbeddingModel();
        }
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiChatModel createOpenAiChatModel() {
        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(aiProperties.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(aiProperties.getModel())
                .temperature(aiProperties.getTemperature())
                .maxTokens(aiProperties.getMaxTokens())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    private OpenAiEmbeddingModel createOpenAiEmbeddingModel() {
        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(aiProperties.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(aiProperties.getEmbeddingModel())
                .dimensions(aiProperties.getVectorDimension())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, options);
    }

    private OllamaChatModel createOllamaChatModel() {
        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }

        OllamaApi ollamaApi = new OllamaApi(baseUrl);

        OllamaOptions options = OllamaOptions.builder()
                .model(aiProperties.getModel())
                .temperature(aiProperties.getTemperature())
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }

    private OllamaEmbeddingModel createOllamaEmbeddingModel() {
        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }

        OllamaApi ollamaApi = new OllamaApi(baseUrl);

        OllamaOptions options = OllamaOptions.builder()
                .model(aiProperties.getEmbeddingModel())
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }
}
