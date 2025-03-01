package im.actor.model.api;
/*
 *  Generated by the Actor API Scheme generator.  DO NOT EDIT!
 */

import im.actor.model.droidkit.bser.BserObject;
import im.actor.model.droidkit.bser.BserValues;
import im.actor.model.droidkit.bser.BserWriter;
import im.actor.model.droidkit.bser.util.SparseArray;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class FileLocation extends BserObject {

    private long fileId;
    private long accessHash;

    public FileLocation(long fileId, long accessHash) {
        this.fileId = fileId;
        this.accessHash = accessHash;
    }

    public FileLocation() {

    }

    public long getFileId() {
        return this.fileId;
    }

    public long getAccessHash() {
        return this.accessHash;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.fileId = values.getLong(1);
        this.accessHash = values.getLong(2);
        if (values.hasRemaining()) {
            setUnmappedObjects(values.buildRemaining());
        }
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        writer.writeLong(1, this.fileId);
        writer.writeLong(2, this.accessHash);
        if (this.getUnmappedObjects() != null) {
            SparseArray<Object> unmapped = this.getUnmappedObjects();
            for (int i = 0; i < unmapped.size(); i++) {
                int key = unmapped.keyAt(i);
                writer.writeUnmapped(key, unmapped.get(key));
            }
        }
    }

    @Override
    public String toString() {
        String res = "struct FileLocation{";
        res += "fileId=" + this.fileId;
        res += "}";
        return res;
    }

}
