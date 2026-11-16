package com.vku.rmi.server;

import com.vku.rmi.db.ServerDatabase;
import com.vku.rmi.model.DataRecord;
import com.vku.rmi.model.ServerInfo;
import com.vku.rmi.model.SyncEvent;
import com.vku.rmi.service.IDataSyncService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class DataSyncServiceImpl extends UnicastRemoteObject implements IDataSyncService {
    private static final long serialVersionUID = 1L;

    private final RmiSyncServer server;
    private final ServerDatabase db;

    public DataSyncServiceImpl(RmiSyncServer server, ServerDatabase db, int port) throws RemoteException {
        super(port);
        this.server = server;
        this.db = db;
    }

    @Override
    public boolean syncCreate(DataRecord record, String originServerId) {
        return apply(SyncEvent.Type.CREATE, record, originServerId);
    }

    @Override
    public boolean syncUpdate(DataRecord record, String originServerId) {
        return apply(SyncEvent.Type.UPDATE, record, originServerId);
    }

    @Override
    public boolean syncDelete(DataRecord record, String originServerId) {
        return apply(SyncEvent.Type.DELETE, record, originServerId);
    }

    private boolean apply(SyncEvent.Type type, DataRecord record, String originServerId) {
        if (!db.applyReplication(record)) return false;
        server.log("[RMI NHẬN] " + type + " " + record.getId() + " từ " + originServerId);
        db.recordEvent(new SyncEvent(type, record.getId(), originServerId, server.getServerId(), "Nhận qua RMI"));
        return true;
    }

    @Override
    public List<DataRecord> syncAll(long lastSyncTimestamp) {
        List<DataRecord> list = new ArrayList<>();
        for (DataRecord record : db.getReplicationRecords()) {
            if (record.getLastModified() >= lastSyncTimestamp) list.add(record);
        }
        return list;
    }

    @Override
    public int mergeRecords(List<DataRecord> records, String originServerId) {
        int merged = 0;
        for (DataRecord record : records) {
            if (db.applyReplication(record)) merged++;
        }
        if (merged > 0) {
            server.log("[RMI NHẬN] Đồng bộ bù " + merged + " thay đổi từ " + originServerId);
            db.recordEvent(new SyncEvent(SyncEvent.Type.FULL_SYNC, "ALL", originServerId,
                    server.getServerId(), "Áp dụng " + merged + " thay đổi"));
        }
        return merged;
    }

    @Override
    public boolean heartbeat(String senderServerId) {
        return true;
    }

    @Override
    public ServerInfo getServerInfo() {
        return server.buildServerInfo();
    }
}
