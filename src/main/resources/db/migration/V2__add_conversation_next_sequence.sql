ALTER TABLE conversations
    ADD COLUMN next_sequence INTEGER NOT NULL DEFAULT 0;

UPDATE conversations c
   SET next_sequence = (
       SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id
   );
