# Thiết lập Firebase cho QLBX ERP

Ứng dụng dùng Firebase Authentication (Email/Password) và Cloud Firestore. Dữ liệu nghiệp vụ vẫn lưu IndexedDB trên thiết bị; đồng bộ hiện là thao tác thủ công, có xác nhận thay thế dữ liệu theo một chiều.

## 1. Bật dịch vụ

1. Mở Firebase Console, chọn dự án `erp-parking`.
2. Authentication → Sign-in method → bật **Email/Password**.
3. Firestore Database → Create database. Chọn vị trí gần Việt Nam phù hợp và chế độ Production.
4. Mở Rules, triển khai nội dung file `firestore.rules` trong repository.

Không cần bật Firebase Storage, Cloud Functions, Hosting, Analytics hay SMS.

## 2. Tạo tài khoản quản trị đầu tiên

1. Authentication → Users → Add user, tạo email và mật khẩu.
2. Sao chép UID của tài khoản.
3. Firestore → tạo collection `users`, document ID bằng UID.
4. Thêm các trường:
   - `role` (string): `super_admin`
   - `active` (boolean): `true`
   - `displayName` (string): tên hiển thị
5. Tạo các tài khoản khác trong Authentication. Quản trị viên có thể thêm hồ sơ role tương ứng trong collection `users`.

Các role: `super_admin`, `admin`, `operator`, `viewer`. Không cho phép người dùng tự đăng ký hoặc tự chọn quyền trong ứng dụng.

## 3. Cách sao lưu và khôi phục trong ứng dụng

- Đăng nhập bằng tài khoản đã được kích hoạt.
- **Sao lưu lên Firebase**: thay thế toàn bộ bộ dữ liệu cloud của bãi `main` bằng dữ liệu trên thiết bị hiện tại. Chỉ super_admin/admin/operator được ghi.
- **Khôi phục từ Firebase**: thay thế toàn bộ dữ liệu trên thiết bị bằng bộ dữ liệu cloud. Tài khoản viewer cũng có thể khôi phục về thiết bị để xem.
- Trước mỗi lần thay thế, nên tải bản JSON cục bộ từ Hệ thống → Sao lưu & khôi phục.

## 4. Cấu trúc dữ liệu cloud

- `users/{uid}`: vai trò và trạng thái tài khoản.
- `yards/main/vehicles/{id}`
- `yards/main/logs/{id}`
- `yards/main/slots/{id}`
- `yards/main/events/{id}`
- `yards/main/meta/{key}`

Đây là đồng bộ thủ công có xác nhận thay thế, chưa phải đồng bộ hai chiều tự động theo thời gian thực. Không thao tác ghi từ nhiều thiết bị cùng lúc. Sau khi nghiệm thu cơ chế này mới nâng cấp sang đồng bộ nền, xử lý xung đột và nhật ký xóa.

## 5. Bảo mật và chi phí

Firebase Web API key là cấu hình client công khai, không phải service-account key. Bảo mật dữ liệu dựa vào Authentication và Firestore Security Rules. Không đưa service-account JSON vào ứng dụng hoặc repository.

Sử dụng Spark plan; theo dõi hạn mức Firestore trong Firebase Console. Nếu hết hạn mức, không nâng cấp billing tự động. Dữ liệu vẫn được giữ cục bộ trong IndexedDB.
