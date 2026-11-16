# Lab 5 · Đồng bộ dữ liệu trên hai server bằng Java RMI

## Đối chiếu yêu cầu của giảng viên

| Trong ảnh đề bài | Trong dự án |
|---|---|
| RMI đồng bộ hóa dữ liệu trên 2 server | Hai tiến trình Java đăng ký `ClientService` và `DataSyncService`. Thêm, sửa, xóa có thể thực hiện tại cả hai server. |
| Server chạy trên 2 máy thực | Chạy một server trên mỗi máy vật lý, khai báo IP LAN đối tác. |
| Các Client chạy trên các máy ảo | Chạy cấu hình Client trên mỗi VM và truyền địa chỉ của hai máy vật lý. |

MySQL là lựa chọn lưu trữ của dự án. Dòng Lab 5 trong ảnh không ghi yêu cầu riêng về cơ sở dữ liệu. Monitor đối chiếu danh sách đọc trực tiếp từ cả hai server và hiển thị nơi lưu trữ thực tế mỗi tiến trình sử dụng.

## Chạy thử bằng IntelliJ trên một máy

1. Mở [pom.xml](pom.xml) bằng IntelliJ dưới dạng project Maven. Chọn JDK 17 trở lên trong **File → Project Structure → Project SDK** và tải lại Maven nếu module `lab5-rmi-sync` chưa hiện. Project biên dịch cho Java 17.
2. Mở XAMPP Control Panel và nhấn **Start** ở dòng MySQL. Mặc định dự án kết nối `127.0.0.1:3306`, tài khoản `root` với mật khẩu trống. Tiến trình Java tự tạo database `db_server1` hoặc `db_server2` và các bảng `records`, `sync_events` khi tài khoản có quyền tạo schema và bảng.
3. Chạy lần lượt các cấu hình có sẵn trong menu **Run**. Giữ hai server chạy song song.

   | Cấu hình IntelliJ | Địa chỉ |
   |---|---|
   | `Server 1 (MySQL)` | `127.0.0.1:1099` |
   | `Server 2 (MySQL)` | `127.0.0.1:1100` |
   | `Monitor` | Đọc cả hai server |
   | `Client` | Ban đầu kết nối Server 1 |

4. Kiểm tra console ghi `Lưu trữ MySQL · db_server1` / `db_server2`. Monitor cũng hiển thị thông tin này. Hai cấu hình server dùng `-Ddb.required=true`, nên lỗi kết nối MySQL sẽ dừng khởi động. Với cấu hình DB khác, vào **Run → Edit Configurations**, sửa `-Ddb.host`, `-Ddb.port`, `-Ddb.user` trong **VM options** và đặt `DB_PASSWORD` trong **Environment variables** nếu có mật khẩu.

Có thể chạy `run_cluster.bat` để xem nhanh hai giao diện trên Windows. Script dùng `java` và `javac` trong PATH. Khi chạy server thiếu `-Ddb.required=true`, chương trình có thể dùng file dự phòng tại thư mục `data_server_1` và `data_server_2` nếu không kết nối được MySQL.

## Chạy chấm trên hai máy vật lý và các VM

Ví dụ máy vật lý A có IP `192.168.1.15`, máy B có IP `192.168.1.25`. Hai máy dùng cùng cổng TCP `1099` vì có IP khác nhau.

1. Sao chép cùng phiên bản project sang hai máy vật lý và VM. Bật MySQL cục bộ trên mỗi máy vật lý. Chọn đúng Project SDK và import Maven ở từng máy.
2. Trên máy A, sửa **Program arguments** của `Server 1 (MySQL)` thành dòng sau rồi chạy.

   ```text
   -id SERVER_1 -host 192.168.1.15 -port 1099 -partnerHost 192.168.1.25 -partnerPort 1099 -partnerId SERVER_2
   ```

3. Trên máy B, sửa **Program arguments** của `Server 2 (MySQL)` thành dòng sau rồi chạy.

   ```text
   -id SERVER_2 -host 192.168.1.25 -port 1099 -partnerHost 192.168.1.15 -partnerPort 1099 -partnerId SERVER_1
   ```

4. Kết nối VM sao cho truy cập được cả hai IP vật lý. Có thể dùng Bridged Adapter nếu mạng hỗ trợ. Nếu cần, cho phép TCP 1099 vào trên từng máy chủ cho mạng dùng để demo. Registry và hai dịch vụ RMI dùng chung cổng này. MySQL vẫn kết nối cục bộ trên từng máy. Từ VM Windows, kiểm tra cả hai cổng trước khi chạy.

   ```powershell
   Test-NetConnection 192.168.1.15 -Port 1099
   Test-NetConnection 192.168.1.25 -Port 1099
   ```

5. Trên mỗi VM, sửa **Program arguments** của `Client` theo dòng sau rồi chạy. Dùng cùng tham số cho `Monitor` trên máy có thể kết nối được cả hai server.

   ```text
   192.168.1.15 1099 192.168.1.25 1099
   ```

Thay các IP ví dụ bằng IPv4 của hai máy khi trình diễn. Nếu TCP chưa thông, kiểm tra cấu hình mạng VM, địa chỉ IP mà server dùng ở `-host`, và quy tắc firewall trên hai máy vật lý.

## Kịch bản trình diễn

Dùng hai mã sinh viên mới nếu chạy lại kịch bản để bắt đầu từ phiên bản v1.

| Bước | Thao tác | Kết quả cần quan sát |
|---|---|---|
| 1 | Từ VM 1, chọn Server 1 và thêm `DEMO01` với tên `Nguyễn Minh An`, ngành `CNTT`, GPA `3.60` | Monitor hiện `v1` ở cả hai cột và trạng thái **Khớp**. |
| 2 | Từ VM 2, bấm **Chuyển máy chủ** để kết nối Server 2, chọn `DEMO01` và sửa GPA thành `3.90` | Cả hai server hiện `v2`, Client trên Server 1 đọc được GPA mới. |
| 3 | Dừng Server 1 trong IntelliJ, giữ Server 2 chạy. Quan sát Client trên VM 1 chuyển sang Server 2. Thêm `DEMO02` và xóa `DEMO01`. | Client tiếp tục làm việc. Monitor đánh dấu Server 1 ngoại tuyến và các hồ sơ ở trạng thái **Chưa xác minh**. |
| 4 | Khởi động lại Server 1 với tham số cũ. Chờ hai thẻ báo đã kết nối đối tác. | `DEMO02` khớp trên cả hai server và `DEMO01` biến mất khỏi cả hai danh sách. |

Để minh họa lưu trữ, chạy truy vấn dưới đây trên database cục bộ của mỗi máy. `deleted = 1` đánh dấu bản ghi đã xóa để truyền thao tác xóa khi đối tác kết nối lại. Giao diện chỉ hiển thị các bản ghi còn hiệu lực.

```sql
SELECT id, name, score, version, deleted, updated_by_server
FROM records
WHERE id IN ('DEMO01', 'DEMO02');
```

Khi hai server cùng chỉnh một hồ sơ, phiên bản cao hơn được giữ lại. Nếu bằng phiên bản, hệ thống so thời điểm sửa rồi đến mã server. Đồng bộ bù trao đổi bản ghi đang hoạt động và dấu xóa, vì vậy nên chạy cùng phiên bản mã trên tất cả máy.
