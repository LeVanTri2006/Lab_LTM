package com.vku.rmi.server;

import com.vku.rmi.db.ServerDatabase;
import com.vku.rmi.model.DataRecord;
import com.vku.rmi.model.ServerInfo;
import com.vku.rmi.model.SyncEvent;
import com.vku.rmi.service.IDataSyncService;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RmiSyncServer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String serverId;
    private final String host;
    private final int port;
    private final String partnerHost;
    private final int partnerPort;
    private final String partnerServerId;
    private final ServerDatabase database;
    private final long startTime = System.currentTimeMillis();

    private final ScheduledExecutorService replication = Executors.newSingleThreadScheduledExecutor();
    private Registry registry;
    private IDataSyncService partner;
    private volatile boolean partnerConnected;

    public RmiSyncServer(String serverId, String host, int port, String partnerHost, int partnerPort, String partnerId) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.partnerHost = partnerHost;
        this.partnerPort = partnerPort;
        this.partnerServerId = partnerId;
        this.database = new ServerDatabase(serverId, "data_" + serverId.toLowerCase(Locale.ROOT));
    }

    public void start() throws RemoteException {
        System.setProperty("java.rmi.server.hostname", host);
        registry = LocateRegistry.createRegistry(port);
        registry.rebind("ClientService", new ClientServiceImpl(this, database, port));
        registry.rebind("DataSyncService", new DataSyncServiceImpl(this, database, port));

        log("RMI đang lắng nghe tại " + host + ":" + port);
        log("Đối tác " + partnerServerId + " tại " + partnerHost + ":" + partnerPort);
        log("Lưu trữ " + storage() + " | " + database.getRecordCount() + " bản ghi");
        replication.scheduleWithFixedDelay(this::checkPartner, 0, 3, TimeUnit.SECONDS);
    }

    private void checkPartner() {
        try {
            Registry partnerRegistry = LocateRegistry.getRegistry(partnerHost, partnerPort);
            IDataSyncService currentPartner = (IDataSyncService) partnerRegistry.lookup("DataSyncService");
            if (!currentPartner.equals(partner)) {
                partnerConnected = false;
                if (!partnerServerId.equals(currentPartner.getServerInfo().getServerId())) {
                    throw new RemoteException("Sai định danh server đối tác");
                }
                partner = currentPartner;
            }
            if (!partner.heartbeat(serverId)) throw new RemoteException("Heartbeat thất bại");
            if (!partnerConnected) {
                int received = 0;
                for (DataRecord record : partner.syncAll(0)) {
                    if (database.applyReplication(record)) received++;
                }
                int sent = partner.mergeRecords(database.getReplicationRecords(), serverId);
                database.recordEvent(new SyncEvent(SyncEvent.Type.FULL_SYNC, "ALL", partnerServerId, serverId,
                        "Nhận " + received + ", gửi " + sent + " thay đổi"));
                partnerConnected = true;
                log("Đã kết nối " + partnerServerId + " | Đồng bộ bù: nhận " + received + ", gửi " + sent);
            }
        } catch (Exception e) {
            disconnectPartner(e);
        }
    }

    public void replicate(SyncEvent.Type type, DataRecord record) {
        replication.execute(() -> {
            if (!partnerConnected) {
                log("Đã lưu " + record.getId() + " tại máy này. Chờ đồng bộ bù.");
                return;
            }
            try {
                switch (type) {
                    case CREATE -> partner.syncCreate(record, serverId);
                    case UPDATE -> partner.syncUpdate(record, serverId);
                    case DELETE -> partner.syncDelete(record, serverId);
                    default -> throw new IllegalArgumentException(type.name());
                }
                log("[RMI GỬI] " + type + " " + record.getId() + " → " + partnerServerId);
                database.recordEvent(new SyncEvent(type, record.getId(), serverId, partnerServerId, "Gửi qua RMI"));
            } catch (Exception e) {
                disconnectPartner(e);
            }
        });
    }

    private void disconnectPartner(Exception error) {
        if (partnerConnected || partner != null) {
            log("Mất kết nối hoặc chưa đồng bộ được với " + partnerServerId + ": " + error.getMessage());
        }
        partnerConnected = false;
        partner = null;
    }

    private String storage() {
        return database.isUsingMysql() ? "MySQL · " + database.getDbName() : "File dự phòng";
    }

    public ServerInfo buildServerInfo() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        return new ServerInfo(serverId, host, port, database.getRecordCount(), uptime, partnerConnected, storage());
    }

    public void log(String message) {
        System.out.printf("[%s] [%-8s] %s%n", LocalTime.now().format(CLOCK), serverId, message);
    }

    public String getServerId() { return serverId; }

    public static void main(String[] args) throws RemoteException {
        String serverId = "SERVER_1";
        String host = "127.0.0.1";
        int port = 1099;
        String partnerHost = "127.0.0.1";
        int partnerPort = 1100;
        String partnerId = "SERVER_2";

        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (i + 1 >= args.length) throw new IllegalArgumentException("Thiếu giá trị cho " + option);
            String value = args[++i];
            switch (option.toLowerCase(Locale.ROOT)) {
                case "-id" -> serverId = value;
                case "-host" -> host = value;
                case "-port" -> port = Integer.parseInt(value);
                case "-partnerhost" -> partnerHost = value;
                case "-partnerport" -> partnerPort = Integer.parseInt(value);
                case "-partnerid" -> partnerId = value;
                default -> throw new IllegalArgumentException("Tham số không hợp lệ: " + option);
            }
        }

        new RmiSyncServer(serverId, host, port, partnerHost, partnerPort, partnerId).start();
    }
}
