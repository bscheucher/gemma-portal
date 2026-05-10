package com.gemmaportal.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ConversationRepositorySpec {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.docker.compose.enabled", () -> "false");
    }

    @Autowired
    ConversationRepository conversationRepository;

    @Autowired
    MessageRepository messageRepository;

    @BeforeEach
    void cleanUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Nested
    class WhenFindingBySessionId {

        @Test
        void returnsEmptyWhenNoConversationExists() {
            Optional<Conversation> result = conversationRepository.findBySessionId("unknown-session");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsConversationMatchingSessionId() {
            Conversation saved = conversationRepository.save(
                    Conversation.create("session-abc", "gemma4"));

            Optional<Conversation> result = conversationRepository.findBySessionId("session-abc");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
        }

        @Test
        void doesNotReturnConversationWithDifferentSessionId() {
            conversationRepository.save(Conversation.create("session-1", "gemma4"));

            Optional<Conversation> result = conversationRepository.findBySessionId("session-2");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyAfterConversationIsDeleted() {
            Conversation saved = conversationRepository.save(
                    Conversation.create("session-xyz", "gemma4"));
            conversationRepository.deleteById(saved.getId());

            Optional<Conversation> result = conversationRepository.findBySessionId("session-xyz");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class WhenPersistingConversation {

        @Test
        void assignsUuidOnSave() {
            Conversation conversation = Conversation.create("session-new", "gemma4");
            assertThat(conversation.getId()).isNull();

            Conversation saved = conversationRepository.save(conversation);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void preservesSessionIdModelAndTimestamp() {
            Conversation saved = conversationRepository.save(
                    Conversation.create("session-ts", "gemma4:27b"));

            Conversation loaded = conversationRepository.findById(saved.getId()).orElseThrow();

            assertThat(loaded.getSessionId()).isEqualTo("session-ts");
            assertThat(loaded.getModel()).isEqualTo("gemma4:27b");
            assertThat(loaded.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    class WhenDeletingConversation {

        @Test
        void cascadeDeletesAssociatedMessages() {
            Conversation conversation = conversationRepository.save(
                    Conversation.create("session-del", "gemma4"));
            messageRepository.save(Message.create(conversation, Role.USER, "hello", 0));
            messageRepository.save(Message.create(conversation, Role.ASSISTANT, "hi there", 1));

            conversationRepository.deleteById(conversation.getId());

            assertThat(messageRepository.findByConversationIdOrderBySequenceNumber(conversation.getId())).isEmpty();
        }
    }

    @Nested
    class WhenQueryingMessages {

        @Test
        void returnsMessagesInSequenceOrder() {
            Conversation conversation = conversationRepository.save(
                    Conversation.create("session-ord", "gemma4"));
            messageRepository.save(Message.create(conversation, Role.ASSISTANT, "hi there", 1));
            messageRepository.save(Message.create(conversation, Role.USER, "hello", 0));
            messageRepository.save(Message.create(conversation, Role.USER, "and then?", 2));

            List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumber(conversation.getId());

            assertThat(messages).extracting(Message::getContent)
                    .containsExactly("hello", "hi there", "and then?");
        }

        @Test
        void returnsOnlyMessagesForGivenConversation() {
            Conversation conv1 = conversationRepository.save(Conversation.create("s1", "gemma4"));
            Conversation conv2 = conversationRepository.save(Conversation.create("s2", "gemma4"));
            messageRepository.save(Message.create(conv1, Role.USER, "conv1 message", 0));
            messageRepository.save(Message.create(conv2, Role.USER, "conv2 message", 0));

            List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumber(conv1.getId());

            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getContent()).isEqualTo("conv1 message");
        }

        @Test
        void returnsEmptyListForConversationWithNoMessages() {
            Conversation conversation = conversationRepository.save(
                    Conversation.create("session-empty", "gemma4"));

            List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumber(conversation.getId());

            assertThat(messages).isEmpty();
        }
    }

    @Nested
    class WhenPersistingMessage {

        @Test
        void assignsUuidAndPreservesFields() {
            Conversation conversation = conversationRepository.save(
                    Conversation.create("session-msg", "gemma4"));

            Message saved = messageRepository.save(
                    Message.create(conversation, Role.USER, "test content", 0));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getRole()).isEqualTo(Role.USER);
            assertThat(saved.getContent()).isEqualTo("test content");
            assertThat(saved.getSequenceNumber()).isZero();
            assertThat(saved.getCreatedAt()).isNotNull();
        }

        @Test
        void supportsAllRoles() {
            Conversation conversation = conversationRepository.save(
                    Conversation.create("session-roles", "gemma4"));
            int seq = 0;
            for (Role role : Role.values()) {
                Message saved = messageRepository.save(
                        Message.create(conversation, role, "content for " + role, seq++));
                assertThat(saved.getRole()).isEqualTo(role);
            }
        }

        @Test
        void supportsLongContent() {
            Conversation conversation = conversationRepository.save(
                    Conversation.create("session-long", "gemma4"));
            String longContent = "a".repeat(100_000);

            Message saved = messageRepository.save(
                    Message.create(conversation, Role.ASSISTANT, longContent, 0));

            assertThat(messageRepository.findById(saved.getId()).orElseThrow().getContent())
                    .hasSize(100_000);
        }
    }
}