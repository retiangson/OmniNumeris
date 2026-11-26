CREATE TABLE notebook (id TEXT NOT NULL PRIMARY KEY, wrappedKey BLOB NOT NULL);
CREATE TABLE blob_pointer (notebookId TEXT NOT NULL PRIMARY KEY, blobId TEXT NOT NULL, FOREIGN KEY(notebookId) REFERENCES notebook(id) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE UNIQUE INDEX index_blob_pointer_blobId ON blob_pointer(blobId);
