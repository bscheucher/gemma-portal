package com.gemmaportal.memory;

import com.gemmaportal.dto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ChatMemoryServiceSpec {

    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private ChatMemoryService service;

    private static final String SESSION_ID = "test-session-id";
    private static final String MODEL = "gemma4";

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        service = new ChatMemoryService(conversationRepository, messageRepository);
    }

    @Nested
    class WhenGettingOrCreatingConversation {

        @Test
        void createsNewConversationForUnknownSession() {
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Conversation result = service.getOrCreateConversation(SESSION_ID, MODEL);

            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(result.getModel()).isEqualTo(MODEL);
            verify(conversationRepository).save(any(Conversation.class));
        }

        @Test
        void returnsExistingConversationForKnownSession() {
            Conversation existing = Conversation.create(SESSION_ID, MODEL);
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(existing));

            Conversation result = service.getOrCreateConversation(SESSION_ID, MODEL);

            assertThat(result).isSameAs(existing);
            verify(conversationRepository, never()).save(any());
        }

        @Test
        void doesNotSaveWhenConversationAlreadyExists() {
            when(conversationRepository.findBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(Conversation.create(SESSION_ID, MODEL)));

            service.getOrCreateConversation(SESSION_ID, MODEL);

            verify(conversationRepository, never()).save(any());
        }
    }

    @Nested
    class WhenAppendingMessages {

        private UUID conversationId;

        @BeforeEach
        void setUp() {
            conversationId = UUID.randomUUID();
            when(conversationRepository.getReferenceById(conversationId))
                    .thenReturn(Conversation.create(SESSION_ID, MODEL));
        }

        @Test
        void persistsMessageWithCorrectRole() {
            when(conversationRepository.reserveNextSequence(conversationId)).thenReturn(0);
            when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Message result = service.appendMessage(conversationId, Role.USER, "hello");

            assertThat(result.getRole()).isEqualTo(Role.USER);
            assertThat(result.getContent()).isEqualTo("hello");
        }

        @Test
        void usesSequenceNumberReservedByRepository() {
            when(conversationRepository.reserveNextSequence(conversationId)).thenReturn(3);
            when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.appendMessage(conversationId, Role.USER, "new message");

            verify(messageRepository).save(argThat(m -> m.getSequenceNumber() == 3));
        }

        @Test
        void sequenceNumberStartsAtZeroForFirstMessage() {
            when(conversationRepository.reserveNextSequence(conversationId)).thenReturn(0);
            when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.appendMessage(conversationId, Role.USER, "first");

            verify(messageRepository).save(argThat(m -> m.getSequenceNumber() == 0));
        }
    }

    @Nested
    class WhenGettingHistory {

        @Test
        void returnsEmptyListForSessionWithNoConversation() {
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            List<ChatMessage> history = service.getHistory(SESSION_ID);

            assertThat(history).isEmpty();
        }

        @Test
        void returnsMessagesAsChatMessageDtos() {
            Conversation conversation = Conversation.create(SESSION_ID, MODEL);
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(conversation));
            when(messageRepository.findByConversationIdOrderBySequenceNumber(any()))
                    .thenReturn(List.of(
                            Message.create(conversation, Role.USER, "hello", 0),
                            Message.create(conversation, Role.ASSISTANT, "hi there", 1)));

            List<ChatMessage> history = service.getHistory(SESSION_ID);

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getRole()).isEqualTo("user");
            assertThat(history.get(0).getContent()).isEqualTo("hello");
            assertThat(history.get(1).getRole()).isEqualTo("assistant");
            assertThat(history.get(1).getContent()).isEqualTo("hi there");
        }

        @Test
        void preservesChronologicalMessageOrder() {
            Conversation conversation = Conversation.create(SESSION_ID, MODEL);
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(conversation));
            when(messageRepository.findByConversationIdOrderBySequenceNumber(any()))
                    .thenReturn(List.of(
                            Message.create(conversation, Role.USER, "first", 0),
                            Message.create(conversation, Role.ASSISTANT, "second", 1),
                            Message.create(conversation, Role.USER, "third", 2)));

            List<ChatMessage> history = service.getHistory(SESSION_ID);

            assertThat(history).extracting(ChatMessage::getContent)
                    .containsExactly("first", "second", "third");
        }

        @Test
        void eachCallReturnsANewListInstance() {
            Conversation conversation = Conversation.create(SESSION_ID, MODEL);
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(conversation));
            when(messageRepository.findByConversationIdOrderBySequenceNumber(any()))
                    .thenReturn(List.of(Message.create(conversation, Role.USER, "hello", 0)));

            List<ChatMessage> first = service.getHistory(SESSION_ID);
            List<ChatMessage> second = service.getHistory(SESSION_ID);

            assertThat(first).isNotSameAs(second);
        }
    }

    @Nested
    class WhenClearingHistory {

        @Test
        void deletesConversationForSession() {
            Conversation conversation = Conversation.create(SESSION_ID, MODEL);
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(conversation));

            service.clearHistory(SESSION_ID);

            verify(conversationRepository).delete(conversation);
        }

        @Test
        void isIdempotentWhenNoConversationExists() {
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            service.clearHistory(SESSION_ID);

            verify(conversationRepository, never()).delete(any());
        }

        @Test
        void subsequentHistoryCallReturnsEmpty() {
            when(conversationRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            service.clearHistory(SESSION_ID);
            List<ChatMessage> history = service.getHistory(SESSION_ID);

            assertThat(history).isEmpty();
        }
    }

    @Nested
    class WhenMappingRoles {

        @Test
        void userRoleMapsToLowercaseString() {
            assertThat(service.roleToString(Role.USER)).isEqualTo("user");
        }

        @Test
        void assistantRoleMapsToLowercaseString() {
            assertThat(service.roleToString(Role.ASSISTANT)).isEqualTo("assistant");
        }

        @Test
        void systemRoleMapsToLowercaseString() {
            assertThat(service.roleToString(Role.SYSTEM)).isEqualTo("system");
        }
    }
}