package im.actor.model.api.updates;
/*
 *  Generated by the Actor API Scheme generator.  DO NOT EDIT!
 */

import im.actor.model.droidkit.bser.Bser;
import im.actor.model.droidkit.bser.BserValues;
import im.actor.model.droidkit.bser.BserWriter;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import im.actor.model.network.parser.*;
import im.actor.model.api.*;

public class UpdateUserAvatarChanged extends Update {

    public static final int HEADER = 0x10;
    public static UpdateUserAvatarChanged fromBytes(byte[] data) throws IOException {
        return Bser.parse(new UpdateUserAvatarChanged(), data);
    }

    private int uid;
    private Avatar avatar;

    public UpdateUserAvatarChanged(int uid, @Nullable Avatar avatar) {
        this.uid = uid;
        this.avatar = avatar;
    }

    public UpdateUserAvatarChanged() {

    }

    public int getUid() {
        return this.uid;
    }

    @Nullable
    public Avatar getAvatar() {
        return this.avatar;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.uid = values.getInt(1);
        this.avatar = values.optObj(2, new Avatar());
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        writer.writeInt(1, this.uid);
        if (this.avatar != null) {
            writer.writeObject(2, this.avatar);
        }
    }

    @Override
    public String toString() {
        String res = "update UserAvatarChanged{";
        res += "uid=" + this.uid;
        res += ", avatar=" + (this.avatar != null ? "set":"empty");
        res += "}";
        return res;
    }

    @Override
    public int getHeaderKey() {
        return HEADER;
    }
}
