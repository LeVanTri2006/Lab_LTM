package com.vku.rmi.server;

import com.vku.rmi.db.ServerDatabase;
import com.vku.rmi.model.DataRecord;
import com.vku.rmi.model.ServerInfo;
import com.vku.rmi.model.SyncEvent;
import com.vku.rmi.service.IClientService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class ClientServiceImpl extends UnicastRemoteObject implements IClientService {
    private static final long serialVersionUID = 1L;

    private final RmiSyncServer server;
    private final ServerDatabase db;

    public ClientServiceImpl(RmiSyncServer server, ServerDatabase db, int port) throws RemoteException {
        super(port);
        this.server = server;
        this.db = db;
    }

    @Override
    public List<DataRecord> getAllRecords() {
        return db.getAllRecords();
    }

    @Override
    public DataRecord getRecordById(String id) {
        return db.getRecord(id);
    }

    @Override
    public boolean createRecord(DataRecord record) {
        return replicate(SyncEvent.Type.CREATE, db.createRecord(record));
    }

    @Override
    public boolean updateRecord(DataRecord record) {
        return replicate(SyncEvent.Type.UPDATE, db.updateRecord(record));
    }

    @Override
    public boolean deleteRecord(String id) {
        return replicate(SyncEvent.Type.DELETE, db.deleteRecordLocal(id));
    }

    private boolean replicate(SyncEvent.Type type, DataRecord record) {
        if (record == null) return false;
        server.log("[CLIENT] " + type + " " + record.getId());
        server.replicate(type, record);
        return true;
    }

    @Override
    public String getServerId() {
        return server.getServerId();
    }

    @Override
    public ServerInfo getServerInfo() {
        return server.buildServerInfo();
    }
}
