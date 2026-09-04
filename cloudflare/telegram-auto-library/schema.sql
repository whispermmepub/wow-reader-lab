CREATE TABLE IF NOT EXISTS books (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  channel_id TEXT NOT NULL,
  message_id INTEGER NOT NULL,
  file_id TEXT NOT NULL,
  file_unique_id TEXT NOT NULL,
  file_name TEXT NOT NULL,
  mime_type TEXT,
  file_size INTEGER NOT NULL DEFAULT 0,
  caption TEXT NOT NULL DEFAULT '',
  published_at INTEGER NOT NULL DEFAULT 0,
  downloadable INTEGER NOT NULL DEFAULT 1,
  active INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  UNIQUE(channel_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_books_active_id ON books(active, id);
CREATE INDEX IF NOT EXISTS idx_books_unique_file ON books(file_unique_id);
