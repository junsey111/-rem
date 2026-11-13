ALTER TABLE user_moment
  ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE AFTER visible,
  ADD KEY moment_owner_hidden_page (user_id, visible, hidden, id);
