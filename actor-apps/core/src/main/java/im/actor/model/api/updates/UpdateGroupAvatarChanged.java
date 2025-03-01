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

public class UpdateGroupAvatarChanged extends Update {

    public static final int HEADER = 0x27;
    public static UpdateGroupAvatarChanged fromBytes(byte[] data) throws IOException {
        return Bser.parse(new UpdateGroupAvatarChanged(), data);
    }

    private int groupId;
    private long rid;
    private int uid;
    private Avatar avatar;
    private long date;

    public UpdateGroupAvatarChanged(int groupId, long rid, int uid, @Nullable Avatar avatar, long date) {
        this.groupId = groupId;
        this.rid = rid;
        this.uid = uid;
        this.avatar = avatar;
        this.date = date;
    }

    public UpdateGroupAvatarChanged() {

    }

    public int getGroupId() {
        return this.groupId;
    }

    public long getRid() {
        return this.rid;
    }

    public int getUid() {
        return this.uid;
    }

    @Nullable
    public Avatar getAvatar() {
        return this.avatar;
    }

    public long getDate() {
        return this.date;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.groupId = values.getInt(1);
        this.rid = values.getLong(5);
        this.uid = values.getInt(2);
        this.avatar = values.optObj(3, new Avatar());
        this.date = values.getLong(4);
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        writer.writeInt(1, this.groupId);
        writer.writeLong(5, this.rid);
        writer.writeInt(2, this.uid);
        if (this.avatar != null) {
            writer.writeObject(3, this.avatar);
        }
        writer.writeLong(4, this.date);
    }

    @Override
    public String toString() {
        String res = "update GroupAvatarChanged{";
        res += "groupId=" + this.groupId;
        res += ", rid=" + this.rid;
        res += ", uid=" + this.uid;
        res += ", avatar=" + (this.avatar != null ? "set":"empty");
        res += ", date=" + this.date;
        res += "}";
        return res;
    }

    @Override
    public int getHeaderKey() {
        return HEADER;
    }
}
