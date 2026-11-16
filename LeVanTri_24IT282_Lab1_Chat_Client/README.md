# BÁO CÁO VÀ HƯỚNG DẪN LAB 1: CHAT APPLICATION (LTM - VKU)

## 1. Yêu Cầu Đề Bài & Mức Độ Hoàn Thiện
- **Giao diện thân thiện:** Thiết kế GUI hiện đại mô phỏng Messenger / Zalo bằng Java Swing (HTML bubble formatting, chia bố cục danh sách online, danh sách phòng, phân biệt tin nhắn gửi/nhận).
- **Multicast & Broadcast:**
  - **Broadcast:** Kênh "Phòng Chung" phát tin nhắn tới tất cả client kết nối đến Server.
  - **Multicast:** Kênh "Nhóm Học Tập" sử dụng `MulticastSocket` tham gia nhóm IP đa hướng `224.2.2.3:8889` để trao đổi nhóm không ảnh hưởng đến toàn mạng.
  - **Private Chat (1-đối-1):** Nhấp vào bất kỳ bạn bè nào đang online để chat riêng qua socket TCP bảo mật.
- **Icon biểu cảm (Emoji):** Có bảng chọn Emoji (😀, 😂, 😍, 👍, ❤️, 🔥, 🎉, 🚀...) bấm để chèn ngay vào tin nhắn.
- **Text - Audio - Video:**
  - Gửi tin nhắn Text.
  - **Tin nhắn thoại:** Bấm **Ghi âm** để thu từ mic, rồi bấm **Gửi bản ghi**. Cả hai bên có thể bấm **Nghe lại** ngay trong chat.
  - **Gọi thoại và gọi video 1–1:** Chọn tài khoản trực tuyến rồi bấm **Gọi thoại** hoặc **Gọi video** ở đầu cuộc trò chuyện. Đối phương có thể trả lời hoặc từ chối. Cả hai chế độ đều truyền âm thanh mic hai chiều qua máy chủ (UDP 8892) và có nút tắt mic, gác máy.
  - **Video:** Cửa sổ cuộc gọi hiển thị video đối phương và hình xem trước của bạn. Máy khách có thể bật webcam hoặc chia sẻ màn hình bằng `java.awt.Robot`.
- **Thông báo chưa đọc:** Số đỏ trên biểu tượng Tin nhắn hiển thị tổng thông báo chưa đọc. Bấm biểu tượng để mở cuộc trò chuyện có thông báo mới nhất. Mỗi cuộc trò chuyện cũng có số riêng và có thể hiển thị **Cuộc gọi nhỡ** nếu cuộc gọi đến kết thúc trước khi bạn trả lời.
- **Đính kèm tệp:** Mỗi tin nhắn tệp có **Tải về** để chọn chỗ lưu và **Mở trong thư mục** để tìm tệp đã lưu.
- **Cơ sở dữ liệu:** Hệ thống lưu trữ `ChatDatabase` quản lý tài khoản người dùng, phòng chat, lưu trữ lịch sử tin nhắn và tệp upload vào thư mục `data/`.

---

## 2. Cách Chạy Demo & Chấm Điểm
### Tài khoản thử nghiệm đã cấu hình sẵn (Mật khẩu chung: `123456`):
1. `alice` - Alice Nguyễn
2. `bob` - Bob Trần
3. `charlie` - Charlie Lê
4. `david` - David Phạm
5. `eve` - Eve Hoàng
6. `frank` - Frank Đỗ

### Các bước chạy:
1. **Khởi động Server:**
   - Chạy file: `run_server.bat`
   - Giao diện quản trị Server sẽ hiện lên, lắng nghe cổng TCP 8888 và UDP Multicast 8889.
2. **Khởi động đồng thời 6 Client:**
   - Chạy file: `run_5_clients.bat`
   - File batch sẽ tự động mở đồng thời 6 cửa sổ Client với 6 tài khoản khác nhau (Alice, Bob, Charlie, David, Eve, Frank).
   - Thử nghiệm gửi tin nhắn chung (Broadcast), gửi tin nhắn nhóm (Multicast), chat riêng 1-1, gửi Emoji, gửi file, gửi Voice Audio, gửi Video Call.

---

## 3. Hướng Dẫn Test Trên 2 Máy Tính Khác Nhau (Mạng LAN / Wi-Fi)

