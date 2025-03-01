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

public class Avatar extends BserObject {

    private AvatarImage smallImage;
    private AvatarImage largeImage;
    private AvatarImage fullImage;

    public Avatar(@Nullable AvatarImage smallImage, @Nullable AvatarImage largeImage, @Nullable AvatarImage fullImage) {
        this.smallImage = smallImage;
        this.largeImage = largeImage;
        this.fullImage = fullImage;
    }

    public Avatar() {

    }

    @Nullable
    public AvatarImage getSmallImage() {
        return this.smallImage;
    }

    @Nullable
    public AvatarImage getLargeImage() {
        return this.largeImage;
    }

    @Nullable
    public AvatarImage getFullImage() {
        return this.fullImage;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.smallImage = values.optObj(1, new AvatarImage());
        this.largeImage = values.optObj(2, new AvatarImage());
        this.fullImage = values.optObj(3, new AvatarImage());
        if (values.hasRemaining()) {
            setUnmappedObjects(values.buildRemaining());
        }
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        if (this.smallImage != null) {
            writer.writeObject(1, this.smallImage);
        }
        if (this.largeImage != null) {
            writer.writeObject(2, this.largeImage);
        }
        if (this.fullImage != null) {
            writer.writeObject(3, this.fullImage);
        }
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
        String res = "struct Avatar{";
        res += "smallImage=" + (this.smallImage != null ? "set":"empty");
        res += ", largeImage=" + (this.largeImage != null ? "set":"empty");
        res += ", fullImage=" + (this.fullImage != null ? "set":"empty");
        res += "}";
        return res;
    }

}
