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

public class UpdateMessageDateChanged extends Update {

    public static final int HEADER = 0xa3;
    public static UpdateMessageDateChanged fromBytes(byte[] data) throws IOException {
        return Bser.parse(new UpdateMessageDateChanged(), data);
    }

    private Peer peer;
    private long rid;
    private long date;

    public UpdateMessageDateChanged(@NotNull Peer peer, long rid, long date) {
        this.peer = peer;
        this.rid = rid;
        this.date = date;
    }

    public UpdateMessageDateChanged() {

    }

    @NotNull
    public Peer getPeer() {
        return this.peer;
    }

    public long getRid() {
        return this.rid;
    }

    public long getDate() {
        return this.date;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.peer = values.getObj(1, new Peer());
        this.rid = values.getLong(2);
        this.date = values.getLong(3);
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        if (this.peer == null) {
            throw new IOException();
        }
        writer.writeObject(1, this.peer);
        writer.writeLong(2, this.rid);
        writer.writeLong(3, this.date);
    }

    @Override
    public String toString() {
        String res = "update MessageDateChanged{";
        res += "peer=" + this.peer;
        res += ", rid=" + this.rid;
        res += ", date=" + this.date;
        res += "}";
        return res;
    }

    @Override
    public int getHeaderKey() {
        return HEADER;
    }
}