Khi giáo viên yêu cầu demo thực tế trên 2 laptop/máy tính độc lập (ví dụ Máy A chạy Server + Client 1, Máy B chạy Client 2), thực hiện theo các bước sau:

### Điều kiện tiên quyết:
- **Cùng mạng mạng LAN/Wi-Fi:** Cả 2 máy phải kết nối vào cùng một mạng Wi-Fi (hoặc 1 máy phát Mobile Hotspot cho máy kia bắt vào).
- **Cài đặt Java:** Cả 2 máy đều đã cài Java (JDK hoặc JRE phiên bản 11 hoặc 17 trở lên).

---

### Bước 1: Chuẩn bị trên Máy A (Máy chạy Server)
1. **Lấy địa chỉ IP của Máy A:**
   - Mở PowerShell hoặc Command Prompt trên Máy A, gõ lệnh:
     ```powershell
     ipconfig
     ```
   - Tìm dòng **IPv4 Address** của card mạng đang kết nối (ví dụ: `192.168.1.15` hoặc `192.168.137.1`).
   - *Ghi nhớ địa chỉ IP này để cấu hình cho Máy B.*

2. **Cấu hình Tường lửa (Windows Firewall) cho Máy A:**
   - Khi Server mở các cổng TCP 8888 và UDP 8889-8892, Windows Defender Firewall có thể chặn kết nối từ máy khác tới.
   - Khi bật Server, nếu Windows hiện thông báo bảo mật, hãy tích chọn cả **Private networks** và **Public networks** rồi nhấn **Allow access**.
   - *Mẹo mở cổng nhanh bằng PowerShell (Run as Administrator) trên Máy A:*
     ```powershell
     New-NetFirewallRule -DisplayName "VKU Chat TCP" -Direction Inbound -Protocol TCP -LocalPort 8888 -Action Allow
     New-NetFirewallRule -DisplayName "VKU Chat UDP" -Direction Inbound -Protocol UDP -LocalPort 8889,8890,8891,8892 -Action Allow
     ```
   - *(Hoặc tạm thời tắt Windows Firewall trong lúc chấm demo).*

3. **Khởi động Chat Server trên Máy A:**
   - Chạy file:
     ```powershell
     .\run_server.bat
     ```
   - Cửa sổ quản trị Server hiện lên, hiển thị: `Chat Server đã khởi động trên cổng TCP: 8888`.

4. **Khởi động Client trên Máy A (Alice):**
   - Chạy file:
     ```powershell
     .\run_client.bat
     ```
   - Hộp thoại đăng nhập hiện lên:
     - **IP Máy Chủ (Server IP):** Giữ nguyên `localhost` (hoặc nhập `192.168.1.15`)
     - **Tên đăng nhập:** `alice`
     - **Mật khẩu:** `123456`
   - Nhấn **OK** để vào giao diện chat.

---

### Bước 2: Chuẩn bị trên Máy B (Máy Khách)
1. **Copy mã nguồn/chương trình sang Máy B:**
   - Copy toàn bộ thư mục `lab1` (hoặc ít nhất thư mục `bin`, `src` và file `run_client.bat`) sang Máy B qua USB, Google Drive, hoặc Zalo.

2. **Kiểm tra thông mạng giữa 2 máy:**
   - Mở PowerShell trên Máy B, ping thử tới IP của Máy A:
     ```powershell
     ping 192.168.1.15
     ```
   - Nếu có phản hồi `Reply from 192.168.1.15: bytes=32 time=...` tức là 2 máy đã thông mạng.

3. **Khởi động Client trên Máy B (Bob):**
   - **Cách 1 (Qua giao diện GUI):**
     - Chạy file: `.\run_client.bat`
     - Trong hộp thoại đăng nhập:
       - **IP Máy Chủ (Server IP):** Nhập IP của Máy A (ví dụ: `192.168.1.15`)
       - **Tên đăng nhập:** `bob`
       - **Mật khẩu:** `123456`
     - Nhấn **OK**.
   - **Cách 2 (Khởi động nhanh qua dòng lệnh):**
     - Mở PowerShell tại thư mục `lab1` trên Máy B và gõ:
       ```powershell
       .\run_client.bat bob 123456 192.168.1.15
       ```

---

### Bước 3: Kịch Bản Demo & Kiểm Thử Giữa 2 Máy

