package com.event_booking_app.ai_service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eventmate.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FaqIngestionRunner implements ApplicationRunner {

    private static final String FAQ_RESOURCE_PATTERN = "classpath*:/faq/**/*.md";
    private static final int MAX_CHUNK_CHARS = 1200;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<Document> documents = loadFaqDocuments();
        if (documents.isEmpty()) {
            log.warn("No FAQ Markdown files found at {}", FAQ_RESOURCE_PATTERN);
            return;
        }

        // This vector table is dedicated to FAQ content. Delete only FAQ rows,
        // then rebuild them from the current version-controlled Markdown files.
        jdbcTemplate.update("DELETE FROM faq_vector_store WHERE metadata ->> 'source_type' = 'faq'");
        vectorStore.add(documents);
        log.info("Indexed {} FAQ chunks from Markdown resources", documents.size());
    }

    private List<Document> loadFaqDocuments() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(FAQ_RESOURCE_PATTERN);
        List<Document> documents = new ArrayList<>();

        for (Resource resource : resources) {
            String markdown;
            try (var input = resource.getInputStream()) {
                markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            String source = resource.getFilename() == null ? "unknown.md" : resource.getFilename();

            List<String> chunks = chunkMarkdown(markdown);
            for (int index = 0; index < chunks.size(); index++) {
                String content = chunks.get(index).trim();
                if (content.isBlank()) continue;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source_type", "faq");
                metadata.put("source", source);
                metadata.put("chunk", index);
                metadata.put("title", titleFrom(markdown, source));
                metadata.put("embedding_model", "nomic-embed-text");

                String stableKey = source + ":" + index + ":" + content;
                String id = UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8)).toString();
                documents.add(new Document(id, content, metadata));
            }
        }
        return documents;
    }

    // Split at paragraph boundaries first; split exceptionally long paragraphs
    // at a nearby word boundary so each embedding input stays bounded.
    private List<String> chunkMarkdown(String markdown) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawParagraph : markdown.split("\\R\\s*\\R")) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isEmpty()) continue;

            if (paragraph.length() > MAX_CHUNK_CHARS) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                splitLongParagraph(paragraph, chunks);
                continue;
            }

            if (!current.isEmpty() && current.length() + paragraph.length() + 2 > MAX_CHUNK_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(paragraph);
        }

        if (!current.isEmpty()) chunks.add(current.toString());
        return chunks;
    }

    private void splitLongParagraph(String paragraph, List<String> chunks) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, paragraph.length());
            if (end < paragraph.length()) {
                int boundary = paragraph.lastIndexOf(' ', end);
                if (boundary > start) end = boundary;
            }
            chunks.add(paragraph.substring(start, end).trim());
            start = end;
            while (start < paragraph.length() && paragraph.charAt(start) == ' ') start++;
        }
    }

    private String titleFrom(String markdown, String fallback) {
        return markdown.lines()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).trim())
                .orElse(fallback);
    }
}