**Gọi thoại và webcam.** Biên dịch lại rồi khởi động cả Server lẫn Client. Gọi thoại dùng mic và loa mặc định qua Java Sound. Webcam dùng [FFmpeg](https://ffmpeg.org/download.html) trên máy khách. Trên Windows, giải nén bản static và đặt `ffmpeg.exe` cạnh `run_client.bat`, hoặc thêm FFmpeg vào `PATH` rồi mở lại Client. Trên Linux, dùng FFmpeg trong `PATH`. Ứng dụng chọn camera đầu tiên trên Windows và `/dev/video0` trên Linux. Có thể đặt `VKU_CHAT_FFMPEG` thành đường dẫn chương trình FFmpeg và `VKU_CHAT_CAMERA` thành tên camera DirectShow trên Windows hoặc đường dẫn thiết bị trên Linux.

**Tạo nhóm LAN bằng IP.** Đăng nhập bằng hai tài khoản khác nhau vào cùng máy chủ. Nếu chạy Client trên máy chủ, hãy nhập IP LAN của máy chủ vào ô đăng nhập để ứng dụng hiển thị IP LAN cho tài khoản đó. Góc trên bên trái hiển thị tài khoản của bạn, còn danh sách bên dưới hiển thị tên, tên đăng nhập và IP của các tài khoản trực tuyến khác.

1. Bấm **Tạo nhóm** và đặt tên. Có thể chọn thành viên trực tuyến hoặc nhập IP để thêm khi tạo.
2. Chọn nhóm, bấm **Thêm thành viên** rồi nhập IP hoặc chọn tài khoản trong danh sách trực tuyến. Giữ Ctrl để chọn nhiều tài khoản cùng lúc. Khi nhiều người dùng chung IP, hãy chọn người theo tên đăng nhập.
3. Nhóm xuất hiện trong danh sách kênh của các thành viên. Chọn nhóm để xem thành viên và gửi tin nhắn. Máy chủ lưu nhóm và thành viên trong `data/rooms.dat`.

| Tính Năng Test | Thao Tác Thực Hiện Trên 2 Máy | Hiện Tượng Quan Sát Được | Giao Thức |
| :--- | :--- | :--- | :--- |
| **Trạng thái Online** | Máy B vừa đăng nhập xong tài khoản `bob` | Cả 2 máy đều thấy nhau hiện đèn xanh Online trong danh sách người dùng bên trái. | TCP Socket |
| **Chat Riêng 1 - 1** | Máy A (Alice) click vào `Bob Trần` và gửi: *"Chào Bob từ Máy A"* | Máy B (Bob) lập tức nhận được tin nhắn trong cuộc trò chuyện riêng với Alice. Bob trả lời lại Máy A. | TCP Socket |
| **Gửi tệp đính kèm** | Máy A bấm biểu tượng kẹp giấy, chọn tệp gửi cho Bob | Bob bấm **Tải về**, chọn chỗ lưu, rồi bấm **Mở trong thư mục** để tìm tệp. | TCP Socket |
| **Phòng Chung** | Cả 2 máy chọn kênh **"🌐 Phòng Chung (Broadcast)"** | Mọi tin nhắn gửi từ Máy A hay Máy B đều được Server chuyển tiếp tới tất cả các máy trong phòng. | TCP Socket (Broadcast Server) |
| **Nhóm Học Tập** | Cả 2 máy chọn kênh **"📡 Nhóm Học Tập (Multicast UDP)"** | Gửi tin nhắn, gói tin truyền theo địa chỉ IP nhóm Multicast `224.2.2.3:8889`. | UDP Multicast |
| **Tin nhắn thoại** | Bấm biểu tượng mic, nói vài giây rồi bấm **Gửi bản ghi** | Bản ghi xuất hiện trong chat. Bấm **Nghe lại** để phát âm thanh từ mic. | TCP Socket |
| **Cuộc gọi thoại** | Chọn Bob rồi bấm **Gọi thoại** | Bob bấm **Trả lời**. Hai bên nói qua mic và nghe qua loa. | TCP Signaling + UDP 8892 |
| **Cuộc gọi video** | Chọn Bob rồi bấm **Gọi video** | Bob bấm **Trả lời**. Hai bên nghe tiếng và xem luồng webcam khi camera hoạt động. | TCP Signaling + UDP 8892 |
| **Chia sẻ màn hình** | Trong cửa sổ video, bấm **Chia sẻ màn hình** | Các khung hình desktop được gửi cho đối phương. | UDP 8892 |

---

### Xử Lý Sự Cố Thường Gặp (Troubleshooting):
- **Lỗi `Đăng nhập thất bại / Không thể kết nối tới Server`:**
  - Kiểm tra xem Máy A đã chạy `run_server.bat` chưa.
  - Kiểm tra lại địa chỉ IP của Máy A (gõ lại `ipconfig` trên Máy A xem có bị đổi IP không).
  - Tắt Tường lửa (Windows Firewall) trên Máy A hoặc chạy lệnh mở cổng firewall ở Bước 1.
- **Multicast không nhận được tin giữa 2 máy:**
  - Một số Router Wi-Fi trường học/công cộng chặn gói tin UDP Multicast (tính năng Wireless Isolation hoặc IGMP Snooping bị tắt).
  - *Giải pháp:* Dùng một điện thoại phát Wi-Fi Hotspot rồi cả 2 máy tính cùng kết nối vào điểm phát đó, tính năng Multicast UDP sẽ hoạt động 100%.

---

## 4. Hướng Dẫn Chạy Demo Giữa Máy Chính (Windows) & Máy Ảo Linux (VMware / VirtualBox)

Khi giảng viên yêu cầu kiểm thử đa nền tảng (Cross-platform) giữa Máy thật (Windows) và Máy ảo (Ubuntu / Kali / Debian Linux):

### 4.1. Cấu hình Card Mạng cho Máy Ảo (Network Adapter)
Để Máy chính và Máy ảo nhìn thấy nhau như 2 máy độc lập trong mạng LAN:
1. **Chế độ khuyên dùng: `Bridged Adapter` (Cầu nối mạng)**
   - **VMware:** Vào `VM` $\rightarrow$ `Settings` $\rightarrow$ `Network Adapter` $\rightarrow$ Chọn **Bridged: Connected directly to the physical network**.
   - **VirtualBox:** Vào `Settings` $\rightarrow$ `Network` $\rightarrow$ `Adapter 1` $\rightarrow$ Attached to: **Bridged Adapter** (chọn đúng tên card Wi-Fi hoặc Ethernet của máy chính).
   - *(Ưu điểm: Máy ảo nhận 1 địa chỉ IP cùng dải với máy chính từ Router Wi-Fi, hỗ trợ tốt 100% cả TCP lẫn UDP Multicast/Broadcast).*
2. **Nếu không có Wi-Fi (Offline):** Chọn chế độ **`Host-Only Adapter`** trong VMware/VirtualBox.

---

### 4.2. Lấy IP và Kiểm Tra Kết Nối Giữa 2 Máy
1. **Trên Máy chính (Windows):**
   - Mở PowerShell / CMD gõ:
     ```powershell
     ipconfig
     ```
   - Ghi lại **IPv4 Address** (Ví dụ: `192.168.1.15`).
2. **Trên Máy ảo (Linux):**
   - Mở Terminal gõ:
     ```bash
     ip a
     # hoặc:
     hostname -I
     ```
   - Ghi lại IP của Linux (Ví dụ: `192.168.1.35`).
3. **Kiểm tra thông mạng (Ping):**
   - Từ Terminal Linux gõ: `ping 192.168.1.15` (ping sang Windows).
   - Nếu có phản hồi `64 bytes from 192.168.1.15: icmp_seq=1 ttl=... time=...` tức là 2 máy đã thông mạng.

---

### 4.3. Cài Đặt Java và Chạy Trên Máy Ảo Linux
1. **Cài đặt JDK trên Linux (nếu chưa có):**
   ```bash
   sudo apt update
   sudo apt install -y openjdk-17-jdk
   # Kiểm tra:
   java -version
   ```
2. **Copy thư mục `lab1` sang máy ảo:**
   - Kéo thả thư mục `lab1` từ Windows sang Linux (nếu đã bật Guest Additions / VMware Tools), hoặc chia sẻ qua Shared Folder / USB.
3. **Phân quyền và biên dịch trên Linux:**
   - Mở Terminal tại thư mục `lab1` trên Linux và chạy:
     ```bash
     chmod +x *.sh
     ./compile.sh
     ```
4. **Kịch bản chạy Demo:**
   - **Trên Máy chính Windows:** Chạy Chat Server và Client Alice:
     - Chạy `run_server.bat` (Server lắng nghe trên cổng 8888).
     - Chạy `run_client.bat` đăng nhập tài khoản `alice` (mật khẩu `123456`, Server IP `localhost`).
   - **Trên Máy ảo Linux:** Chạy Client Bob kết nối tới IP máy Windows:
     ```bash
     ./run_client.sh bob 123456 192.168.1.15
     ```
   - Giao diện Client sẽ hiện lên trên Linux và tự động kết nối vào Server của máy chính.

---

### 4.4. Kịch Bản Kiểm Thử Cross-Platform (Windows $\leftrightarrow$ Linux)
1. **Trạng thái Online:** Cả 2 máy đều thấy nhau hiện đèn xanh Online trên danh sách.
2. **Chat 1-1 TCP:** Alice (Windows) nhắn tin với Bob (Linux) tức thì không có độ trễ.
3. **Gửi tệp đính kèm:** Alice gửi một PDF hoặc ảnh. Bob bấm **Tải về** trong tin nhắn tệp rồi bấm **Mở trong thư mục**.
4. **Tin nhắn thoại và Multicast UDP:** Gửi bản ghi từ mic qua chat TCP. Tham gia Nhóm Học Tập đa hướng qua địa chỉ IP `224.2.2.3:8889`.
5. **Video Call 2 chiều & Chia sẻ màn hình thời gian thực:**
   - Alice (Windows) bấm **Gọi video** cho Bob.
   - Bob (Linux) nhận popup, bấm **`🟢 Trả lời`**.
   - Nếu thiết bị webcam đã được cấp quyền cho máy ảo và có FFmpeg, hai bên có thể mở webcam. Cả hai cũng có thể dùng chức năng chia sẻ màn hình.
   - Bob bấm **"🖥️ Chia sẻ Màn hình"**: Toàn bộ màn hình Desktop của máy ảo Linux được truyền trực tiếp về máy Windows qua luồng UDP.

---

## 5. Câu Hỏi Giáo Viên Thường Hỏi (Vấn Đáp TCP - UDP)

### Q1: Trong ứng dụng này, phần nào dùng TCP và phần nào dùng UDP? Tại sao?
- **Sử dụng TCP (`ServerSocket`, `Socket`):**
  - Dùng cho: Đăng nhập/xác thực tài khoản, truyền tin nhắn văn bản, gửi nhận file đính kèm, danh sách phòng và quản lý trạng thái online/offline.
  - *Lý do:* TCP hướng kết nối (Connection-oriented), đảm bảo dữ liệu truyền đi không bị mất gói (reliability), đúng thứ tự (in-order delivery) và có cơ chế kiểm soát tắc nghẽn. Gửi file và mật khẩu bắt buộc phải dùng TCP vì nếu mất 1 byte thì file sẽ bị hỏng.
- **Sử dụng UDP (`DatagramSocket`, `MulticastSocket`, `DatagramPacket`):**
  - Dùng cho: Phát sóng đa hướng nhóm Multicast (`224.2.2.3`), gói âm thanh thử nghiệm (8891) và âm thanh, hình ảnh của cuộc gọi trực tiếp (8892).
  - *Lý do:* UDP không cần bắt tay 3 bước (Connectionless), tốc độ truyền cực nhanh, không có overhead quản lý kết nối. Trong truyền voice/video thời gian thực, độ trễ thấp quan trọng hơn việc mất mát một vài gói tin (bị giật nhẹ chấp nhận được hơn là bị delay hàng giây).

### Q2: Multicast và Broadcast khác nhau như thế nào?
- **Broadcast:** Gói tin gửi tới địa chỉ quảng bá (ví dụ `255.255.255.255`) được switch/router gửi tới *tất cả* các máy trong cùng mạng LAN, bất kể máy đó có cần nhận hay không.
- **Multicast:** Gói tin gửi tới một địa chỉ IP nhóm (Class D: `224.0.0.0` đến `239.255.255.255`). Chỉ những máy nào đã đăng ký tham gia nhóm thông qua lệnh `multicastSocket.joinGroup()` thì card mạng mới tiếp nhận gói tin đó, giúp giảm đáng kể tải cho mạng.

